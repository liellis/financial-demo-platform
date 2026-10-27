"""Joern adapter: real CPG service, immutable analyzer identity and strict source ranges."""
import json
from pathlib import PurePosixPath,Path
import httpx
from .contracts import DomainError,digest,identity


def normalize(snapshot,raw,engine,read):
    version=digest({'engine':engine,'adapter':'joern-cpg-adapter-1','adapter_sha256':digest(Path(__file__).read_text())})
    pid=identity('projection',[snapshot['id'],version])
    files={f['path']:len(read(snapshot,f['path']).splitlines()) for f in snapshot['files'] if f['path'].endswith('.java')}
    entities=[];mapping={};edges=[];diagnostics=[]
    def path(value):
        value=value.replace('\\','/')
        for prefix in (f"/data/snapshots/{snapshot['id']}/",f"/sources/{snapshot['id']}/"):
            if value.startswith(prefix):value=value[len(prefix):]
        if value not in files:return None
        return value
    for file,lines in files.items():
        key='file:'+file;eid=identity('entity',[pid,key]);mapping[key]=eid
        entities.append({'id':eid,'kind':'File','name':PurePosixPath(file).name,'signature':file,'path':file,'start_line':1,'end_line':lines,'local_key':key,'stable_key':identity('logical',[snapshot['repository_id'],file]),'snapshot_id':snapshot['id'],'projection_id':pid})
    for item in raw['entities']:
        file=path(item['path']);a=item.get('start_line',0);b=item.get('end_line',0)
        if not file or not isinstance(a,int) or not isinstance(b,int) or a<1 or b<a or b>files[file]:
            diagnostics.append({'code':'JOERN_SOURCE_RANGE_UNAVAILABLE','kind':item.get('kind'),'key':item.get('key')});continue
        eid=identity('entity',[pid,item['kind'],item['key']]);mapping[item['key']]=eid
        entities.append({**item,'path':file,'id':eid,'local_key':item['key'],'snapshot_id':snapshot['id'],'projection_id':pid,'stable_key':identity('logical',[snapshot['repository_id'],file,item['kind'],item['signature']])})
    def edge(a,b,kind,extra=None):
        value={'source':a,'target':b,'type':kind,'snapshot_id':snapshot['id'],'projection_id':pid,**(extra or {})};edges.append({'id':identity('edge',value),**value})
    for ent in entities:
        if ent['kind']!='File':edge(mapping.get(ent.get('parent')) or mapping['file:'+ent['path']],ent['id'],'DECLARES')
    unresolved=[];external=[];builtins=0
    for call in raw['calls']:
        file=path(call['path'])
        if call.get('name','').startswith('<operator>.'):
            builtins+=1;continue
        targets=[mapping[t] for t in call['targets'] if t in mapping]
        if not targets and call.get('external_targets'):
            external.append({**call,'path':file or call['path'],'resolution_status':'EXTERNAL_DECLARATION'});continue
        if call['from'] not in mapping or not targets:
            unresolved.append({**call,'path':file or call['path'],'resolution_status':'UNKNOWN'});continue
        for target in targets:edge(mapping[call['from']],target,'CALLS',{'path':file,'start_line':call['start_line'],'resolution_status':'RESOLVED','dispatch_semantics':'STATIC_CPG_DECLARATION_ONLY'})
    edges=list({e['id']:e for e in edges}.values())
    return {'id':pid,'snapshot_id':snapshot['id'],'analyzer':'joern-codegraph','analyzer_revision':version,'codegraph':engine,'artifact':raw.get('artifact'),'entities':entities,'edges':edges,'unresolved_calls':unresolved,'external_calls':external,'builtin_call_count':builtins,'diagnostics':diagnostics,'statistics':raw.get('statistics',{}),'limitations':['Joern CPG projection; full CPG retained separately.','Static call targets do not establish runtime dispatch; missing dependencies/dynamic targets remain UNKNOWN.','No automatic remapping of rules from other analyzer revisions.','Control structures are projected; control/data-flow analyses require explicit computation/validation before use; not all CPG relationships are exported as business relations.']}


def analyze(sources,snapshot):
    settings=sources.settings
    try:
        with httpx.Client(timeout=settings.codegraph_timeout,trust_env=False) as client:
            response=client.post(settings.codegraph_url+'/analyze',headers={'Authorization':'Bearer '+settings.codegraph_token},json={'snapshot_id':snapshot['id'],'content_hash':snapshot['content_hash'],'files':snapshot['files']})
        if response.status_code!=200:raise DomainError('CODEGRAPH_ANALYSIS_FAILED',502,{'status':response.status_code})
        data=response.json()
        if data['snapshot_id']!=snapshot['id'] or data['content_hash']!=snapshot['content_hash']:raise DomainError('CODEGRAPH_SOURCE_MISMATCH',409)
        projection=normalize(snapshot,data['result'],data['engine'],sources.read)
    except httpx.TimeoutException:raise DomainError('CODEGRAPH_TIMEOUT',504)
    except httpx.HTTPError:raise DomainError('CODEGRAPH_UNAVAILABLE',503)
    except (ValueError,KeyError,TypeError):raise DomainError('CODEGRAPH_OUTPUT_INVALID',502)
    with sources.store.transaction() as conn:
        return sources.store.get('projection',projection['id'],conn) or sources.store.insert('projection',projection,conn)
