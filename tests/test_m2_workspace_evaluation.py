import base64
import copy
import json
from pathlib import Path
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from test_m1 import env,headers
from test_m2 import released
from backend import workspace,evaluation
from backend.context import ContextService
from backend.contracts import DomainError


def collector(env):
    release,snapshot=released(env);client,studio,settings=env
    key=Ed25519PrivateKey.generate();private=settings.data_dir/'private.pem'
    private.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
    (settings.data_dir/'workspace-public-key.pem').write_bytes(key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
    config=settings.data_dir/'collector.json';root=settings.data_dir/'snapshots'/snapshot['id']
    config.write_text(json.dumps({'private_key_file':str(private),'repositories':{snapshot['repository_id']:{'path':str(root)}}}))
    return client,studio,release,snapshot,key,config,root


def test_signed_workspace_match_dirty_added_deleted_and_untrusted_claim(env):
    client,studio,release,snapshot,key,config,root=collector(env)
    def package():
        report=workspace.collect(snapshot['repository_id'],release['id'],config)
        response=client.post('/api/v2/context/assemble',headers=headers('agent'),json={'query':'额度','target_baselines':[report]})
        assert response.status_code==200,response.text
        return response.json()
    result=package()
    assert result['baseline_status']=='MATCH' and result['baseline_comparisons'][0]['basis']=='SIGNED_WORKSPACE_OBSERVATION'
    assert 'attestation' not in result['target_baselines'][0]
    source=root/'src/credit/Limit.java';original=source.read_text();source.write_text(original.replace('100','101'))
    assert package()['baseline_status']=='MISMATCH'
    source.write_text(original);added=root/'Untracked.java';added.write_text('class Untracked {}')
    assert package()['baseline_status']=='MISMATCH'
    added.unlink();source.unlink();(root/'Other.java').write_text('class Other {}')
    assert package()['baseline_status']=='MISMATCH'
    claimed=ContextService(studio).assemble('额度',target_baselines=[{k:snapshot[k] for k in ('repository_id','content_hash','scope_manifest_hash')}])
    assert claimed['baseline_status']=='UNKNOWN'


def test_workspace_tamper_wrong_release_expiry_key_and_allowlist(env):
    client,studio,release,snapshot,key,config,root=collector(env)
    report=workspace.collect(snapshot['repository_id'],release['id'],config)
    assert client.get('/api/v2/settings/workspace',headers=headers()).json()['configured']
    bad=copy.deepcopy(report);bad['attestation']['payload']['content_hash']='0'*64
    with pytest.raises(DomainError):workspace.verify(studio.settings,bad,release['id'])
    with pytest.raises(DomainError):workspace.verify(studio.settings,report,'other-release')
    expired=copy.deepcopy(report);expired['attestation']['payload']['observed_at']-=600;expired['attestation']['payload']['expires_at']-=600
    expired['attestation']['signature']=base64.b64encode(key.sign(workspace.canonical(expired['attestation']['payload']))).decode()
    with pytest.raises(DomainError):workspace.verify(studio.settings,expired,release['id'])
    with pytest.raises(DomainError) as exc:workspace.collect('foreign',release['id'],config)
    assert exc.value.code=='WORKSPACE_NOT_CONFIGURED'
    (root/'Linked.java').symlink_to(root/'src/credit/Limit.java')
    with pytest.raises(DomainError) as exc:workspace.collect(snapshot['repository_id'],release['id'],config)
    assert exc.value.code=='WORKSPACE_SYMLINK_FORBIDDEN'
    (root/'Linked.java').unlink()
    public=studio.settings.data_dir/'workspace-public-key.pem';public.unlink()
    with pytest.raises(DomainError):workspace.verify(studio.settings,report,release['id'])


def test_workspace_changes_during_double_scan_are_not_signed(env,monkeypatch):
    _,_,release,snapshot,_,config,_=collector(env)
    values=iter([[{'path':'a.java','sha256':'a','size':1}],[{'path':'a.java','sha256':'b','size':1}]])
    monkeypatch.setattr(workspace,'scan',lambda root:next(values))
    with pytest.raises(DomainError) as exc:workspace.collect(snapshot['repository_id'],release['id'],config)
    assert exc.value.code=='WORKSPACE_CHANGED_DURING_SCAN'


def dataset(release):
    return {'name':'TEST ONLY scoring','split':'development','release_id':release['id'],'annotation_notes':'Synthetic fixture, not bank acceptance gold.','cases':[{'id':'answer','query':'额度','domain':'limit','complexity':'simple','expected_ids':[release['rules'][0]['id']]},{'id':'no-answer','query':'completely-unrelated-zebra','domain':'other','complexity':'medium','expected_ids':[]}]}


def test_evaluation_permissions_frozen_manifest_metrics_and_no_answer(env):
    release,_=released(env);client,studio,_=env;data=dataset(release)
    assert client.post('/api/v2/evaluations/datasets',headers=headers('agent'),json=data).status_code==403
    saved=client.post('/api/v2/evaluations/datasets',headers=headers(),json=data).json()
    assert client.post('/api/v2/evaluations/datasets',headers=headers(),json=data).json()['id']==saved['id']
    response=client.post('/api/v2/evaluations/datasets/'+saved['id']+'/runs',headers=headers())
    assert response.status_code==200,response.text
    run=response.json();assert run['summary']['micro']['tp']==1 and run['summary']['micro']['fn']==0
    assert run['summary']['no_answer']=={'samples':1,'correct_abstentions':1,'incorrect_answers':0,'errors':0}
    assert run['manifest']['protocol']['token_budget']==4000 and run['manifest']['model_calls']==0
    assert run['cases'][0]['package']['knowledge_items'][0]['id']==release['rules'][0]['id']
    assert client.get('/api/v2/evaluations/runs/'+run['id'],headers=headers('agent')).status_code==403
    bad={**data,'cases':[{**data['cases'][0],'expected_ids':['foreign']}]}
    assert client.post('/api/v2/evaluations/datasets',headers=headers(),json=bad).status_code==422
    assert evaluation.metrics(0,0,0)['recall'] is None
    assert client.post('/api/v2/evaluations/datasets',headers=headers(),json={**data,'split':'acceptance'}).status_code==409


def test_evaluation_duplicate_incomplete_topk_and_errors_count_as_missing(env,monkeypatch):
    release,_=released(env);_,studio,_=env;case=dataset(release)['cases'][0]
    package=ContextService(studio).assemble('额度')
    package['knowledge_items']*=2
    scored=evaluation.score(studio,release,case,package)
    assert (scored['tp'],scored['fp'],scored['fn'])==(1,1,0)
    package['rules'][0]['exceptions']=['invented exception']
    scored=evaluation.score(studio,release,case,package)
    assert (scored['tp'],scored['fp'],scored['fn'])==(0,2,1)
    package['knowledge_items']=[{'kind':'BusinessRule','id':'unknown'}]*10+[{'kind':'BusinessRule','id':case['expected_ids'][0]}]
    scored=evaluation.score(studio,release,case,package)
    assert (scored['tp'],scored['fp'],scored['fn'])==(0,10,1)
    saved=evaluation.create_dataset(studio,dataset(release),'admin')
    def fail(*a,**kw):raise DomainError('GRAPH_UNAVAILABLE',503)
    monkeypatch.setattr(ContextService,'assemble',fail)
    run=evaluation.run(studio,saved['id'],'admin')
    assert run['summary']['errors']==2 and run['summary']['micro']['fn']==1
    assert run['summary']['no_answer']['correct_abstentions']==0
