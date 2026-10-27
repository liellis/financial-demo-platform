import copy
import json
import time
import pytest
from test_m1 import env, model_server, retry_fixture, headers
from backend.contracts import DomainError
from backend.execution import states_for, report


def failed_fixture(env, model_server, monkeypatch):
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    def call(*args):
        if next(iter(args[1])) != 'C0.java':
            raise DomainError('MAPPING_EVIDENCE_REQUIRED', 422)
        return output(*args)
    monkeypatch.setattr(studio.models, 'call', call)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    while studio.process_one():
        pass
    calls = []
    def advice(config, system, user, schema):
        data = json.loads(user)
        assert 'SOURCE_DATA' not in data and 'credential' not in user
        assert 'DIAGNOSTICS' in data and 'OUTPUT_SCHEMA' in data
        calls.append(data)
        return {'summary': '映射证据需修正', 'groups': [{'group_id': g['group_id'], 'action': 'RETRY', 'reason': '证据必须落在实体范围内', 'guidance': '选择当前批次实体，引用其范围内的逐字原文；无支持规则则不输出。'} for g in data['DIAGNOSTICS']]}, {'prompt_tokens': 10, 'completion_tokens': 20}
    monkeypatch.setattr(studio.models, 'call_json', advice)
    return env[0], studio, studio.get('job', job['id']), output, calls


def generate(client, job, request_id='request-recovery-0001', role='admin'):
    return client.post(f"/api/v2/knowledge-jobs/{job['id']}/recovery-advice", headers=headers(role), json={'request_id': request_id, 'language': 'zh'})


def approve(client, job, advice, include_unknown=False, **changes):
    return client.post(f"/api/v2/knowledge-jobs/{job['id']}/recovery-advice/{advice['id']}/approve", headers=headers(), json={'expected_revision': advice['revision'], 'group_ids': [g['group_id'] for g in advice['groups'] if g['executable']], 'include_unknown': include_unknown, **changes})


def test_recovery_idempotence_approval_guided_execution_and_retained_success(env, model_server, monkeypatch):
    client, studio, job, output, calls = failed_fixture(env, model_server, monkeypatch)
    before = copy.deepcopy(job)
    result = generate(client, job)
    assert result.status_code == 200, result.text
    advice = result.json()
    assert advice['status'] == 'READY' and len(advice['groups']) == 1
    assert len(advice['groups'][0]['targets']) == 2 and advice['groups'][0]['executable']
    assert studio.get('job', job['id']) == before  # Diagnosis is never execution.
    assert generate(client, job).json() == advice and len(calls) == 1
    assert approve(client, job, advice).status_code == 200
    approved = studio.get('job', job['id'])
    assert approved['dispatch_indices'] == [2, 3] and approved['status'] == 'QUEUED'
    assert approved['batch_states'][0] == before['batch_states'][0]
    assert approved['checkpoint'] == before['checkpoint']
    assert approve(client, job, advice).status_code == 409
    sent=[]
    def guided(*args):
        assert args[0]['recovery_guidance']
        sent.append(next(iter(args[1])))
        return output(*args)
    monkeypatch.setattr(studio.models, 'call', guided)
    while studio.process_one():
        pass
    assert sent == ['C1.java', 'C2.java']
    finished = studio.get('job', job['id'])
    assert finished['status'] == 'SUCCEEDED' and finished['batch_summary']['SUCCEEDED'] == 3
    assert studio.store.all('release') == []
    assert any(a['action'] == 'RECOVERY_ADVICE_APPROVE' for a in studio.store.all('audit'))


def test_recovery_stale_revision_atomicity_and_unknown_confirmation(env, model_server, monkeypatch):
    client, studio, job, _, _ = failed_fixture(env, model_server, monkeypatch)
    with studio.store.transaction() as conn:
        job['batch_states'][1]['status']='UNKNOWN'; job['batch_states'][1]['error']='MODEL_TIMEOUT'
        report(job); job=studio.store.update('job', job, job['revision'], conn)
    advice=generate(client,job).json()
    assert any(g['requires_unknown_confirmation'] for g in advice['groups'])
    before=copy.deepcopy(studio.get('job',job['id']))
    result=approve(client,job,advice)
    assert result.status_code==409 and result.json()['code']=='UNKNOWN_REPLAY_CONFIRMATION_REQUIRED'
    assert studio.get('job',job['id'])==before
    with studio.store.transaction() as conn:
        fresh=studio.get('job',job['id'],conn);fresh['batch_states'][2]['revision']+=1
        studio.store.update('job',fresh,fresh['revision'],conn)
    assert approve(client,job,advice,True).json()['code']=='SUBTASK_REVISION_CONFLICT'
    assert studio.get('job',job['id'])['batch_states'][1]['status']=='UNKNOWN'
    fresh_advice=generate(client,job,'request-recovery-0002').json()
    assert approve(client,job,fresh_advice,True).status_code==200


def test_recovery_permission_guards_failure_and_manual_prerequisites(env, model_server, monkeypatch):
    client, studio, job, _, calls = failed_fixture(env, model_server, monkeypatch)
    assert generate(client,job,role='reviewer').status_code==403
    path=f"/api/v2/knowledge-jobs/{job['id']}/recovery-advice"
    assert client.get(path,headers=headers('agent')).status_code==403
    with studio.store.transaction() as conn:
        for b in job['batch_states'][1:]: b['error']='MODEL_AUTHENTICATION_FAILED'; b['status']='FAILED'
        studio.store.update('job',job,job['revision'],conn)
    advice=generate(client,job).json()
    assert not advice['groups'][0]['executable'] and advice['groups'][0]['action']=='MANUAL'
    assert approve(client,job,advice,group_ids=[advice['groups'][0]['group_id']]).status_code==422
    def invalid(*args):return {'summary':'bad','groups':[{'group_id':'invented','action':'RETRY','reason':'x','guidance':'x'}]},{}
    monkeypatch.setattr(studio.models,'call_json',invalid)
    failed=generate(client,job,'request-recovery-0002').json()
    assert failed['status']=='FAILED' and failed['error']=='ADVICE_OUTPUT_INVALID'
    assert client.get(path,headers=headers()).json()['id']==failed['id']
    def timeout(*args):raise DomainError('MODEL_TIMEOUT',504)
    monkeypatch.setattr(studio.models,'call_json',timeout)
    failed=generate(client,job,'request-recovery-0003').json()
    assert failed['status']=='FAILED' and failed['error']=='MODEL_TIMEOUT'
    assert studio.get('job',job['id'])['status']=='PARTIAL_SUCCEEDED'


def test_recovery_expiry_and_guidance_never_bypasses_validation(env, model_server, monkeypatch):
    client, studio, job, _, _ = failed_fixture(env, model_server, monkeypatch)
    advice=generate(client,job).json()
    with studio.store.transaction() as conn:
        studio.store.update('recovery_advice',{**advice,'expires_at':time.time()-1},advice['revision'],conn)
    expired=studio.get('recovery_advice',advice['id'])
    assert approve(client,job,expired).json()['code']=='ADVICE_STALE'
    fresh=generate(client,job,'request-recovery-0002').json()
    assert approve(client,job,fresh).status_code==200
    def invalid(*args):raise DomainError('MAPPING_EVIDENCE_REQUIRED',422)
    monkeypatch.setattr(studio.models,'call',invalid)
    while studio.process_one():pass
    final=studio.get('job',job['id'])
    assert final['batch_summary']['INVALID']==2 and final['batch_summary']['SUCCEEDED']==1


def test_subtasks_ignore_old_unplanned_failed_jobs(env):
    client,studio,_=env
    with studio.store.transaction() as conn:
        studio.store.insert('job',{'id':'legacy-without-plan','type':'EXTRACT','status':'FAILED'},conn)
    result=client.get('/api/v2/extraction-subtasks',headers=headers())
    assert result.status_code==200 and result.json()['all_total']==0


def test_candidate_graph_preview_preserves_review_gate_and_endpoints(env, model_server, monkeypatch):
    client,studio,job,_,_=failed_fixture(env,model_server,monkeypatch)
    endpoint='/api/v2/code/subgraph'
    params={'projection_id':job['projection_id'],'include_candidates':True,'job_id':job['id'],'limit':300}
    assert client.get(endpoint,params=params,headers=headers('agent')).status_code==403
    result=client.get(endpoint,params=params,headers=headers()).json()
    assert result['preview'] and result['candidate_shown']>0
    ids={n['id'] for n in result['nodes']}
    assert all(e['source'] in ids and e['target'] in ids for e in result['edges'])
    assert all(n['status']=='CANDIDATE' for n in result['nodes'] if n['kind']=='BusinessRule')
    assert studio.store.all('release')==[]
    plain=client.get(endpoint,params={'projection_id':job['projection_id']},headers=headers()).json()
    assert not any(n['kind']=='BusinessRule' for n in plain['nodes'])
    small=client.get(endpoint,params={**params,'limit':1},headers=headers()).json()
    assert len(small['nodes'])<=1 and small['truncated']
