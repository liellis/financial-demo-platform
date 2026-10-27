"""Opt-in live Neo4j acceptance; isolated temporary SQLite, no production approvals.

Run inside the API image with its Neo4j environment. Only uniquely owned test
version graphs are removed at exit. Does not call any paid model endpoint.
"""
import io
import json
import tempfile
import shutil
import uuid
import zipfile
from pathlib import Path
from fastapi.testclient import TestClient
from backend.api import create_app
from backend.config import Settings
from backend import ontology
from backend import workspace


def verify():
    graph_ids = set()
    with tempfile.TemporaryDirectory(prefix='bos-acceptance-') as folder:
        settings = Settings(data_dir=Path(folder), database_url='sqlite:///'+folder+'/test.db', codegraph_url='', tokens={r: r+'-test-only' for r in ('admin','developer','reviewer','agent')})
        if not settings.neo4j_uri:
            raise RuntimeError('Real Neo4j configuration required; local fallback is not acceptance.')
        app = create_app(settings)
        studio = app.state.studio
        def h(role='admin'): return {'Authorization':'Bearer '+settings.tokens[role]}
        def ok(response):
            assert response.status_code == 200, response.text
            return response.json()
        try:
            with TestClient(app) as client:
                payload = io.BytesIO()
                with zipfile.ZipFile(payload,'w') as archive:
                    archive.writestr('src/Limit.java','class Limit {\n boolean allowed(int amount) {\n  return amount <= 100;\n }\n}\n')
                snapshot = ok(client.post('/api/v2/sources/upload',headers=h(),files={'file':('test.zip',payload.getvalue(),'application/zip')},data={'name':'TEST ONLY isolated acceptance'}))
                projection = studio.sources.analyze(snapshot,engine='javac')
                entity = next(e for e in projection['entities'] if e['kind']=='Method' and e['name']=='allowed')
                key='test-only-'+uuid.uuid4().hex
                rule={'id':key,'name':'TEST ONLY 额度上限','condition':'amount <= 100','outcome':'允许','scope':'isolated test','exceptions':[],'uncertainties':[],'entity_version_id':entity['id'],'evidence':[{'path':'src/Limit.java','start_line':3,'end_line':3,'quote':'return amount <= 100;'}],'status':'CANDIDATE','snapshot_id':snapshot['id'],'projection_id':projection['id'],'content_revision':1,'origin_kind':'TEST_FIXTURE'}
                with studio.store.transaction() as conn: rule=studio.store.insert('candidate',rule,conn)
                concept=ontology.save(studio,{'kind':'BusinessConcept','name':'TEST ONLY 额度','definition':'隔离验证额度规则','aliases':['授信天花板'],'projection_id':projection['id'],'rule_ids':[key],'code_entity_ids':[entity['id']],'reason':'TEST ONLY'},'admin')
                ontology.decide(studio,concept['id'],{'decision':'APPROVE','expected_revision':concept['revision'],'reason':'TEST ONLY'},'reviewer')
                assert studio.get('candidate',key)['status']=='CANDIDATE'
                rule=ok(client.post('/api/v2/reviews/'+key+'/decision',headers=h('reviewer'),json={'decision':'APPROVE','expected_revision':rule['revision'],'reason':'TEST ONLY'}))
                release=ok(client.post('/api/v2/releases',headers=h(),json={'candidate_ids':[key],'expected_revision':0,'reason':'TEST ONLY isolated acceptance'}))
                graph_ids.add(release['id'])
                graph=ok(client.get('/api/v2/releases/'+release['id']+'/graph',headers=h('agent')))
                assert graph['backend']=='neo4j' and any(n['kind']=='BusinessConcept' for n in graph['nodes'])
                found=ok(client.get('/api/v2/context/search',headers=h('agent'),params={'q':'授信天花板'}))
                assert found['items'][0]['id']==key and found['concepts'] and found['retrieval']=='scoped-fulltext-alias-graph'
                trace=ok(client.get('/api/v2/context/trace/'+found['concepts'][0]['id'],headers=h('agent')))
                assert any(e['type']=='CONTAINS_RULE' for e in trace['edges'])
                evidence=ok(client.get('/api/v2/context/evidence/'+found['items'][0]['evidence'][0]['id'],headers=h('agent')))
                assert evidence['content'].strip()=='return amount <= 100;'
                package=ok(client.post('/api/v2/context/assemble',headers=h('agent'),json={'query':'授信天花板','token_budget':4000,'target_baselines':[{'repository_id':snapshot['repository_id'],'source_snapshot_id':snapshot['id']}]}))
                assert package['rules'] and package['concepts'] and package['token_usage']<=4000 and package['baseline_status']=='MATCH'
                from cryptography.hazmat.primitives import serialization
                from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
                keypair=Ed25519PrivateKey.generate();private=Path(folder)/'host-private.pem'
                private.write_bytes(keypair.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
                (Path(folder)/'workspace-public-key.pem').write_bytes(keypair.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
                local=Path(folder)/'test-workspace';shutil.copytree(Path(folder)/'snapshots'/snapshot['id'],local)
                config=Path(folder)/'host-config.json';config.write_text(json.dumps({'private_key_file':str(private),'repositories':{snapshot['repository_id']:{'path':str(local)}}}))
                observation=workspace.collect(snapshot['repository_id'],release['id'],config)
                observed=ok(client.post('/api/v2/context/assemble',headers=h('agent'),json={'query':'授信天花板','target_baselines':[observation]}))
                assert observed['baseline_status']=='MATCH' and observed['baseline_comparisons'][0]['basis']=='SIGNED_WORKSPACE_OBSERVATION'
                (local/'NewUntracked.java').write_text('class NewUntracked {}')
                dirty=workspace.collect(snapshot['repository_id'],release['id'],config)
                assert ok(client.post('/api/v2/context/assemble',headers=h('agent'),json={'query':'额度','target_baselines':[dirty]}))['baseline_status']=='MISMATCH'
                dataset=ok(client.post('/api/v2/evaluations/datasets',headers=h(),json={'name':'TEST ONLY Neo4j gold','split':'development','release_id':release['id'],'annotation_notes':'Synthetic technical fixture, not bank-domain gold.','cases':[{'id':'alias','query':'授信天花板','domain':'credit','complexity':'simple','expected_ids':[key,found['concepts'][0]['id']]},{'id':'no-answer','query':'unrelated-zebra-example','domain':'other','complexity':'simple','expected_ids':[]}]}))
                scored=ok(client.post('/api/v2/evaluations/datasets/'+dataset['id']+'/runs',headers=h()))
                assert scored['summary']['micro']['tp']==2 and scored['summary']['micro']['fn']==0 and scored['summary']['errors']==0 and scored['summary']['no_answer']['correct_abstentions']==1
                assert client.get('/api/v2/ontology/entities',headers=h('agent')).status_code==403
                assert client.get('/api/v2/context/evidence/not-authorized',headers=h('agent')).status_code==404
                # Confirm the actual index contains searchable rule fields, not just alias-side scoring.
                with studio.graph.driver() as driver:
                    rows,_,_=driver.execute_query('CALL db.index.fulltext.queryNodes("bos_context_terms",$q) YIELD node WHERE node.graph_id=$id RETURN node.id AS id',q='graph_id:"'+release['id']+'" AND search_text:"100"',id=release['id'])
                    assert any(r['id']==key for r in rows)
                print(json.dumps({'isolated':True,'authority':'temporary-sqlite','graph_backend':'real-neo4j','concept_graph':True,'alias_retrieval':True,'fulltext_index':True,'evidence':True,'token_usage':package['token_usage'],'token_budget':4000,'fixed_snapshot_match':True,'signed_workspace_match':True,'untracked_file_mismatch':True,'evaluation_tp':2,'evaluation_no_answer_abstentions':1,'agent_write_denied':True,'paid_model_calls':0}))
        finally:
            # Never enumerate/delete production graphs; only exact IDs created above.
            with studio.graph.driver() as driver:
                for key in graph_ids:
                    driver.execute_query('MATCH (n:BOSNode {graph_id:$id}) DETACH DELETE n',id=key)
                    driver.execute_query('MATCH (g:BOSGraph {id:$id}) DELETE g',id=key)

if __name__=='__main__': verify()
