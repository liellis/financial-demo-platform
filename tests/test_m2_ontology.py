import copy
import pytest
from test_m1 import env,upload,parse,seed_candidate,approve,publish,headers
from backend import ontology
from backend.codegraph import normalize
from backend.contracts import DomainError


def fixture(env):
    client,studio,_=env;snapshot=upload(client);projection=parse(studio,snapshot);rule=seed_candidate(studio,snapshot,projection)
    data={'kind':'BusinessConcept','name':'额度上限','definition':'额度超过限制时不允许通过','aliases':['授信天花板'],'projection_id':projection['id'],'rule_ids':[rule['id']],'code_entity_ids':[],'reason':'测试明确源代码对应关系'}
    return client,studio,snapshot,projection,rule,data


def test_ontology_review_alias_and_release_isolation(env):
    client,studio,snapshot,projection,rule,data=fixture(env)
    assert client.post('/api/v2/ontology/entities',headers=headers('agent'),json=data).status_code==403
    created=client.post('/api/v2/ontology/entities',headers=headers('developer'),json=data).json()
    assert created['status']=='CANDIDATE'
    decision={'decision':'APPROVE','expected_revision':created['revision'],'reason':'已核对成员代码范围'}
    assert client.post('/api/v2/ontology/entities/'+created['id']+'/decision',headers=headers('developer'),json=decision).status_code==403
    approved=client.post('/api/v2/ontology/entities/'+created['id']+'/decision',headers=headers('reviewer'),json=decision).json()
    assert approved['status']=='APPROVED' and studio.get('candidate',rule['id'])['status']=='CANDIDATE'
    release=publish(client,approve(client,rule)).json()
    assert release['ontology'] and release['ontology'][0]['status']=='PUBLISHED'
    found=client.get('/api/v2/context/search',headers=headers('agent'),params={'q':'授信天花板'}).json()
    assert found['items'][0]['id']==rule['id'] and found['concepts'][0]['logical_id']==created['id']
    assert client.get('/api/v2/context/trace/'+found['concepts'][0]['id'],headers=headers('agent')).status_code==200
    edited=client.put('/api/v2/ontology/entities/'+created['id'],headers=headers(),json={**data,'definition':'新定义需重新审核','expected_revision':approved['revision']}).json()
    assert edited['status']=='CANDIDATE'
    assert studio.get('release',release['id'])['ontology'][0]['definition']==data['definition']


def test_ontology_stale_member_and_projection_gate(env):
    client,studio,snapshot,projection,rule,data=fixture(env)
    created=ontology.save(studio,data,'admin')
    with studio.store.transaction() as conn:
        updated=studio.get('candidate',rule['id'],conn);updated['content_revision']+=1
        studio.store.update('candidate',updated,updated['revision'],conn)
    with pytest.raises(DomainError) as exc:ontology.decide(studio,created['id'],{'decision':'APPROVE','expected_revision':created['revision'],'reason':'stale'},'admin')
    assert exc.value.code=='ONTOLOGY_MEMBERS_CHANGED'
    assert client.post('/api/v2/ontology/entities',headers=headers(),json={**data,'rule_ids':[],'code_entity_ids':['foreign']}).status_code==422


def test_joern_normalization_preserves_source_and_unknown_calls(env):
    client,studio,snapshot,projection,rule,data=fixture(env)
    path=snapshot['files'][0]['path']
    raw={'entities':[{'key':'type','kind':'Class','name':'Limit','signature':'credit.Limit','path':path,'start_line':2,'end_line':7},{'key':'m','parent':'type','kind':'Method','name':'allowed','signature':'credit.Limit.allowed(int)','path':path,'start_line':3,'end_line':5},{'key':'bad','kind':'Method','name':'synthetic','signature':'bad','path':path,'start_line':0,'end_line':0}], 'calls':[{'from':'m','targets':['missing'],'path':path,'start_line':4,'name':'dynamic'},{'from':'m','targets':[],'external_targets':['java.lang.String.length'],'path':path,'start_line':4,'name':'length'},{'from':'m','targets':[],'path':path,'start_line':4,'name':'<operator>.lessEqualsThan'}]}
    result=normalize(snapshot,raw,{'version':'fixed'},studio.sources.read)
    assert result['analyzer']=='joern-codegraph' and len(result['unresolved_calls'])==1 and len(result['external_calls'])==1 and result['builtin_call_count']==1
    assert len(result['diagnostics'])==1 and len({n['id'] for n in result['entities']})==len(result['entities'])
    assert result['id']==normalize(snapshot,raw,{'version':'fixed'},studio.sources.read)['id']
    assert result['id']!=normalize(snapshot,raw,{'version':'different'},studio.sources.read)['id']


def test_analysis_engine_authorization_revision_and_no_silent_fallback(env,monkeypatch):
    client,studio,settings=env
    assert client.get('/api/v2/settings/analysis',headers=headers()).json()['engine']=='javac'
    assert client.put('/api/v2/settings/analysis',headers=headers('developer'),json={'engine':'javac','expected_revision':0}).status_code==403
    missing=client.put('/api/v2/settings/analysis',headers=headers(),json={'engine':'joern','expected_revision':0})
    assert missing.status_code==409 and missing.json()['code']=='CODEGRAPH_NOT_CONFIGURED'
    settings.codegraph_url='http://internal-test-only'
    configured=client.put('/api/v2/settings/analysis',headers=headers(),json={'engine':'joern','expected_revision':0})
    assert configured.status_code==200 and configured.json()['engine']=='joern'
    assert client.put('/api/v2/settings/analysis',headers=headers(),json={'engine':'javac','expected_revision':0}).status_code==409
    snapshot=upload(client)
    import backend.codegraph as codegraph
    def unavailable(*args): raise DomainError('CODEGRAPH_UNAVAILABLE',503)
    monkeypatch.setattr(codegraph,'analyze',unavailable)
    with pytest.raises(DomainError) as exc:studio.sources.analyze(snapshot,engine='joern')
    assert exc.value.code=='CODEGRAPH_UNAVAILABLE' and not studio.store.all('projection')
    # Explicit legacy analysis remains available without changing the Joern failure.
    assert studio.sources.analyze(snapshot,engine='javac')['analyzer']!='joern-codegraph'
