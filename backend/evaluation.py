"""Frozen, retrieval-only EP-1 technical scorer; no LLM or semantic adjudication.

Gold IDs refer to exact reviewed release units. This tests delivery of frozen
knowledge, not whether the original bank-domain annotation was correct.
"""
import copy
import re
from collections import Counter
from pathlib import Path
from .contracts import DomainError,digest,identity
from .context import ContextService

PROTOCOL={'id':'EP-1-context-exact-v1','k':10,'token_budget':4000,'tokenizer':'cl100k_base','matcher':'exact-release-fields-and-evidence-v1','repeats':1}


def metrics(tp,fp,fn):
    precision=tp/(tp+fp) if tp+fp else None
    recall=tp/(tp+fn) if tp+fn else None
    f1=2*tp/(2*tp+fp+fn) if 2*tp+fp+fn else None
    return {'tp':tp,'fp':fp,'fn':fn,'precision':precision,'recall':recall,'f1':f1}


def create_dataset(studio,data,actor):
    release=ContextService(studio).release(data['release_id'])
    authorized={r['id'] for r in release['rules']}|{c['id'] for c in release.get('ontology',[])}
    seen=set()
    normalized_queries=set()
    normalize_query=lambda q:re.sub(r'\s+',' ',q.strip()).casefold()
    for case in data['cases']:
        if case['id'] in seen or len(set(case['expected_ids']))!=len(case['expected_ids']):raise DomainError('EVALUATION_DUPLICATE_UNIT',422)
        seen.add(case['id'])
        normalized=normalize_query(case['query'])
        if not normalized or normalized in normalized_queries:raise DomainError('EVALUATION_DUPLICATE_QUERY',422)
        normalized_queries.add(normalized)
        if set(case['expected_ids'])-authorized:raise DomainError('EVALUATION_GOLD_NOT_IN_RELEASE',422)
    content={k:copy.deepcopy(data[k]) for k in ('name','split','release_id','annotation_notes','cases')}
    content['release_digest']=release['digest']
    content['dataset_digest']=digest(content)
    content['id']=identity('evaluation_dataset',content['dataset_digest'])
    with studio.store.transaction() as conn:
        for other in studio.store.all('evaluation_dataset',conn):
            if other['split']!=data['split'] and normalized_queries&{normalize_query(c['query']) for c in other['cases']}:
                raise DomainError('EVALUATION_SPLIT_OVERLAP',409)
        previous=studio.store.get('evaluation_dataset',content['id'],conn)
        if previous:return previous
        saved=studio.store.insert('evaluation_dataset',content,conn)
        studio.audit(actor,'EVALUATION_DATASET_CREATE',saved['id'],conn,{'split':saved['split']})
        return saved


def score(studio,release,case,package):
    gold=set(case['expected_ids']);matched=set();judgments=[]
    lookup={r['id']:r for r in package.get('rules',[])}|{c['id']:c for c in package.get('concepts',[])}
    truth={r['id']:r for r in release['rules']}|{c['id']:c for c in release.get('ontology',[])}
    context=ContextService(studio)
    for position,unit in enumerate(package.get('knowledge_items',[])[:PROTOCOL['k']],1):
        key=unit.get('id');prediction=lookup.get(key);expected=truth.get(key)
        complete=bool(prediction and expected)
        reason='NOT_GOLD_OR_DUPLICATE'
        if complete and unit.get('kind')=='BusinessRule' and key in {r['id'] for r in release['rules']}:
            complete=all(prediction.get(k)==expected.get(k) for k in ('name','condition','outcome','scope','exceptions','uncertainties','entity_version_id'))
            expected_evidence=context.rule(release,expected)['evidence']
            complete=complete and prediction.get('evidence')==expected_evidence
            if complete:
                try:
                    for evidence in prediction['evidence']:context.evidence(evidence['id'],release['id'])
                except DomainError:complete=False
        elif complete:
            complete=unit.get('kind') in ('BusinessConcept','BusinessModule') and unit.get('kind')==expected.get('kind') and all(prediction.get(k)==expected.get(k) for k in ('definition','name','aliases','rule_ids','code_entity_ids'))
        if not complete:reason='INCOMPLETE_OR_INVALID_REFERENCE'
        correct=complete and key in gold and key not in matched
        if correct:matched.add(key);reason='EXACT_COMPLETE_MATCH'
        judgments.append({'position':position,'id':key,'correct':correct,'reason':reason})
    tp=len(matched);fp=len(judgments)-tp;fn=len(gold-matched)
    return {**metrics(tp,fp,fn),'judgments':judgments,'missing_ids':sorted(gold-matched)}


def run(studio,dataset_id,actor):
    dataset=studio.get('evaluation_dataset',dataset_id)
    context=ContextService(studio);release=context.release(dataset['release_id'])
    if release['digest']!=dataset['release_digest']:raise DomainError('EVALUATION_RELEASE_CHANGED',409)
    import tiktoken
    from importlib.metadata import version
    scorer_digest=digest(Path(__file__).read_text())
    retrieval_digest=digest(Path(__file__).with_name('context.py').read_text())
    manifest={'protocol':PROTOCOL,'dataset_id':dataset_id,'dataset_digest':dataset['dataset_digest'],'split':dataset['split'],'release_id':release['id'],'release_digest':release['digest'],'projection_ids':release['projection_ids'],'source_snapshots':[{k:s[k] for k in ('id','content_hash','scope_manifest_hash')} for s in release['source_snapshots']],'scorer_digest':scorer_digest,'retrieval_digest':retrieval_digest,'tiktoken_version':version('tiktoken'),'neo4j':bool(studio.settings.neo4j_uri),'model_calls':0}
    rows=[]
    for case in dataset['cases']:
        try:
            package=context.assemble(case['query'],release['id'],10,4000)
            measurement=len(tiktoken.get_encoding('cl100k_base').encode(__import__('json').dumps(package,ensure_ascii=False,sort_keys=True),disallowed_special=()))
            if measurement>4000 or package['token_usage']!=measurement:raise DomainError('EVALUATION_TOKEN_CONTRACT_FAILED',422)
            result=score(studio,release,case,package)
            rows.append({'case_id':case['id'],'domain':case['domain'],'complexity':case['complexity'],'answerable':bool(case['expected_ids']),'status':'OK','package':package,'output_digest':digest(package),**result})
        except DomainError as exc:
            rows.append({'case_id':case['id'],'domain':case['domain'],'complexity':case['complexity'],'answerable':bool(case['expected_ids']),'status':'ERROR','error':exc.code,**metrics(0,0,len(case['expected_ids'])),'judgments':[],'missing_ids':case['expected_ids']})
    answered=[r for r in rows if r['answerable']]
    no_answer=[r for r in rows if not r['answerable']]
    totals=metrics(*(sum(r[k] for r in answered) for k in ('tp','fp','fn')))
    def macro(items):
        return {k:sum(values)/len(values) if (values:=[r[k] for r in items if r[k] is not None]) else None for k in ('precision','recall','f1')}
    groups={}
    for field in ('domain','complexity'):
        groups[field]={value:{'samples':len(group:=[r for r in answered if r[field]==value]),'micro':metrics(*(sum(r[k] for r in group) for k in ('tp','fp','fn'))),'macro':macro(group)} for value in sorted({r[field] for r in answered})}
    summary={'answerable_samples':len(answered),'micro':totals,'macro':macro(answered),'groups':groups,'errors':sum(r['status']=='ERROR' for r in rows),'error_groups':dict(Counter(r['error'] for r in rows if r['status']=='ERROR')),'no_answer':{'samples':len(no_answer),'correct_abstentions':sum(r['status']=='OK' and not r['judgments'] for r in no_answer),'incorrect_answers':sum(bool(r['judgments']) for r in no_answer),'errors':sum(r['status']=='ERROR' for r in no_answer)},'acceptance':'TECHNICAL_SINGLE_RUN_ONLY','limitations':['Exact frozen-unit delivery, not semantic annotation correctness.','Single run; no statistical or bank-domain acceptance claim.','Exact normalized queries cannot cross splits; experts must additionally check semantic leakage.']}
    content={'manifest':manifest,'summary':summary,'cases':rows}
    content['result_digest']=digest(content);content['id']=identity('evaluation_run',content['result_digest'])
    with studio.store.transaction() as conn:
        previous=studio.store.get('evaluation_run',content['id'],conn)
        if previous:return previous
        saved=studio.store.insert('evaluation_run',content,conn)
        studio.audit(actor,'EVALUATION_RUN',saved['id'],conn,{'dataset_id':dataset_id,'model_calls':0})
        return saved
