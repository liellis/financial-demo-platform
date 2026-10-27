"""Read-only, release-pinned context. Candidates are never implicitly published."""
import json
import re
from .contracts import DomainError, identity
from . import workspace


class ContextService:
    def __init__(self, studio):self.studio=studio

    def release(self, release_id=None):
        if not release_id:
            pointer=self.studio.store.get('pointer','current')
            if not pointer:raise DomainError('NO_PUBLISHED_KNOWLEDGE',409)
            release_id=pointer['release_id']
        release=self.studio.get('release',release_id)
        if any(r.get('status')!='PUBLISHED' for r in release['rules']):raise DomainError('RELEASE_NOT_PUBLISHED',409)
        return release

    def search(self, query, release_id=None, limit=10):
        release=self.release(release_id)
        self.studio.graph.prepare(release)
        concepts=release.get('ontology',[])
        needle=query.casefold().strip()
        matched=[c for c in concepts if any(term.casefold() in needle or needle in term.casefold() for term in [c['name'],*c.get('aliases',[])] if term)]
        terms={needle}|{t.casefold() for c in matched for t in [c['name'],*c.get('aliases',[])]}
        scores={r['id']:0.0 for r in release['rules']}
        for c in matched:
            for key in c['rule_ids']:
                if key in scores:scores[key]+=5
        for rule in release['rules']:
            text=' '.join(str(rule.get(k,'')) for k in ('id','name','condition','outcome','scope')).casefold()
            scores[rule['id']]+=sum(2 if term in rule['name'].casefold() else 1 for term in terms if term and term in text)
        if self.studio.settings.neo4j_uri:
            tokens=re.findall(r'[^\W_]+',query,re.UNICODE)[:20]
            lucene='graph_id:"'+release['id']+'" AND ('+' OR '.join('search_text:"'+t+'"' for t in tokens)+')'
            if tokens:
                try:
                    with self.studio.graph.driver() as driver:
                        driver.execute_query('CALL db.awaitIndex("bos_context_terms",10)')
                        rows,_,_=driver.execute_query('CALL db.index.fulltext.queryNodes("bos_context_terms",$query) YIELD node,score WHERE node.graph_id=$id AND node.kind="BusinessRule" RETURN node.id AS id,score ORDER BY score DESC LIMIT $limit',query=lucene,id=release['id'],limit=min(100,limit*3))
                        for rank,row in enumerate(rows):
                            if row['id'] in scores:scores[row['id']]+=1/(rank+1)
                except Exception:raise DomainError('GRAPH_QUERY_FAILED',503)
        ids=sorted((key for key,value in scores.items() if value>0),key=lambda key:(-scores[key],key))[:limit]
        rules={r['id']:r for r in release['rules']}
        return {'release_id':release['id'],'items':[self.rule(release,rules[key]) for key in ids], 'concepts':matched[:limit], 'retrieval':'scoped-fulltext-alias-graph' if self.studio.settings.neo4j_uri else 'alias-keyword-local','limitations':['No embedding/reranker used; relevance must be assessed on a fixed evaluation set.']}

    def rule(self, release, rule):
        return {k:rule[k] for k in ('id','name','condition','outcome','scope','exceptions','uncertainties','entity_version_id')} | {'evidence':[{'id':identity('evidence',[release['id'],rule['id'],i]), **ev,'snapshot_id':rule['snapshot_id']} for i,ev in enumerate(rule['evidence'])]}

    def trace(self, rule_id, release_id=None):
        release=self.release(release_id)
        rule=next((r for r in release['rules'] if r['id']==rule_id),None)
        if not rule:
            concept=next((c for c in release.get('ontology',[]) if c['id']==rule_id),None)
            if not concept:raise DomainError('NOT_FOUND',404)
            graph=self.studio.graph.read(release,100,rule_id)
            return {'release_id':release['id'],'concept':concept,**graph}
        # Ensure all graph reads are release-scoped and exercise the actual graph store.
        graph=self.studio.graph.read(release,100,rule_id)
        return {'release_id':release['id'],'rule':self.rule(release,rule),'nodes':graph['nodes'],'edges':graph['edges'],'truncated':graph['truncated'],'backend':graph['backend']}

    def evidence(self,evidence_id,release_id=None):
        release=self.release(release_id)
        for rule in release['rules']:
            for i,ev in enumerate(rule['evidence']):
                if identity('evidence',[release['id'],rule['id'],i])!=evidence_id:continue
                snapshot=next(s for s in release['source_snapshots'] if s['id']==rule['snapshot_id'])
                text=self.studio.sources.read(snapshot,ev['path'])
                excerpt='\n'.join(text.splitlines()[ev['start_line']-1:ev['end_line']])
                if ev['quote'].strip() not in excerpt:raise DomainError('EVIDENCE_STALE',409)
                return {'release_id':release['id'],'snapshot_id':snapshot['id'],'content_hash':snapshot['content_hash'],**ev,'content':excerpt}
        raise DomainError('NOT_FOUND',404)

    def assemble(self,query,release_id=None,limit=10,token_budget=4000,target_baselines=None):
        import tiktoken
        release=self.release(release_id)
        targets=target_baselines or []
        if len({t['repository_id'] for t in targets})!=len(targets):raise DomainError('DUPLICATE_BASELINES',422)
        comparisons=[]
        for source in release['source_snapshots']:
            target=next((t for t in targets if t['repository_id']==source['repository_id']),None)
            status='UNKNOWN'
            basis='NOT_OBSERVED'
            observation=workspace.verify(self.studio.settings,target,release['id']) if target else None
            if observation:
                status='MATCH' if observation['content_hash']==source['content_hash'] and observation['scope_manifest_hash']==source['scope_manifest_hash'] else 'MISMATCH'
                basis='SIGNED_WORKSPACE_OBSERVATION'
            elif target and target.get('source_snapshot_id'):
                basis='FIXED_SNAPSHOT'
                actual=self.studio.store.get('snapshot',target['source_snapshot_id'])
                # Only snapshots already authorized by this published release can be compared by an Agent.
                if actual and actual['id'] in {s['id'] for s in release['source_snapshots']} and actual['repository_id']==source['repository_id']:
                    status='MATCH' if actual['content_hash']==source['content_hash'] and actual['scope_manifest_hash']==source['scope_manifest_hash'] else 'MISMATCH'
            elif target and target.get('content_hash') and target.get('scope_manifest_hash'):
                basis='CLIENT_CLAIM'
                # A client claim is diagnostic, not proof of the current worktree.
                status='MISMATCH' if target['content_hash']!=source['content_hash'] or target['scope_manifest_hash']!=source['scope_manifest_hash'] else 'UNKNOWN'
            comparisons.append({'repository_id':source['repository_id'],'source_snapshot_id':source['id'],'status':status,'basis':basis,**({'observed_at':observation['observed_at'],'expires_at':observation['expires_at'],'observation_id':observation['observation_id']} if observation else {})})
        unknown_repos={t['repository_id'] for t in targets}-{s['repository_id'] for s in release['source_snapshots']}
        if unknown_repos:raise DomainError('BASELINE_SOURCE_NOT_AUTHORIZED',403)
        overall='MISMATCH' if any(c['status']=='MISMATCH' for c in comparisons) else 'UNKNOWN' if any(c['status']=='UNKNOWN' for c in comparisons) else 'MATCH'
        summarized_targets=[{k:v for k,v in t.items() if k!='attestation'}|({'observation_id':t['attestation']['payload']['observation_id']} if t.get('attestation') else {}) for t in targets]
        package={'release_id':release['id'],'query':query,'source_manifest':[{k:s.get(k) for k in ('repository_id','id','commit','content_hash','scope_manifest_hash')} for s in release['source_snapshots']], 'projection_ids':release['projection_ids'],'target_baselines':summarized_targets,'baseline_comparisons':comparisons,'baseline_status':overall,'knowledge_items':[],'rules':[],'concepts':[],'related_code':[],'uncertainties':['Read-only context; no code changes authorized.','Workspace attestations are point-in-time observations; recheck before any write.'],'authorization_scope':{'project_id':'local','knowledge':'published-only'},'truncated':False,'token_usage':0,'tokenizer':'cl100k_base','token_budget':token_budget}
        encoding=tiktoken.get_encoding('cl100k_base')
        def count(value):return len(encoding.encode(json.dumps(value,ensure_ascii=False,sort_keys=True),disallowed_special=()))
        search=self.search(query,release['id'],limit)
        results=search['items']
        for concept in search.get('concepts',[]):
            trial={**package,'concepts':package['concepts']+[concept],'knowledge_items':package['knowledge_items']+[{'kind':concept['kind'],'id':concept['id']}]}
            if count(trial)+16>token_budget:package['truncated']=True;break
            package=trial
        for rule in results:
            code=next((n for n in release['nodes'] if n['id']==rule['entity_version_id']),None)
            trial={**package,'rules':package['rules']+[rule],'knowledge_items':package['knowledge_items']+[{'kind':'BusinessRule','id':rule['id']}],'related_code':package['related_code']+([code] if code and all(n['id']!=code['id'] for n in package['related_code']) else [])}
            # Reserve room for the final count and truncation flag.
            if count(trial)+16>token_budget:package['truncated']=True;break
            package=trial
        for _ in range(4):
            measured=count(package)
            if measured==package['token_usage']:break
            package['token_usage']=measured
        if package['token_usage']>token_budget:raise DomainError('CONTEXT_BUDGET_TOO_SMALL',422)
        return package
