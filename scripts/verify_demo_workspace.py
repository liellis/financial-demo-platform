"""Read-only live release/baseline acceptance; mutation cases use temporary copies."""
import base64,copy,json,shutil,sys,tempfile,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import httpx
from cryptography.hazmat.primitives import serialization
from backend import workspace
from backend.contracts import DomainError,digest


def verify():
    runtime=ROOT/'.data/m2/runtime';config=ROOT/'.data/m2/workspaces.json'
    token=json.loads((runtime/'access-tokens.json').read_text())['agent']
    with httpx.Client(base_url='http://127.0.0.1:8002/api/v2',headers={'Authorization':'Bearer '+token},trust_env=False,timeout=90) as client:
        def ok(response):response.raise_for_status();return response.json()
        manifest=ok(client.get('/context/baseline-manifest'));release_id=manifest['release_id']
        if len(manifest['sources'])!=1:raise RuntimeError('Exactly one reviewed demo source required.')
        source=manifest['sources'][0];repo=source['repository_id'];host=json.loads(config.read_text());root=Path(host['repositories'][repo]['path'])
        original=workspace.scan(root);assert digest(original)==source['content_hash']
        target=workspace.collect(repo,release_id,config)
        def assemble(t):return client.post('/context/assemble',json={'query':'支付校验','release_id':release_id,'token_budget':4000,'target_baselines':[t]})
        package=ok(assemble(target));assert package['baseline_status']=='MATCH' and package['baseline_comparisons'][0]['basis']=='SIGNED_WORKSPACE_OBSERVATION'
        assert package['rules'] and package['concepts'];assert package['token_usage']<=4000
        search=ok(client.get('/context/search',params={'q':'支付校验','release_id':release_id}));rule=search['items'][0]
        trace=ok(client.get('/context/trace/'+rule['id'],params={'release_id':release_id}));assert trace['edges']
        evidence=ok(client.get('/context/evidence/'+rule['evidence'][0]['id'],params={'release_id':release_id}));assert evidence['content']
        graph=ok(client.get('/releases/'+release_id+'/graph',params={'limit':300}));assert graph['backend']=='neo4j'
        outcomes={}
        with tempfile.TemporaryDirectory(prefix='bos-real-baseline-') as temp:
            local=Path(temp)/'copy';shutil.copytree(root,local)
            cfg=Path(temp)/'collector.json';cfg.write_text(json.dumps({'private_key_file':host['private_key_file'],'repositories':{repo:{'path':str(local)}}}))
            chosen=next(f for f in original if f['path'].endswith('/service/payment/PaymentService.java'));path=local/chosen['path'];raw=path.read_bytes()
            path.write_bytes(raw+b'\n// temporary baseline verification\n')
            outcomes['modified']=ok(assemble(workspace.collect(repo,release_id,cfg)))['baseline_status'];path.write_bytes(raw)
            added=local/'BaselineNewUntracked.java';added.write_text('class BaselineNewUntracked {}\n');outcomes['added']=ok(assemble(workspace.collect(repo,release_id,cfg)))['baseline_status'];added.unlink()
            path.unlink();outcomes['deleted']=ok(assemble(workspace.collect(repo,release_id,cfg)))['baseline_status'];path.write_bytes(raw)
            assert all(v=='MISMATCH' for v in outcomes.values())
        tampered=copy.deepcopy(target);tampered['attestation']['payload']['content_hash']='0'*64
        assert assemble(tampered).status_code==422
        expired=copy.deepcopy(target);payload=expired['attestation']['payload'];now=int(time.time());payload['observed_at']=now-301;payload['expires_at']=now-1
        private=serialization.load_pem_private_key(Path(host['private_key_file']).read_bytes(),password=None)
        expired['attestation']['signature']=base64.b64encode(private.sign(workspace.canonical(payload))).decode()
        invalid=assemble(expired);assert invalid.status_code==422 and invalid.json()['code']=='WORKSPACE_ATTESTATION_INVALID_OR_EXPIRED'
        assert workspace.scan(root)==original
        report={'release_id':release_id,'repository_id':repo,'graph_backend':graph['backend'],'file_count':len(original),'real_workspace':'MATCH','changes_on_temporary_copy':outcomes,'expired_rejected':True,'tampered_rejected':True,'original_workspace_unchanged':True,'published_search_trace_evidence':True,'token_usage':package['token_usage'],'token_budget':4000,'paid_model_calls':0}
        (ROOT/'.data/m2/demo-workspace-acceptance.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,ensure_ascii=False))

if __name__=='__main__':verify()
