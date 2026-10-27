"""Versioned, rebuildable Neo4j projections. Relational records remain authoritative."""
import json
from .contracts import DomainError, digest


class GraphProjection:
    def __init__(self, settings):
        self.settings = settings

    def driver(self):
        from neo4j import GraphDatabase
        return GraphDatabase.driver(self.settings.neo4j_uri, auth=(self.settings.neo4j_user, self.settings.neo4j_password), connection_timeout=5, max_transaction_retry_time=5)

    def health(self):
        if not self.settings.neo4j_uri:
            return {'backend': 'local-manifest', 'ready': True}
        try:
            with self.driver() as driver:
                driver.verify_connectivity()
            return {'backend': 'neo4j', 'ready': True}
        except Exception:
            raise DomainError('GRAPH_UNAVAILABLE', 503)

    def prepare(self, manifest):
        if not self.settings.neo4j_uri:
            return {'backend': 'local-manifest', 'ready': True, 'digest': manifest['digest']}
        ids = {n['id'] for n in manifest['nodes']}
        if len(ids) != len(manifest['nodes']) or any(e['source'] not in ids or e['target'] not in ids for e in manifest['edges']):
            raise DomainError('GRAPH_ENDPOINT_INVALID', 422)
        unique_edges = {}
        for edge in manifest['edges']:
            previous = unique_edges.get(edge['id'])
            if previous and digest(previous) != digest(edge):
                raise DomainError('GRAPH_EDGE_ID_CONFLICT',422)
            unique_edges[edge['id']] = edge
        try:
            with self.driver() as driver:
                driver.execute_query('CREATE CONSTRAINT bos_graph_id IF NOT EXISTS FOR (g:BOSGraph) REQUIRE g.id IS UNIQUE')
                driver.execute_query('CREATE CONSTRAINT bos_node_version IF NOT EXISTS FOR (n:BOSNode) REQUIRE (n.graph_id,n.id) IS UNIQUE')
                driver.execute_query('CREATE FULLTEXT INDEX bos_terms IF NOT EXISTS FOR (n:BOSNode) ON EACH [n.name,n.search_text]')
                driver.execute_query('CREATE FULLTEXT INDEX bos_context_terms IF NOT EXISTS FOR (n:BOSNode) ON EACH [n.graph_id,n.name,n.search_text]')
                def write(tx):
                    row = tx.run('MATCH (g:BOSGraph {id:$id}) RETURN g.digest AS digest,g.ready AS ready,g.storage_version AS storage_version', id=manifest['id']).single()
                    if row and row['digest'] != manifest['digest']:
                        raise DomainError('GRAPH_MANIFEST_CONFLICT', 409)
                    if row and row['ready'] and row['storage_version']==2:
                        return
                    tx.run('MERGE (g:BOSGraph {id:$id}) SET g.digest=$digest,g.ready=false', id=manifest['id'], digest=manifest['digest']).consume()
                    rule_details={r['id']:r for r in manifest.get('rules',[])}
                    rich_nodes=[{**rule_details.get(n['id'],{}),**n} for n in manifest['nodes']]
                    nodes = [{'id':n['id'],'kind':n['kind'],'name':n.get('name',''),'search_text':' '.join(str(n.get(k,'')) for k in ('name','definition','aliases','condition','outcome','scope','signature')), 'payload':json.dumps(n,ensure_ascii=False)} for n in rich_nodes]
                    tx.run('UNWIND $nodes AS n MERGE (v:BOSNode {graph_id:$id,id:n.id}) SET v.kind=n.kind,v.name=n.name,v.search_text=n.search_text,v.payload=n.payload', id=manifest['id'], nodes=nodes).consume()
                    edges = [{**e,'payload':json.dumps(e,ensure_ascii=False)} for e in unique_edges.values()]
                    tx.run('UNWIND $edges AS e MATCH (a:BOSNode {graph_id:$id,id:e.source}), (b:BOSNode {graph_id:$id,id:e.target}) MERGE (a)-[r:BOS_EDGE {id:e.id}]->(b) SET r.type=e.type,r.payload=e.payload',id=manifest['id'],edges=edges).consume()
                    n=tx.run('MATCH (n:BOSNode {graph_id:$id}) RETURN count(n) AS n',id=manifest['id']).single()['n']
                    e=tx.run('MATCH (:BOSNode {graph_id:$id})-[r:BOS_EDGE]->(:BOSNode {graph_id:$id}) RETURN count(r) AS n',id=manifest['id']).single()['n']
                    if n != len(nodes) or e != len(edges):
                        raise DomainError('GRAPH_NOT_READY',503)
                    tx.run('MATCH (g:BOSGraph {id:$id}) SET g.ready=true,g.storage_version=2',id=manifest['id']).consume()
                with driver.session() as session:
                    session.execute_write(write)
            return {'backend':'neo4j','ready':True,'digest':manifest['digest']}
        except DomainError:
            raise
        except Exception:
            raise DomainError('GRAPH_PREPARATION_FAILED',503)

    def read(self, manifest, limit=150, root=None):
        limit=max(1,min(limit,300))
        self.prepare(manifest)
        if not self.settings.neo4j_uri:
            nodes=manifest['nodes']; edges=manifest['edges']
            if root:
                related={root}|{e['target'] if e['source']==root else e['source'] for e in edges if root in (e['source'],e['target'])}
                nodes=sorted((n for n in nodes if n['id'] in related),key=lambda n:n['id']!=root)
            selected={}
            for n in nodes:
                if n['kind'] in ('BusinessModule','BusinessConcept') and len(selected)<max(1,limit//6):
                    selected[n['id']]=n
            # Keep both ends of mappings together in bounded previews.
            lookup={n['id']:n for n in nodes}
            for e in edges if not root else []:
                if e['type']=='IMPLEMENTED_BY' and e['source'] in lookup and e['target'] in lookup:
                    required={e['source'],e['target']}-selected.keys()
                    if len(selected)+len(required)<=limit:
                        for key in (e['source'],e['target']):selected[key]=lookup[key]
            for n in nodes:
                if len(selected)>=limit:break
                selected.setdefault(n['id'],n)
            return {'nodes':list(selected.values()),'edges':[e for e in edges if e['source'] in selected and e['target'] in selected], 'backend':'local-manifest','truncated':len(selected)<len(nodes)}
        try:
            with self.driver() as driver:
                with driver.session() as session:
                    def read(tx):
                        graph=tx.run('MATCH (g:BOSGraph {id:$id}) RETURN g.ready AS ready,g.digest AS digest',id=manifest['id']).single()
                        if not graph or not graph['ready'] or graph['digest']!=manifest['digest']:
                            raise DomainError('GRAPH_NOT_READY',503)
                        if root:
                            rows=tx.run('MATCH (r:BOSNode {graph_id:$id,id:$root}) OPTIONAL MATCH (r)-[:BOS_EDGE]-(n:BOSNode {graph_id:$id}) WITH r,collect(DISTINCT n) AS neighbors UNWIND [r]+neighbors AS item RETURN item.payload AS payload LIMIT $limit',id=manifest['id'],root=root,limit=limit)
                            nodes=[json.loads(r['payload']) for r in rows]
                        else:
                            chosen={}
                            groups=tx.run('MATCH (m:BOSNode {graph_id:$id}) WHERE m.kind IN ["BusinessModule","BusinessConcept"] OPTIONAL MATCH (m)-[:BOS_EDGE]->(n:BOSNode {graph_id:$id}) RETURN m.id AS id,m.payload AS payload,collect(n.payload)[0..2] AS members ORDER BY id LIMIT $limit',id=manifest['id'],limit=max(1,limit//6))
                            for group in groups:
                                for payload in [group['payload'],*group['members']]:
                                    if payload and len(chosen)<limit:
                                        value=json.loads(payload);chosen[value['id']]=value
                            rows=tx.run('MATCH (r:BOSNode {graph_id:$id,kind:"BusinessRule"}) OPTIONAL MATCH (r)-[e:BOS_EDGE {type:"IMPLEMENTED_BY"}]->(c:BOSNode {graph_id:$id}) RETURN r.payload AS rule,c.payload AS code ORDER BY r.id LIMIT $limit',id=manifest['id'],limit=max(0,(limit-len(chosen))//2))
                            for row in rows:
                                for field in ('rule','code'):
                                    if row[field]:
                                        n=json.loads(row[field]);chosen[n['id']]=n
                            rows=tx.run('MATCH (n:BOSNode {graph_id:$id}) WHERE NOT n.id IN $ids RETURN n.payload AS payload ORDER BY n.id LIMIT $limit',id=manifest['id'],ids=list(chosen),limit=limit-len(chosen))
                            for row in rows:
                                n=json.loads(row['payload']);chosen[n['id']]=n
                            nodes=list(chosen.values())
                        ids=[n['id'] for n in nodes]
                        edges=[json.loads(r['payload']) for r in tx.run('MATCH (a:BOSNode {graph_id:$id})-[r:BOS_EDGE]->(b:BOSNode {graph_id:$id}) WHERE a.id IN $ids AND b.id IN $ids RETURN r.payload AS payload',id=manifest['id'],ids=ids)]
                        return {'nodes':nodes,'edges':edges,'backend':'neo4j','truncated':len(nodes)<len(manifest['nodes'])}
                    return session.execute_read(read)
        except DomainError:raise
        except Exception:raise DomainError('GRAPH_QUERY_FAILED',503)
