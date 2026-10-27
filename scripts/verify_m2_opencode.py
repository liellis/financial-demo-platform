"""Opt-in real OpenCode acceptance on isolated synthetic knowledge.

Requires --live-model or zero-cost --probe-tools. --published-demo additionally
requires explicit authorization to send the approved demo data to the provider.
Uses an existing allowed platform model behind a bounded
loopback proxy (at most 8 requests, 1024 output tokens/request). The provider
secret stays in this process; no production knowledge is approved or published.
"""
import argparse
import io
import json
import os
import secrets
import signal
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import zipfile
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import httpx
import uvicorn
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from starlette.responses import Response
from backend.api import create_app
from backend.config import Settings


def verify(model_id=None,probe=False,published_demo=False):
    runtime=ROOT/'.data/m2/runtime'
    token=json.loads((runtime/'access-tokens.json').read_text())['admin']
    with httpx.Client(trust_env=False,timeout=15) as client:
        response=client.get('http://127.0.0.1:8002/api/v2/model-connections',headers={'Authorization':'Bearer '+token});response.raise_for_status()
        candidates=[m for m in response.json() if m['enabled'] and m['allow_source'] and (not model_id or m['id']==model_id)]
    if not candidates:raise RuntimeError('No authorized enabled model configuration found.')
    model=candidates[0]
    if model.get('credential_mode')=='api_key':
        key=Fernet((runtime/'credentials/master.key').read_bytes()).decrypt((runtime/'credentials'/(model['credential_reference']+'.enc')).read_bytes()).decode()
    else:key=os.environ.get(model['credential_env'],'')
    if not key:raise RuntimeError('Model credential unavailable; no request made.')
    from urllib.parse import urlparse
    if urlparse(model['endpoint']).scheme!='https':raise RuntimeError('Live acceptance requires the configured HTTPS provider.')
    proxy_token=secrets.token_urlsafe(24);calls=[];observations=[]
    class Proxy(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_POST(self):
            if self.headers.get('Authorization')!='Bearer '+proxy_token:return self.send_error(401)
            length=int(self.headers.get('Content-Length','0'))
            if not 0<length<=80000 or len(calls)>=8:return self.send_error(429)
            body=json.loads(self.rfile.read(length))
            original_tools=[t.get('function',{}).get('name') for t in body.get('tools',[])]
            allowed={'bank-ontology_'+name for name in ('search_business_concepts','trace_business_to_code','get_evidence','assemble_business_context')}
            if body.get('tools'):
                body['tools']=[t for t in body['tools'] if t.get('function',{}).get('name') in allowed]
                if not body['tools']:
                    body.pop('tools',None)
                    body.pop('tool_choice',None)
            body['model']=model['model_id'];body['max_tokens']=1024
            if urlparse(model['endpoint']).hostname=='api.deepseek.com':body['thinking']={'type':'disabled'}
            calls.append({'index':len(calls)+1,'input_chars':length,'output_limit':1024,'original_tools':original_tools,'request_fields':sorted(body),'available_tools':[t.get('function',{}).get('name',t.get('name')) for t in body.get('tools',[])]})
            if probe:
                self.send_response(200);self.send_header('Content-Type','text/event-stream' if body.get('stream') else 'application/json');self.end_headers()
                response={'id':'test-only','object':'chat.completion','created':0,'model':body['model'],'choices':[{'index':0,'message':{'role':'assistant','content':'TOOL_PROBE_ONLY'},'finish_reason':'stop'}],'usage':{'prompt_tokens':1,'completion_tokens':1,'total_tokens':2}}
                if body.get('stream'):
                    response['object']='chat.completion.chunk';response['choices'][0]={'index':0,'delta':{'role':'assistant','content':'TOOL_PROBE_ONLY'},'finish_reason':'stop'}
                    self.wfile.write(('data: '+json.dumps(response)+'\n\ndata: [DONE]\n\n').encode())
                else:self.wfile.write(json.dumps(response).encode())
                return
            try:
                with httpx.Client(timeout=90,trust_env=False,follow_redirects=False) as client:
                    with client.stream('POST',model['endpoint'].rstrip('/')+'/chat/completions',headers={'Authorization':'Bearer '+key},json=body) as response:
                        if response.status_code!=200:return self.send_error(502,'Provider request rejected')
                        raw=response.read()
                        decoded=[]
                        if body.get('stream'):
                            for line in raw.decode().splitlines():
                                if line.startswith('data: ') and line[6:]!='[DONE]':decoded.append(json.loads(line[6:]))
                        else:decoded=[json.loads(raw)]
                        tool_names=[];finish=[]
                        for item in decoded:
                            for choice in item.get('choices',[]):
                                finish.append(choice.get('finish_reason'))
                                for call in (choice.get('delta') or choice.get('message') or {}).get('tool_calls',[]):
                                    name=call.get('function',{}).get('name')
                                    if name:
                                        if name not in allowed:return self.send_error(502,'Unexpected non-read-only tool rejected')
                                        tool_names.append(name)
                        calls[-1].update(returned_tools=tool_names,finish_reasons=[f for f in finish if f])
                        self.send_response(200);self.send_header('Content-Type',response.headers.get('Content-Type','text/event-stream'));self.send_header('Content-Length',str(len(raw)));self.end_headers()
                        self.wfile.write(raw);self.wfile.flush()
            except (httpx.HTTPError,BrokenPipeError):pass
    proxy=ThreadingHTTPServer(('127.0.0.1',0),Proxy);threading.Thread(target=proxy.serve_forever,daemon=True).start()
    with tempfile.TemporaryDirectory(prefix='bos-opencode-') as temp:
        folder=Path(temp);data=folder/'runtime';workspace_root=folder/'workspace';workspace_root.mkdir()
        if published_demo:
            app=FastAPI()
            collector=ROOT/'.data/m2/workspaces.json';agent_token=ROOT/'.data/m2/opencode-agent.token'
            agent_secret=agent_token.read_text().strip()
            approved=json.loads((ROOT/'.data/m2/demo-release-acceptance.json').read_text())
            approved_release=approved['release_id']
            with httpx.Client(trust_env=False,timeout=45) as read_client:
                listed=read_client.get('http://127.0.0.1:8002/api/v2/context/search',params={'q':'支付校验','release_id':approved_release},headers={'Authorization':'Bearer '+agent_secret})
                listed.raise_for_status();listed=listed.json()
            allowed_paths={'context/trace/'+r['id'] for r in listed['items'] if r['id'] in approved['rule_ids']}
            allowed_paths.update('context/evidence/'+e['id'] for r in listed['items'] if r['id'] in approved['rule_ids'] for e in r['evidence'])
            @app.api_route('/api/v2/{path:path}',methods=['GET','POST'])
            async def published_read_proxy(path:str,request:Request):
                # Agent credential and a strict endpoint allowlist forbid approvals or writes.
                if path not in ('context/search','context/assemble','context/baseline-manifest') and path not in allowed_paths:
                    return Response(status_code=403)
                if request.method=='POST' and path!='context/assemble':return Response(status_code=403)
                params=dict(request.query_params)
                if params.get('release_id',approved_release)!=approved_release:return Response(status_code=403)
                params['release_id']=approved_release
                body=await request.body()
                if request.method=='POST':
                    payload=json.loads(body)
                    if payload.get('release_id') not in (None,approved_release) or payload.get('token_budget',4000)>4000:return Response(status_code=403)
                    payload['release_id']=approved_release;body=json.dumps(payload).encode()
                async with httpx.AsyncClient(trust_env=False,timeout=90) as client:
                    upstream=await client.request(request.method,'http://127.0.0.1:8002/api/v2/'+path,params=params,content=body,headers={'Authorization':'Bearer '+agent_secret,'Content-Type':'application/json'})
                return Response(upstream.content,status_code=upstream.status_code,media_type='application/json')
        else:
            settings=Settings(data_dir=data,database_url='sqlite:///'+str(data/'test.db'),neo4j_uri='',codegraph_url='',tokens={r:secrets.token_urlsafe(24) for r in ('admin','developer','reviewer','agent')})
            app=create_app(settings)
            def h(role='admin'):return {'Authorization':'Bearer '+settings.tokens[role]}
            def ok(r):
                if r.status_code!=200:raise RuntimeError('Isolated acceptance setup failed: '+str(r.status_code))
                return r.json()
            with TestClient(app) as client:
                archive=io.BytesIO()
                source='class Limit {\n boolean allowed(int amount) {\n  return amount <= 100;\n }\n}\n'
                with zipfile.ZipFile(archive,'w') as z:z.writestr('src/Limit.java',source)
                snapshot=ok(client.post('/api/v2/sources/upload',headers=h(),files={'file':('test.zip',archive.getvalue(),'application/zip')},data={'name':'TEST ONLY OpenCode acceptance'}))
                studio=app.state.studio;projection=studio.sources.analyze(snapshot,engine='javac');method=next(n for n in projection['entities'] if n['kind']=='Method' and n['name']=='allowed')
                with studio.store.transaction() as conn:
                    rule=studio.store.insert('candidate',{'id':'test-opencode-limit','name':'额度上限校验','condition':'amount <= 100','outcome':'允许；超过100返回false','scope':'TEST ONLY integer amount','exceptions':[],'uncertainties':[],'entity_version_id':method['id'],'evidence':[{'path':'src/Limit.java','start_line':3,'end_line':3,'quote':'return amount <= 100;'}],'status':'CANDIDATE','snapshot_id':snapshot['id'],'projection_id':projection['id'],'content_revision':1,'origin_kind':'TEST_FIXTURE'},conn)
                rule=ok(client.post('/api/v2/reviews/'+rule['id']+'/decision',headers=h('reviewer'),json={'decision':'APPROVE','reason':'TEST ONLY source checked','expected_revision':rule['revision']}))
                release=ok(client.post('/api/v2/releases',headers=h(),json={'candidate_ids':[rule['id']],'expected_revision':0,'reason':'TEST ONLY isolated acceptance'}))
            shutil.copytree(data/'snapshots'/snapshot['id'],workspace_root,dirs_exist_ok=True)
            signing=Ed25519PrivateKey.generate();private=folder/'host-private.pem';private.write_bytes(signing.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()));private.chmod(0o600)
            (data/'workspace-public-key.pem').write_bytes(signing.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
            collector=folder/'collector.json';collector.write_text(json.dumps({'private_key_file':str(private),'repositories':{snapshot['repository_id']:{'path':str(workspace_root)}}}))
            agent_token=folder/'agent.token';agent_token.write_text(settings.tokens['agent']);agent_token.chmod(0o600)
        @app.middleware('http')
        async def observe(request,call_next):
            response=await call_next(request)
            if request.url.path.startswith('/api/v2/context/'):
                raw=b''.join([chunk async for chunk in response.body_iterator]);item={'path':request.url.path,'status':response.status_code}
                if request.url.path.endswith('/assemble') and response.status_code==200:
                    result=json.loads(raw);item.update(baseline_status=result['baseline_status'],basis=result['baseline_comparisons'][0]['basis'],token_usage=result['token_usage'])
                observations.append(item)
                return Response(raw,status_code=response.status_code,headers=dict(response.headers),media_type=response.media_type)
            return response
        sock=socket.socket();sock.bind(('127.0.0.1',0));api_port=sock.getsockname()[1]
        server=uvicorn.Server(uvicorn.Config(app,log_level='error',access_log=False));thread=threading.Thread(target=lambda:server.run(sockets=[sock]),daemon=True);thread.start()
        for _ in range(100):
            if server.started:break
            time.sleep(.05)
        mcp_server={'type':'local','command':[sys.executable,'-m','backend.mcp_server'],'codemode':False,'environment':{'PYTHONPATH':str(ROOT),'BOS_API_URL':f'http://127.0.0.1:{api_port}','BOS_MCP_TOKEN_FILE':str(agent_token),'BOS_WORKSPACE_CONFIG_FILE':str(collector)}}
        # The isolated proxy exposes only the four read-only MCP functions and rejects any other returned tool call.
        permissions=[{'action':'*','resource':'*','effect':'allow'}]
        config={'$schema':'https://opencode.ai/config.json','model':'bos-test/'+model['model_id'],'providers':{'bos-test':{'package':'@ai-sdk/openai-compatible','name':'Bounded isolated acceptance','settings':{'baseURL':f'http://127.0.0.1:{proxy.server_port}/v1','apiKey':proxy_token},'models':{model['model_id']:{'name':model['model_id'],'capabilities':{'tools':True,'input':['text'],'output':['text']},'limit':{'context':32000,'output':1024}}}}},'permissions':permissions,'agents':{'ontology-check':{'mode':'primary','description':'Read-only ontology acceptance','system':'You are a read-only ontology verification agent. Invoke the four bank-ontology_* function tools directly using their exact names in the tool list. Never use execute, JavaScript, tool discovery, filesystem, shell, subagents or web tools. Return a short source-cited Chinese answer after all four direct tools succeed.','steps':8,'permissions':permissions}},'mcp':{'servers':{'bank-ontology':mcp_server}}}
        config_file=workspace_root/'opencode.json';config_file.write_text(json.dumps(config));config_file.chmod(0o600)
        environment={**os.environ,'PWD':str(workspace_root),'OPENCODE_CONFIG':str(config_file),'XDG_DATA_HOME':str(folder/'xdg-data'),'XDG_STATE_HOME':str(folder/'xdg-state'),'XDG_CONFIG_HOME':str(folder/'xdg-config')}
        environment['OPENCODE_PASSWORD']=secrets.token_urlsafe(24)
        prompt='这是隔离的只读技术验收。直接调用 bank-ontology_search_business_concepts、bank-ontology_trace_business_to_code、bank-ontology_get_evidence、bank-ontology_assemble_business_context 四个函数工具。不要使用 execute，不要寻找资源，不要写 JavaScript，不要调用其他工具。依次执行：search_business_concepts(query="额度")；对返回规则调用 trace_business_to_code；对返回证据调用 get_evidence；最后调用 assemble_business_context(query="额度",token_budget=4000)，不要手动传 target_baselines，由宿主采集器自动核验。完成全部四步后用中文说明超过100如何处理，引用准确文件行号，并报告 baseline_status。不要修改任何文件。'
        if published_demo:
            prompt=prompt.replace('隔离的只读技术验收','已获用户批准发布的demo知识只读验收').replace('额度','支付校验').replace('超过100如何处理','金额为空、零、正数以及保留两位小数时如何处理；明确局部规则不表示所有支付校验通过')
        report_path=ROOT/('.data/m2/opencode-tool-probe.json' if probe else '.data/m2/opencode-published-acceptance.json' if published_demo else '.data/m2/opencode-live-acceptance.json')
        oc_process=None;preflight={}
        try:
            with socket.socket() as port_socket:port_socket.bind(('127.0.0.1',0));oc_port=port_socket.getsockname()[1]
            oc_log=(folder/'opencode-server.log').open('wb')
            oc_process=subprocess.Popen(['opencode','serve','--hostname','127.0.0.1','--port',str(oc_port),'--print-logs','--log-level','debug'],cwd=workspace_root,env=environment,stdout=oc_log,stderr=subprocess.STDOUT,start_new_session=True)
            oc_url=f'http://127.0.0.1:{oc_port}'
            with httpx.Client(base_url=oc_url,timeout=10,trust_env=False,auth=('opencode',environment['OPENCODE_PASSWORD'])) as client:
                for _ in range(40):
                    try:
                        r=client.get('/api/mcp',params={'location[directory]':str(workspace_root)})
                        if r.status_code==200:
                            preflight['mcp']=r.json()
                            if 'connected' in json.dumps(preflight['mcp']).lower():break
                    except httpx.HTTPError:pass
                    if oc_process.poll() is not None:break
                    time.sleep(.25)
                models=client.get('/api/model',params={'location[directory]':str(workspace_root)}).json()
                preflight['models']=[{k:m.get(k) for k in ('id','providerID','modelID','capabilities','enabled')} for m in models.get('data',[]) if m.get('providerID')=='bos-test']
            # Pin the native session location explicitly; CLI run can resolve a different location catalog.
            with httpx.Client(base_url=oc_url,timeout=15,trust_env=False,auth=('opencode',environment['OPENCODE_PASSWORD'])) as client:
                r=client.post('/api/session',json={'title':'Bounded read-only ontology acceptance','location':{'directory':str(workspace_root)},'agent':'ontology-check','model':{'providerID':'bos-test','id':model['model_id']}})
                r.raise_for_status();session=r.json()['data'];preflight['session_location']=session['location']
                sid=session['id']
                r=client.post('/api/session/'+sid+'/prompt',json={'text':prompt});r.raise_for_status()
                for _ in range(30):
                    try:
                        done=client.post('/api/experimental/session/'+sid+'/wait',timeout=10)
                        if done.status_code==204:break
                        done.raise_for_status()
                    except httpx.ReadTimeout:continue
                messages=client.get('/api/session/'+sid+'/message').json()
                session=client.get('/api/session/'+sid).json()
                from types import SimpleNamespace
                result=SimpleNamespace(returncode=0 if session['data'].get('outcome')=='succeeded' else 1,stdout=json.dumps(messages,ensure_ascii=False),stderr='')
            required=['/search','/trace/','/evidence/','/assemble']
            complete=all(any(part in r['path'] and r['status']==200 for r in observations) for part in required)
            signed=any(r.get('baseline_status')=='MATCH' and r.get('basis')=='SIGNED_WORKSPACE_OBSERVATION' for r in observations)
            report={'client':'OpenCode','version':subprocess.check_output(['opencode','--version'],env=environment,text=True).strip(),'isolated':not published_demo,'knowledge_fixture':'approved-demo-release' if published_demo else 'synthetic-limit-100','authority':'postgresql' if published_demo else 'temporary-sqlite','graph':'neo4j' if published_demo else 'local-manifest (Neo4j tested separately)','model_id':model['model_id'],'model_calls':calls,'observations':observations,'preflight':preflight,'all_four_tools':complete,'signed_workspace_match':signed,'returncode':result.returncode,'stdout':result.stdout,'stderr':result.stderr,'quality_acceptance':False}
            report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2));report_path.chmod(0o600)
            print(json.dumps({k:v for k,v in report.items() if k not in ('stdout','stderr','observations','model_calls')}|{'model_calls':len(calls),'http_observations':len(observations)},ensure_ascii=False),flush=True)
            if probe:
                print(json.dumps({'probe_only':True,'paid_model_calls':0,'available_tools':sorted({t for c in calls for t in c['available_tools']})}));return
            if not (result.returncode==0 and complete and signed):raise RuntimeError('Live acceptance incomplete; inspect the private report. No production data was changed.')
        finally:
            if oc_process:
                try:os.killpg(oc_process.pid,signal.SIGTERM)
                except ProcessLookupError:pass
                try:oc_process.wait(timeout=5)
                except subprocess.TimeoutExpired:os.killpg(oc_process.pid,signal.SIGKILL);oc_process.wait()
                oc_log.close()
            server.should_exit=True;thread.join(timeout=5);sock.close();proxy.shutdown();proxy.server_close()


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--live-model',action='store_true');parser.add_argument('--probe-tools',action='store_true');parser.add_argument('--model-config-id');parser.add_argument('--published-demo',action='store_true');args=parser.parse_args()
    if not args.live_model and not args.probe_tools:parser.error('Explicit --live-model or --probe-tools is required.')
    verify(args.model_config_id,args.probe_tools,args.published_demo)
