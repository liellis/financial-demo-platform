import secrets
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated, Literal
from fastapi import FastAPI, Depends, File, Form, UploadFile, Request, Query
from fastapi.responses import JSONResponse, FileResponse, RedirectResponse
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.staticfiles import StaticFiles
from fastapi.security import HTTPBearer
from pydantic import BaseModel, Field, ConfigDict, SecretStr
from jsonschema.exceptions import ValidationError as SchemaError
from .auth import AuthService
from .config import ROOT, Settings
from .contracts import DomainError, RULE_SCHEMA, identity, digest
from .service import Studio
from .context import ContextService
from . import ontology
from .subtasks import list_subtasks, subtask_detail, command_subtasks
from .recovery import latest_advice, generate_advice, approve_advice
from .sources import safe_path
from .store import Conflict, Store


class Body(BaseModel):
    model_config = ConfigDict(extra="forbid")


class WorkspacePayload(Body):
    collector: Literal['workspace-bytes-v1']
    repository_id: str = Field(min_length=1,max_length=128)
    release_id: str = Field(min_length=1,max_length=128)
    content_hash: str = Field(pattern='^[0-9a-f]{64}$')
    scope_manifest_hash: str = Field(pattern='^[0-9a-f]{64}$')
    file_count: int = Field(ge=1,le=1000,strict=True)
    observed_at: int = Field(ge=0,strict=True)
    expires_at: int = Field(ge=0,strict=True)
    observation_id: str = Field(pattern='^[0-9a-f]{32}$')


class WorkspaceAttestation(Body):
    payload: WorkspacePayload
    signature: str = Field(min_length=88,max_length=88)


class TargetBaseline(Body):
    repository_id: str = Field(min_length=1,max_length=128)
    source_snapshot_id: str | None = Field(default=None,max_length=128)
    content_hash: str | None = Field(default=None,max_length=128)
    scope_manifest_hash: str | None = Field(default=None,max_length=128)
    attestation: WorkspaceAttestation | None = None


class ContextRequest(Body):
    query: str = Field(min_length=1,max_length=1000)
    release_id: str | None = Field(default=None,max_length=128)
    limit: int = Field(default=10,ge=1,le=30)
    token_budget: int = Field(default=4000,ge=512,le=16000)
    target_baselines: list[TargetBaseline] = Field(default_factory=list,max_length=20)


class AnalysisSetting(Body):
    engine: Literal["joern","javac"]
    expected_revision: int = Field(ge=0)


class EvaluationCase(Body):
    id: str = Field(min_length=1,max_length=80)
    query: str = Field(min_length=1,max_length=1000)
    domain: str = Field(min_length=1,max_length=80)
    complexity: Literal['simple','medium','complex']
    expected_ids: list[Annotated[str,Field(min_length=1,max_length=128)]] = Field(max_length=100)


class EvaluationDataset(Body):
    name: str = Field(min_length=1,max_length=120)
    split: Literal['development','acceptance']
    release_id: str = Field(min_length=1,max_length=128)
    annotation_notes: str = Field(min_length=1,max_length=2000)
    cases: list[EvaluationCase] = Field(min_length=1,max_length=30)


class OntologyBody(Body):
    kind: Literal["BusinessModule","BusinessConcept"]
    name: str = Field(min_length=1,max_length=120)
    definition: str = Field(min_length=1,max_length=4000)
    aliases: list[Annotated[str, Field(min_length=1,max_length=120)]] = Field(default_factory=list,max_length=30)
    projection_id: str = Field(max_length=128)
    rule_ids: list[str] = Field(default_factory=list,max_length=200)
    code_entity_ids: list[str] = Field(default_factory=list,max_length=200)
    reason: str = Field(min_length=1,max_length=2000)
    expected_revision: int | None = Field(default=None,ge=1)


class OntologyDecision(Body):
    decision: Literal["APPROVE","REJECT"]
    expected_revision: int = Field(ge=1)
    reason: str = Field(min_length=1,max_length=2000)


class Login(Body):
    username: str = Field(min_length=1, max_length=100)
    password: SecretStr = Field(min_length=1, max_length=1024)


class PasswordChange(Body):
    current_password: SecretStr = Field(min_length=1, max_length=1024)
    new_password: SecretStr = Field(min_length=8, max_length=1024)


class GitSource(Body):
    name: str = Field(min_length=1, max_length=100)
    url: str = Field(max_length=500)
    ref: str = Field(default="main", min_length=1, max_length=150)


class ModelConfig(Body):
    name: str = Field(min_length=1, max_length=100)
    endpoint: str = Field(max_length=500)
    model_id: str = Field(min_length=1, max_length=100)
    credential_env: str = "BOS_MODEL_API_KEY"
    credential_mode: Literal["environment", "api_key"] = "environment"
    api_key: SecretStr | None = None
    allow_source: bool = False
    max_input_chars: int = Field(default=24000, ge=4000, le=100000)
    max_output_tokens: int = Field(default=4000, ge=100, le=16000)
    temperature: float = Field(default=0.1, ge=0, le=1)


class ModelHost(Body):
    host: str = Field(min_length=1, max_length=253)


class JobConfig(Body):
    type: Literal["ANALYZE", "EXTRACT"]
    snapshot_id: str
    model_id: str | None = None
    projection_id: str | None = None


class Command(Body):
    command: Literal["START", "PAUSE", "RESUME", "CANCEL"]
    expected_revision: int = Field(ge=1)


class RetryJob(Body):
    expected_revision: int = Field(ge=1)
    include_unknown: bool = False


class RecoveryRequest(Body):
    request_id: str = Field(pattern=r"^[a-zA-Z0-9-]{16,64}$")
    language: Literal["zh", "en"] = "zh"


class RecoveryApproval(Body):
    expected_revision: int = Field(ge=1, strict=True)
    group_ids: list[str] = Field(min_length=1, max_length=100)
    include_unknown: bool = False


class SubtaskTarget(Body):
    index: int = Field(ge=1, strict=True)
    expected_revision: int = Field(ge=1, strict=True)


class SubtaskCommand(Body):
    command: Literal["RETRY", "HOLD", "RESUME", "SKIP", "RESTORE"]
    expected_revision: int = Field(ge=1, strict=True)
    include_unknown: bool = False


class SubtaskBulkCommand(Body):
    command: Literal["RETRY", "HOLD", "RESUME", "SKIP", "RESTORE"]
    targets: list[SubtaskTarget] = Field(min_length=1, max_length=100)
    include_unknown: bool = False


class ExtractionDefaults(Body):
    max_source_lines: int = Field(default=100, ge=1, le=2000, strict=True)
    expected_revision: int = Field(ge=0)
    model_timeout_seconds: int = Field(default=120, ge=10, le=300, strict=True)
    transient_retries: int = Field(default=2, ge=0, le=5, strict=True)
    retry_backoff_seconds: int = Field(default=5, ge=1, le=60, strict=True)


class Decision(Body):
    decision: Literal["APPROVE", "REJECT", "EDIT"]
    reason: str = Field(min_length=1, max_length=2000)
    expected_revision: int = Field(ge=1)
    changes: dict | None = None


class Publish(Body):
    candidate_ids: list[str] = Field(min_length=1, max_length=200)
    expected_revision: int = Field(ge=0)
    reason: str = Field(min_length=1, max_length=2000)


class Pointer(Body):
    release_id: str
    expected_revision: int = Field(ge=1)
    reason: str = Field(min_length=1, max_length=2000)


def create_app(settings=None):
    settings = settings or Settings()
    store = Store(settings.database_url)
    studio = Studio(settings, store)
    auth = AuthService(store)
    app = FastAPI(title="Bank Engineering Ontology Studio", version="0.1.0", description="M1 development API. Single project; no production/Action or MCP claims.")
    app.state.studio = studio
    app.state.auth = auth
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=["127.0.0.1", "localhost", "testserver"])
    app.add_middleware(CORSMiddleware, allow_origins=["http://127.0.0.1:5173", "http://localhost:5173"], allow_methods=["GET", "POST", "PUT", "PATCH"], allow_headers=["Authorization", "Content-Type"])

    @app.middleware("http")
    async def trace(request: Request, call_next):
        request.state.trace_id = uuid.uuid4().hex
        response = await call_next(request)
        response.headers["X-Trace-ID"] = request.state.trace_id
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Cache-Control"] = "no-store"
        return response

    def error(request, code, status, params=None):
        return JSONResponse({"code": code, "message_key": "errors." + code, "params": params or {}, "trace_id": request.state.trace_id}, status_code=status)

    @app.exception_handler(DomainError)
    async def domain_error(request, exc):
        return error(request, exc.code, exc.status, exc.params)

    @app.exception_handler(Conflict)
    async def conflict(request, exc):
        return error(request, "REVISION_CONFLICT", 409)

    @app.exception_handler(SchemaError)
    async def schema_error(request, exc):
        return error(request, "SCHEMA_VALIDATION_FAILED", 422, {"path": list(exc.absolute_path)})

    from fastapi.exceptions import RequestValidationError
    @app.exception_handler(RequestValidationError)
    async def request_error(request, exc):
        # Do not echo submitted input: it may include sensitive source/endpoint text.
        return error(request, "VALIDATION_FAILED", 422, {"fields": [list(e["loc"]) for e in exc.errors()]})

    bearer = HTTPBearer(auto_error=False)

    def require(*roles):
        def authenticate(credentials=Depends(bearer)):
            value = credentials.credentials if credentials and credentials.scheme.lower() == "bearer" else ""
            role = next((r for r, token in settings.tokens.items() if value and secrets.compare_digest(value.encode(), token.encode())), None)
            if not role:
                account = auth.account(value)
                role = account["role"] if account else None
            if not role:
                raise DomainError("UNAUTHENTICATED", 401)
            if role not in roles:
                raise DomainError("ACCESS_DENIED", 403)
            return role
        return authenticate

    reader = require("admin", "developer", "reviewer")
    builder = require("admin", "developer")
    admin = require("admin")
    reviewer = require("admin", "reviewer")
    published_reader = require("admin", "developer", "reviewer", "agent")

    @app.post("/api/v2/auth/login")
    def login(data: Login, request: Request):
        return auth.login(data.username, data.password.get_secret_value(), request.client.host if request.client else "local")

    @app.post("/api/v2/auth/logout")
    def logout(actor=Depends(published_reader), credentials=Depends(bearer)):
        auth.logout(credentials.credentials)
        return {"logged_out": True}

    @app.post("/api/v2/auth/password")
    def change_password(data: PasswordChange, actor=Depends(published_reader), credentials=Depends(bearer)):
        return auth.change_password(credentials.credentials, data.current_password.get_secret_value(), data.new_password.get_secret_value())

    context = ContextService(studio)

    @app.get('/api/v2/evaluations/datasets')
    def evaluation_datasets(actor=Depends(reader)):
        return store.all('evaluation_dataset')

    @app.post('/api/v2/evaluations/datasets')
    def create_evaluation_dataset(data: EvaluationDataset,actor=Depends(admin)):
        from .evaluation import create_dataset
        return create_dataset(studio,data.model_dump(),actor)

    @app.post('/api/v2/evaluations/datasets/{dataset_id}/runs')
    def run_evaluation(dataset_id: str,actor=Depends(admin)):
        from .evaluation import run
        return run(studio,dataset_id,actor)

    @app.get('/api/v2/evaluations/runs')
    def evaluation_runs(actor=Depends(reader)):
        return [{k:v for k,v in row.items() if k!='cases'} for row in store.all('evaluation_run')]

    @app.get('/api/v2/evaluations/runs/{run_id}')
    def evaluation_run(run_id: str,actor=Depends(reader)):
        return studio.get('evaluation_run',run_id)

    @app.get('/api/v2/settings/workspace')
    def workspace_settings(actor=Depends(reader)):
        from .workspace import status
        return status(settings)

    @app.get('/api/v2/context/baseline-manifest')
    def baseline_manifest(release_id: str | None = None,actor=Depends(published_reader)):
        release=context.release(release_id)
        return {'release_id':release['id'],'sources':[{k:s[k] for k in ('repository_id','content_hash','scope_manifest_hash')} for s in release['source_snapshots']]}

    @app.get("/api/v2/context/search")
    def context_search(q: str = Query(min_length=1,max_length=1000), release_id: str | None = None, limit: int = Query(default=10,ge=1,le=30), actor=Depends(published_reader)):
        return context.search(q, release_id, limit)

    @app.get("/api/v2/context/trace/{rule_id}")
    def context_trace(rule_id: str, release_id: str | None = None, actor=Depends(published_reader)):
        return context.trace(rule_id, release_id)

    @app.get("/api/v2/context/evidence/{evidence_id}")
    def context_evidence(evidence_id: str, release_id: str | None = None, actor=Depends(published_reader)):
        return context.evidence(evidence_id, release_id)

    @app.post("/api/v2/context/assemble")
    def context_assemble(data: ContextRequest, actor=Depends(published_reader)):
        return context.assemble(data.query,data.release_id,data.limit,data.token_budget,[t.model_dump(exclude_none=True) for t in data.target_baselines])

    @app.get("/api/v2/settings/extraction")
    def extraction_settings(actor=Depends(reader)):
        return studio.extraction_defaults()

    @app.put("/api/v2/settings/extraction")
    def update_extraction_settings(data: ExtractionDefaults, actor=Depends(admin)):
        return studio.update_extraction_defaults(data.model_dump(), actor)

    @app.get("/api/v2/health")
    def health():
        return {"status": "ok", "stage": "M2-increment-3", "storage": "postgresql" if settings.database_url.startswith("postgresql") else "sqlite-local", "graph_projection": "neo4j" if settings.neo4j_uri else "local-manifest", "mcp": "STDIO_ADAPTER", "codegraph": "joern" if settings.codegraph_url else "javac-ast", "codegraph_configured": bool(settings.codegraph_url)}

    @app.get("/api/v2/readiness")
    def readiness():
        try:
            from sqlalchemy import text
            with store.engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            graph = studio.graph.health()
            return {"status": "ready", "storage": store.engine.dialect.name, "graph": graph["backend"]}
        except Exception:
            raise DomainError("DEPENDENCIES_UNAVAILABLE", 503)

    @app.get("/api/v2/me")
    def me(actor=Depends(published_reader), credentials=Depends(bearer)):
        account = auth.account(credentials.credentials)
        return {"username": account["id"] if account else None, "auth_method": "password" if account else "api_token", "role": actor, "project_id": "local", "scope": "single-project-development", "git_hosts": settings.git_hosts, "model_hosts": studio.models.allowed_hosts(), "model_host_policy": "environment" if settings.model_hosts else "administrator"}

    @app.get("/api/v2/ontology/schema")
    def schema(actor=Depends(reader)):
        return {"version": "m1-ontology-1", "extraction": RULE_SCHEMA, "code_entity_kinds": ["File", "Class", "Interface", "Method"], "business_entity_kinds": ["BusinessRule"], "relations": {"DECLARES": "code container -> code entity", "CALLS": "method -> statically resolved method declaration", "IMPLEMENTED_BY": "reviewed BusinessRule -> exact code entity version"}, "limitations": ["BusinessModule/BusinessObject editors and generic schema editor are not implemented in this increment."]}

    @app.get("/api/v2/source-snapshots")
    def snapshots(actor=Depends(reader)):
        return store.all("snapshot")

    @app.post("/api/v2/sources/upload")
    async def upload(file: UploadFile = File(...), name: str = Form(..., min_length=1, max_length=100), actor=Depends(builder)):
        data = await file.read(settings.max_upload + 1)
        result = studio.sources.upload_zip(data, name)
        with store.transaction() as conn:
            studio.audit(actor, "SOURCE_UPLOAD", result["id"], conn)
        return result

    @app.post("/api/v2/sources/directory")
    async def directory(files: list[UploadFile] = File(...), name: str = Form(..., min_length=1, max_length=100), actor=Depends(builder)):
        if len(files) > settings.max_files:
            raise DomainError("TOO_MANY_FILES", 413)
        result, total = {}, 0
        for file in files:
            path = safe_path(file.filename or "")
            if path is None:
                continue
            value = await file.read(settings.max_file + 1)
            total += len(value)
            if len(value) > settings.max_file or total > settings.max_upload:
                raise DomainError("UPLOAD_TOO_LARGE", 413)
            if path in result:
                raise DomainError("DUPLICATE_UPLOAD_PATH")
            result[path] = value
        value = studio.sources.persist(result, name, "UPLOAD", None, None)
        with store.transaction() as conn:
            studio.audit(actor, "SOURCE_DIRECTORY_UPLOAD", value["id"], conn)
        return value

    @app.post("/api/v2/sources/git")
    def git_source(data: GitSource, actor=Depends(builder)):
        result = studio.sources.clone(data.url, data.ref, data.name)
        with store.transaction() as conn:
            studio.audit(actor, "SOURCE_GIT", result["id"], conn)
        return result

    @app.get("/api/v2/source-snapshots/{key}/files")
    def file_content(key: str, path: str, actor=Depends(reader)):
        snapshot = studio.get("snapshot", key)
        return {"path": path, "content": studio.sources.read(snapshot, path), "snapshot_id": key, "content_hash": snapshot["content_hash"]}

    @app.get("/api/v2/settings/analysis")
    def analysis_settings(actor=Depends(reader)):
        return store.get("settings","analysis") or {"id":"analysis","engine":"joern" if settings.codegraph_url else "javac","revision":0}

    @app.put("/api/v2/settings/analysis")
    def update_analysis_settings(data: AnalysisSetting, actor=Depends(admin)):
        if data.engine == "joern" and not settings.codegraph_url:
            raise DomainError("CODEGRAPH_NOT_CONFIGURED",409)
        with store.transaction() as conn:
            current = store.get("settings","analysis",conn)
            if (current["revision"] if current else 0) != data.expected_revision:
                raise DomainError("REVISION_CONFLICT",409)
            value = {"id":"analysis","engine":data.engine}
            result = store.update("settings",value,current["revision"],conn) if current else store.insert("settings",value,conn)
            studio.audit(actor,"ANALYSIS_ENGINE_UPDATE","analysis",conn,{"engine":data.engine})
            return result

    @app.get("/api/v2/codegraph/status")
    def codegraph_status(actor=Depends(reader)):
        if not settings.codegraph_url:
            return {"configured": False, "engine": "javac-ast", "ready": True}
        try:
            import httpx
            with httpx.Client(timeout=5,trust_env=False) as client:
                response = client.get(settings.codegraph_url + "/health")
            response.raise_for_status()
            return {"configured": True, "ready": True, **response.json()}
        except Exception:
            return {"configured": True, "ready": False, "error": "CODEGRAPH_UNAVAILABLE"}

    @app.get("/api/v2/ontology/entities")
    def ontology_entities(actor=Depends(reader)):
        return store.all("ontology")

    @app.post("/api/v2/ontology/entities")
    def create_ontology(data: OntologyBody, actor=Depends(builder)):
        return ontology.save(studio,data.model_dump(),actor)

    @app.put("/api/v2/ontology/entities/{key}")
    def edit_ontology(key: str, data: OntologyBody, actor=Depends(builder)):
        return ontology.save(studio,data.model_dump(),actor,key)

    @app.post("/api/v2/ontology/entities/{key}/decision")
    def decide_ontology(key: str, data: OntologyDecision, actor=Depends(reviewer)):
        return ontology.decide(studio,key,data.model_dump(),actor)

    @app.get("/api/v2/code/entities")
    def code_entities(projection_id: str, q: str = Query(default="",max_length=200), offset: int = Query(default=0,ge=0), limit: int = Query(default=100,ge=1,le=200), actor=Depends(reader)):
        projection = studio.get("projection",projection_id)
        needle = q.casefold()
        items = [n for n in projection["entities"] if n["kind"] != "File" and needle in (n.get("name","")+" "+n.get("signature","")+" "+n.get("path","")).casefold()]
        return {"items":items[offset:offset+limit],"total":len(items),"offset":offset,"limit":limit}

    @app.get("/api/v2/code/projections")
    def projections(actor=Depends(reader)):
        return [{k: v for k, v in p.items() if k not in ("entities", "edges", "unresolved_calls", "external_calls")} | {"entity_count": len(p["entities"]), "edge_count": len(p["edges"]), "unresolved_count": len(p["unresolved_calls"]), "external_count": len(p.get("external_calls",[]))} for p in store.all("projection")]

    @app.get("/api/v2/code/subgraph")
    def code_graph(projection_id: str, limit: int = 150, include_candidates: bool = False, job_id: str | None = None, root: str | None = None, actor=Depends(reader)):
        projection = studio.get("projection", projection_id)
        limit = max(1, min(limit, 300))
        if include_candidates:
            candidates = [c for c in store.all("candidate") if c["projection_id"] == projection_id and c["status"] != "REJECTED"]
            if not job_id and candidates:
                jobs = [j for j in store.all("job") if j.get("projection_id") == projection_id and j["status"] != "CANCELLED" and any(c["job_id"] == j["id"] for c in candidates)]
                job_id = max(jobs, key=lambda j: j["created_at"])["id"] if jobs else None
            if job_id:
                job = studio.get("job", job_id)
                if job.get("projection_id") != projection_id:
                    raise DomainError("GRAPH_JOB_PROJECTION_MISMATCH", 422)
                candidates = [c for c in candidates if c["job_id"] == job_id]
            entities = {n["id"]: n for n in projection["entities"]}
            nodes_by_id, mappings = {}, []
            for candidate in candidates:
                target = entities.get(candidate["entity_version_id"])
                if not target:
                    continue
                nodes_by_id[target["id"]] = target
                nodes_by_id[candidate["id"]] = {**candidate, "kind": "BusinessRule", "preview": True}
                mappings.append({"id": identity("preview_mapping", candidate["id"]), "source": candidate["id"], "target": target["id"], "type": "IMPLEMENTED_BY", "status": candidate["status"], "preview": True})
            for entity in projection["entities"]:
                nodes_by_id.setdefault(entity["id"], entity)
            ontology_nodes, ontology_edges, _ = ontology.graph_nodes(studio,[projection_id],[c['id'] for c in candidates],list(entities))
            nodes_by_id.update({n['id']:n for n in ontology_nodes})
            graph_edges = mappings + projection["edges"] + ontology_edges
            core = {"nodes": list(nodes_by_id.values()), "edges": graph_edges}
            graph_digest = digest(core)
            result = studio.graph.read({**core, "id": "preview_" + graph_digest, "digest": graph_digest}, limit, root)
            return {**result, "preview": True, "job_id": job_id, "candidate_total": len(candidates), "candidate_shown": sum(n["kind"] == "BusinessRule" for n in result["nodes"]), "snapshot_id": projection["snapshot_id"], "projection_id": projection_id, "limit": limit, "limitations": ["Unreviewed candidate preview; not published knowledge.", "No inter-rule business dependencies inferred from layout."], "unresolved_calls": projection["unresolved_calls"], "diagnostics": projection["diagnostics"]}
        core = {"nodes": projection["entities"], "edges": projection["edges"]}
        graph_digest = digest(core)
        result = studio.graph.read({**core, "id": "code_" + graph_digest, "digest": graph_digest}, limit, root)
        return {**result, "snapshot_id": projection["snapshot_id"], "projection_id": projection_id, "unresolved_calls": projection["unresolved_calls"], "diagnostics": projection["diagnostics"], "limitations": projection["limitations"]}

    @app.get("/api/v2/model-connections")
    def models(actor=Depends(reader)):
        result = []
        for config in store.all("model"):
            visible = config if actor == "admin" else {k: config[k] for k in ("id", "name", "model_id", "allow_source", "enabled", "revision")}
            validation, eligibility_error = studio.models.eligibility(config)
            result.append({**visible, "eligibility_error": eligibility_error, **({"validation": validation} if actor == "admin" else {})})
        return result

    @app.post("/api/v2/model-connections")
    def register_model(data: ModelConfig, actor=Depends(admin)):
        result = studio.models.register(data.model_dump())
        with store.transaction() as conn:
            studio.audit(actor, "MODEL_REGISTER", result["id"], conn)
        return result

    @app.post("/api/v2/model-endpoint-hosts")
    def authorize_model_host(data: ModelHost, actor=Depends(admin)):
        return studio.models.authorize_host(data.host, actor)

    @app.post("/api/v2/model-connections/{key}/validations")
    def validate_model(key: str, actor=Depends(admin)):
        result = studio.models.validate(studio.get("model", key))
        with store.transaction() as conn:
            studio.audit(actor, "MODEL_VALIDATE", key, conn, {"validation_id": result["id"], "eligible": result["eligible"]})
        return result

    @app.post("/api/v2/model-connections/{key}/disable")
    def disable_model(key: str, actor=Depends(admin)):
        with store.transaction() as conn:
            config = studio.get("model", key, conn)
            config["enabled"] = False
            result = store.update("model", config, config["revision"], conn)
            studio.audit(actor, "MODEL_DISABLE", key, conn)
            return result

    @app.get("/api/v2/knowledge-jobs")
    def jobs(actor=Depends(reader)):
        return store.all("job")

    @app.post("/api/v2/knowledge-jobs")
    def create_job(data: JobConfig, actor=Depends(builder)):
        if data.type == "EXTRACT" and not (data.model_id and data.projection_id):
            raise DomainError("EXTRACTION_CONFIG_REQUIRED", 422)
        return studio.job(data.model_dump(), actor)

    @app.post("/api/v2/knowledge-jobs/{key}/commands")
    def job_command(key: str, data: Command, actor=Depends(builder)):
        return studio.command(key, data.command, data.expected_revision, actor)

    @app.post("/api/v2/knowledge-jobs/{key}/retry")
    def retry_job(key: str, data: RetryJob, actor=Depends(builder)):
        return studio.retry_job(key, data.expected_revision, actor, data.include_unknown)

    @app.get("/api/v2/ontology/candidates")
    def candidates(actor=Depends(reader)):
        return store.all("candidate")

    @app.get("/api/v2/knowledge-jobs/{key}/recovery-advice")
    def read_recovery_advice(key: str, actor=Depends(reader)):
        return latest_advice(studio, key)

    @app.post("/api/v2/knowledge-jobs/{key}/recovery-advice")
    def request_recovery_advice(key: str, data: RecoveryRequest, actor=Depends(builder)):
        return generate_advice(studio, key, data.request_id, data.language, actor)

    @app.post("/api/v2/knowledge-jobs/{key}/recovery-advice/{advice_id}/approve")
    def approve_recovery_advice(key: str, advice_id: str, data: RecoveryApproval, actor=Depends(builder)):
        return approve_advice(studio, key, advice_id, data.expected_revision, data.group_ids, data.include_unknown, actor)

    @app.get("/api/v2/extraction-subtasks")
    def extraction_subtasks(job_id: str | None = None, status: str | None = None,
                            q: str = Query(default="", max_length=200), offset: int = Query(default=0, ge=0),
                            limit: int = Query(default=25, ge=1, le=100), actor=Depends(reader)):
        return list_subtasks(studio, job_id, status, q, offset, limit)

    @app.get("/api/v2/knowledge-jobs/{key}/subtasks/{index}")
    def get_subtask(key: str, index: int, actor=Depends(reader)):
        return subtask_detail(studio, key, index)

    @app.post("/api/v2/knowledge-jobs/{key}/subtasks/{index}/commands")
    def subtask_command(key: str, index: int, data: SubtaskCommand, actor=Depends(builder)):
        return command_subtasks(studio, key, data.command, [{"index": index, "expected_revision": data.expected_revision}], actor, data.include_unknown)

    @app.post("/api/v2/knowledge-jobs/{key}/subtasks/commands")
    def subtask_bulk_command(key: str, data: SubtaskBulkCommand, actor=Depends(builder)):
        return command_subtasks(studio, key, data.command, [t.model_dump() for t in data.targets], actor, data.include_unknown)

    @app.post("/api/v2/reviews/{key}/decision")
    def review(key: str, data: Decision, actor=Depends(reviewer)):
        return studio.review(key, data.model_dump(), actor)

    @app.get("/api/v2/releases")
    def releases(actor=Depends(published_reader)):
        return [{k: v for k, v in r.items() if k not in ("nodes", "edges", "rules", "source_snapshots")} | {"rule_count": len(r["rules"]), "snapshot_ids": [s["id"] for s in r["source_snapshots"]]} for r in store.all("release")]

    @app.post("/api/v2/releases")
    def publish(data: Publish, actor=Depends(admin)):
        if len(set(data.candidate_ids)) != len(data.candidate_ids):
            raise DomainError("DUPLICATE_CANDIDATES")
        return studio.publish(data.model_dump(), actor)

    @app.get("/api/v2/releases/{key}/graph")
    def release_graph(key: str, limit: int = Query(default=80,ge=1,le=300), actor=Depends(published_reader)):
        release = context.release(key)
        return {**studio.graph.read(release,limit), "id": key, "release_id": key, "preview": False}

    @app.get("/api/v2/releases/{key}")
    def release(key: str, actor=Depends(published_reader)):
        return studio.get("release", key)

    @app.get("/api/v2/release-pointers/current")
    def current(actor=Depends(published_reader)):
        return store.get("pointer", "current") or {"id": "current", "release_id": None, "revision": 0}

    @app.put("/api/v2/release-pointers/current")
    def switch(data: Pointer, actor=Depends(admin)):
        studio.get("release", data.release_id)
        with store.transaction() as conn:
            result = store.update("pointer", {"id": "current", "release_id": data.release_id, "reason": data.reason, "actor": actor}, data.expected_revision, conn)
            studio.audit(actor, "POINTER_SWITCH", data.release_id, conn, {"reason": data.reason})
            return result

    @app.get("/api/v2/audit-events")
    def audits(actor=Depends(reviewer)):
        return sorted(store.all("audit"), key=lambda a: a["at"], reverse=True)[:200]

    dist = ROOT / "frontend/dist"
    if (dist / "assets").exists():
        app.mount("/assets", StaticFiles(directory=dist / "assets"), name="assets")

    demo_dist = ROOT / "demo/dist"
    if (demo_dist / "index.html").exists():
        @app.get("/demo", include_in_schema=False)
        def demo_redirect():
            return RedirectResponse(url="/demo/")

        app.mount("/demo", StaticFiles(directory=demo_dist, html=True), name="demo")

    @app.get("/", include_in_schema=False)
    def index():
        if (dist / "index.html").exists():
            return FileResponse(dist / "index.html")
        return JSONResponse({"message": "请先构建 frontend，或运行 Vite 开发服务。", "api_docs": "/docs"})

    return app


def main():
    import uvicorn
    uvicorn.run(create_app(), host="127.0.0.1", port=8000)


if __name__ == "__main__":
    main()
