import json
from pathlib import Path
import pytest
from test_m1 import env,upload,parse,seed_candidate,approve,publish,headers
from backend.context import ContextService
from backend.contracts import DomainError
from backend.migrate import export,restore
from backend.store import Store


def released(env):
    client,studio,_=env
    snapshot=upload(client);projection=parse(studio,snapshot);item=seed_candidate(studio,snapshot,projection)
    approved=approve(client,item);result=publish(client,approved)
    assert result.status_code==200,result.text
    return result.json(),snapshot


def test_context_no_candidate_leak_and_role_boundary(env):
    client,studio,_=env
    snapshot=upload(client);seed_candidate(studio,snapshot,parse(studio,snapshot),key='unreviewed')
    assert client.get('/api/v2/context/search',params={'q':'额度'},headers=headers('agent')).json()['code']=='NO_PUBLISHED_KNOWLEDGE'
    release,snapshot=released(env)
    assert client.get('/api/v2/context/search',params={'q':'额度'}).status_code==401
    result=client.get('/api/v2/context/search',params={'q':'额度'},headers=headers('agent')).json()
    assert result['release_id']==release['id'] and len(result['items'])==1
    published_graph=client.get('/api/v2/releases/'+release['id']+'/graph',headers=headers('agent')).json()
    assert published_graph['preview'] is False and published_graph['nodes']
    rule=result['items'][0]
    trace=client.get('/api/v2/context/trace/'+rule['id'],headers=headers('agent')).json()
    assert any(e['type']=='IMPLEMENTED_BY' for e in trace['edges'])
    evidence=client.get('/api/v2/context/evidence/'+rule['evidence'][0]['id'],headers=headers('agent')).json()
    assert evidence['content'].strip()=='return amount <= 100;'
    assert client.get('/api/v2/context/evidence/not-authorized',headers=headers('agent')).status_code==404
    assert client.get('/api/v2/source-snapshots',headers=headers('agent')).status_code==403
    assert studio.store.get('graph_sync',release['id'])['status']=='COMMITTED'


def test_context_budget_fixed_release_and_baseline_status(env):
    release,snapshot=released(env);client,studio,_=env
    data={'query':'额度','token_budget':4000,'release_id':release['id']}
    result=client.post('/api/v2/context/assemble',headers=headers('agent'),json=data).json()
    assert result['baseline_status']=='UNKNOWN' and result['rules'] and result['token_usage']<=4000
    data['target_baselines']=[{'repository_id':snapshot['repository_id'],'source_snapshot_id':snapshot['id']}]
    matched=client.post('/api/v2/context/assemble',headers=headers('agent'),json=data).json()
    assert matched['baseline_status']=='MATCH'
    data['target_baselines']=[{'repository_id':snapshot['repository_id'],'content_hash':'wrong','scope_manifest_hash':'wrong'}]
    assert client.post('/api/v2/context/assemble',headers=headers('agent'),json=data).json()['baseline_status']=='MISMATCH'
    data['target_baselines']=[{'repository_id':'foreign','source_snapshot_id':'hidden'}]
    assert client.post('/api/v2/context/assemble',headers=headers('agent'),json=data).status_code==403
    data['target_baselines']=[];data['token_budget']=512
    response=client.post('/api/v2/context/assemble',headers=headers('agent'),json=data)
    assert response.status_code in (200,422)
    if response.status_code==200:assert response.json()['token_usage']<=512


def test_migration_integrity_idempotence_and_conflicts(env,tmp_path):
    _,studio,settings=env
    snapshot=upload(env[0]);projection=parse(studio,snapshot);seed_candidate(studio,snapshot,projection)
    # Preserve a real salted user hash and all original revisions.
    backup=tmp_path.parent/(tmp_path.name+'-backup');manifest=export(settings.data_dir,backup)
    destination=tmp_path/'destination';target='sqlite:///'+str(tmp_path/'target.db')
    result=restore(backup,target,destination)
    assert result['verified'] and result['records']==manifest['records']
    assert restore(backup,target,destination)==result
    store=Store(target)
    assert store.get('snapshot',snapshot['id'])==snapshot
    with store.transaction() as conn:store.insert('test',{'id':'different'},conn)
    with pytest.raises(ValueError,match='not empty'):restore(backup,target,destination)
    file=backup/'snapshots'/snapshot['id']/snapshot['files'][0]['path'];file.write_text('modified')
    with pytest.raises(ValueError,match='mismatch'):restore(backup,'sqlite:///'+str(tmp_path/'other.db'),tmp_path/'other')


def test_graph_failures_do_not_commit_pointer(env,monkeypatch):
    client,studio,_=env
    snapshot=upload(client);item=seed_candidate(studio,snapshot,parse(studio,snapshot));item=approve(client,item)
    def fail(manifest):raise DomainError('GRAPH_UNAVAILABLE',503)
    monkeypatch.setattr(studio.graph,'prepare',fail)
    result=publish(client,item)
    assert result.status_code==503 and not studio.store.get('pointer','current')
    assert studio.store.all('graph_sync')[0]['status']=='FAILED'
    assert studio.store.get('candidate',item['id'])['status']=='APPROVED'


def test_graph_conflicting_edge_identity_is_rejected_before_storage(env):
    from backend.graph import GraphProjection
    _,_,settings=env
    settings.neo4j_uri='bolt://127.0.0.1:1'
    graph=GraphProjection(settings)
    nodes=[{'id':'a','kind':'Method'},{'id':'b','kind':'Method'}]
    edges=[{'id':'same','source':'a','target':'b','type':'CALLS'},{'id':'same','source':'b','target':'a','type':'CALLS'}]
    with pytest.raises(DomainError) as exc:graph.prepare({'id':'test','digest':'test','nodes':nodes,'edges':edges})
    assert exc.value.code=='GRAPH_EDGE_ID_CONFLICT'
