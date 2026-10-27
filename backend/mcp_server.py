"""OpenCode stdio bridge. Only published read APIs; credentials are host-side files."""
import json
import os
from pathlib import Path
from urllib.parse import urlparse
import httpx
from mcp.server.fastmcp import FastMCP
from mcp.types import ToolAnnotations

mcp = FastMCP('bank-ontology-context')


def call(path, method='GET', body=None, params=None):
    base=os.getenv('BOS_API_URL','http://127.0.0.1:8002').rstrip('/')
    parsed=urlparse(base)
    if parsed.scheme not in ('http','https') or parsed.username or parsed.password or (parsed.scheme=='http' and parsed.hostname not in ('127.0.0.1','localhost')):
        raise ValueError('A local loopback URL or authenticated HTTPS endpoint is required')
    token_file=Path(os.environ['BOS_MCP_TOKEN_FILE']).expanduser()
    token=token_file.read_text().strip()
    if not token or len(token)>1024:raise ValueError('Invalid Agent credential file')
    try:
        with httpx.Client(timeout=45,trust_env=False,follow_redirects=False) as client:
            result=client.request(method,base+'/api/v2'+path,params=params,json=body,headers={'Authorization':'Bearer '+token})
        data=result.json()
        if result.status_code>=400:
            return {'error':data.get('code','CONTEXT_REQUEST_FAILED'),'trace_id':data.get('trace_id'),'knowledge_scope':'published-only'}
        return data
    except (httpx.HTTPError,ValueError):
        return {'error':'CONTEXT_SERVICE_UNAVAILABLE','knowledge_scope':'published-only'}


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, openWorldHint=False))
def search_business_concepts(query:str, release_id:str|None=None, limit:int=10)->dict:
    """Search approved, published business rules. No published release returns an explicit error."""
    return call('/context/search',params={'q':query,'limit':limit,**({'release_id':release_id} if release_id else {})})


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, openWorldHint=False))
def trace_business_to_code(rule_id:str, release_id:str|None=None)->dict:
    """Trace a published rule to exact code entity versions and graph neighbors."""
    from urllib.parse import quote
    return call('/context/trace/'+quote(rule_id,safe=''),params={'release_id':release_id} if release_id else {})


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, openWorldHint=False))
def assemble_business_context(query:str, release_id:str|None=None, token_budget:int=4000, limit:int=10, target_baselines:list[dict]|None=None)->dict:
    """Assemble release-pinned rules, code, evidence and explicit baseline status within a token budget."""
    targets=target_baselines or []
    config_file=os.getenv('BOS_WORKSPACE_CONFIG_FILE')
    if not targets and config_file and Path(config_file).exists():
        from .workspace import collect
        from .contracts import DomainError
        manifest=call('/context/baseline-manifest',params={'release_id':release_id} if release_id else {})
        if 'error' in manifest:return manifest
        release_id=manifest['release_id']
        try:
            configured=json.loads(Path(config_file).read_text()).get('repositories',{})
            targets=[collect(s['repository_id'],release_id,config_file) for s in manifest['sources'] if s['repository_id'] in configured]
        except Exception as exc:
            return {'error':exc.code if isinstance(exc,DomainError) else 'WORKSPACE_COLLECTION_FAILED','knowledge_scope':'published-only'}
    return call('/context/assemble','POST',{'query':query,'release_id':release_id,'token_budget':token_budget,'limit':limit,'target_baselines':targets})


@mcp.tool(annotations=ToolAnnotations(readOnlyHint=True, destructiveHint=False, openWorldHint=False))
def get_evidence(evidence_id:str, release_id:str|None=None)->dict:
    """Read only an evidence range referenced by a published rule. Arbitrary files are forbidden."""
    from urllib.parse import quote
    return call('/context/evidence/'+quote(evidence_id,safe=''),params={'release_id':release_id} if release_id else {})


if __name__=='__main__':mcp.run(transport='stdio')
