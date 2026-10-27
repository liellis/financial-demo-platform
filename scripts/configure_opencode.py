"""Write a project-local OpenCode profile; never change global OpenCode settings."""
import json
import os
from pathlib import Path
import sys
import subprocess

ROOT=Path(__file__).resolve().parents[1]

def main():
    runtime=ROOT/'.data/m2/runtime'
    tokens=json.loads((runtime/'access-tokens.json').read_text())
    token_file=ROOT/'.data/m2/opencode-agent.token'
    fd=os.open(token_file,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as f:f.write(tokens['agent'])
    entry={'type':'local','command':[sys.executable,'-m','backend.mcp_server'],'enabled':True,'environment':{'PYTHONPATH':str(ROOT),'BOS_API_URL':'http://127.0.0.1:8002','BOS_MCP_TOKEN_FILE':str(token_file),'BOS_WORKSPACE_CONFIG_FILE':str(ROOT/'.data/m2/workspaces.json')}}
    environment={**os.environ,**{k:str(ROOT/'.data/m2'/v) for k,v in [('XDG_DATA_HOME','opencode-data'),('XDG_STATE_HOME','opencode-state'),('XDG_CONFIG_HOME','opencode-config')]}}
    installed=subprocess.check_output(['opencode','--version'],env=environment,text=True,timeout=10).strip()
    modern='v2.' in installed
    if modern:
        entry.pop('enabled',None)
        entry['codemode']=False
    config={'$schema':'https://opencode.ai/config.json','mcp':{'servers':{'bank-ontology':entry}} if modern else {'bank-ontology':entry}}
    path=ROOT/'.data/m2/opencode.json';path.write_text(json.dumps(config,ensure_ascii=False,indent=2));path.chmod(0o600)
    project=ROOT/'opencode.json'
    existing=json.loads(project.read_text()) if project.exists() else {'$schema':config['$schema']}
    mcp=existing.setdefault('mcp',{})
    if modern:
        mcp.pop('bank-ontology',None)
        mcp.setdefault('servers',{})['bank-ontology']=entry
    else:mcp['bank-ontology']=entry
    project.write_text(json.dumps(existing,ensure_ascii=False,indent=2));project.chmod(0o600)
    print('Created private project OpenCode profiles using installed-client format; own duplicate legacy entry removed. Global settings untouched.')

if __name__=='__main__':main()
