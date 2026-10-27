import copy
import io
import json
import stat
import threading
import zipfile
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import pytest
from fastapi.testclient import TestClient
from backend.api import create_app
from backend.config import Settings
from backend.contracts import DomainError
from backend.store import Store

SOURCE = """package credit;
class Limit {
  boolean allowed(int amount) {
    return amount <= 100;
  }
  boolean verify(int amount) { return allowed(amount); }
}
"""


def archive(files=None):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        for path, content in (files or {"src/credit/Limit.java": SOURCE}).items():
            z.writestr(path, content)
    return out.getvalue()


@pytest.fixture
def env(tmp_path, monkeypatch):
    monkeypatch.setenv("BOS_MODEL_API_KEY", "test-only-key")
    settings = Settings(data_dir=tmp_path, tokens={r: r + "-test-token" for r in ("admin", "developer", "reviewer", "agent")}, model_hosts=("127.0.0.1",))
    app = create_app(settings)
    with TestClient(app) as client:
        yield client, app.state.studio, settings


def headers(role="admin"):
    return {"Authorization": "Bearer " + role + "-test-token"}


def upload(client, files=None, name="test-repository"):
    result = client.post("/api/v2/sources/upload", headers=headers(), files={"file": ("source.zip", archive(files), "application/zip")}, data={"name": name})
    assert result.status_code == 200, result.text
    return result.json()


def parse(studio, snapshot):
    return studio.sources.analyze(snapshot)


def rule_for(projection):
    ent = next(e for e in projection["entities"] if e["kind"] == "Method" and e["name"] == "allowed")
    return {"name": "额度上限校验", "condition": "amount <= 100", "outcome": "允许", "scope": "额度校验", "exceptions": [], "uncertainties": [], "entity_version_id": ent["id"], "evidence": [{"path": "src/credit/Limit.java", "start_line": 4, "end_line": 4, "quote": "return amount <= 100;"}]}


def seed_candidate(studio, snapshot, projection, key="candidate-test"):
    with studio.store.transaction() as c:
        return studio.store.insert("candidate", {**rule_for(projection), "id": key, "status": "CANDIDATE", "snapshot_id": snapshot["id"], "projection_id": projection["id"], "content_revision": 1, "origin_kind": "TEST_FIXTURE"}, c)


def approve(client, item):
    result = client.post(f"/api/v2/reviews/{item['id']}/decision", headers=headers("reviewer"), json={"decision": "APPROVE", "reason": "人工核对测试证据", "expected_revision": item["revision"]})
    assert result.status_code == 200, result.text
    return result.json()


def publish(client, item, rev=0):
    return client.post("/api/v2/releases", headers=headers(), json={"candidate_ids": [item["id"]], "expected_revision": rev, "reason": "发布已核对的测试规则"})


def test_auth_roles_and_no_automatic_business_data(env):
    client, studio, settings = env
    assert client.get("/api/v2/source-snapshots").status_code == 401
    assert client.get("/api/v2/source-snapshots", headers=headers("agent")).status_code == 403
    assert client.get("/api/v2/ontology/candidates", headers=headers("agent")).status_code == 403
    assert client.get("/api/v2/releases", headers=headers("agent")).json() == []
    assert studio.store.all("snapshot") == []
    assert client.get("/api/v2/me", headers=headers("developer")).json()["role"] == "developer"
    result = client.post("/api/v2/releases", headers=headers("developer"), json={"candidate_ids": ["x"], "expected_revision": 0, "reason": "attempt"})
    assert result.status_code == 403


def test_upload_traversal_symlink_and_idempotency(env):
    client, studio, _ = env
    bad = client.post("/api/v2/sources/upload", headers=headers(), files={"file": ("bad.zip", archive({"../escape.java": SOURCE}))}, data={"name": "bad"})
    assert bad.status_code == 400 and bad.json()["code"] == "INVALID_UPLOAD_PATH"
    symlink = io.BytesIO()
    with zipfile.ZipFile(symlink, "w") as z:
        info = zipfile.ZipInfo("Bad.java")
        info.external_attr = (stat.S_IFLNK | 0o777) << 16
        z.writestr(info, "/etc/passwd")
    result = client.post("/api/v2/sources/upload", headers=headers(), files={"file": ("bad.zip", symlink.getvalue())}, data={"name": "bad"})
    assert result.json()["code"] == "UPLOAD_SYMLINK_FORBIDDEN"
    first = upload(client)
    second = upload(client)
    assert first["id"] == second["id"]
    assert len(studio.store.all("snapshot")) == 1
    denied = client.get(f"/api/v2/source-snapshots/{first['id']}/files", headers=headers(), params={"path": "../../etc/passwd"})
    assert denied.status_code == 422


def test_same_commit_different_content_and_real_ast(env):
    _, studio, _ = env
    s0 = studio.sources.persist({"src/credit/Limit.java": SOURCE.encode()}, "repo", "WORKSPACE", "same-sha", "repo")
    s1 = studio.sources.persist({"src/credit/Limit.java": SOURCE.replace("100", "200").encode()}, "repo", "WORKSPACE", "same-sha", "repo")
    p0, p1 = parse(studio, s0), parse(studio, s1)
    m0 = next(e for e in p0["entities"] if e["name"] == "allowed")
    m1 = next(e for e in p1["entities"] if e["name"] == "allowed")
    assert s0["id"] != s1["id"] and m0["id"] != m1["id"]
    assert m0["stable_key"] == m1["stable_key"]
    assert m0["start_line"] == 3 and m0["end_line"] == 5
    assert any(e["type"] == "CALLS" and e["target"] == m0["id"] for e in p0["edges"])
    assert all(e["name"] != "<init>" for e in p0["entities"])
    assert parse(studio, s0)["id"] == p0["id"]
    assert "100" in studio.sources.read(s0, m0["path"])
    assert "200" in studio.sources.read(s1, m1["path"])


def test_review_publish_gate_optimistic_lock_and_frozen_history(env):
    client, studio, _ = env
    snap = upload(client)
    projection = parse(studio, snap)
    candidate = seed_candidate(studio, snap, projection)
    assert publish(client, candidate).json()["code"] == "UNAPPROVED_KNOWLEDGE"
    candidate = approve(client, candidate)
    stale = client.post(f"/api/v2/reviews/{candidate['id']}/decision", headers=headers("reviewer"), json={"decision": "REJECT", "reason": "stale", "expected_revision": 1})
    assert stale.json()["code"] == "REVISION_CONFLICT"
    result = publish(client, candidate)
    assert result.status_code == 200, result.text
    frozen = copy.deepcopy(result.json())
    release_id = frozen["id"]
    candidate = studio.get("candidate", candidate["id"])
    edit = client.post(f"/api/v2/reviews/{candidate['id']}/decision", headers=headers("reviewer"), json={"decision": "EDIT", "reason": "cannot mutate", "expected_revision": candidate["revision"], "changes": {"condition": "amount > 100"}})
    assert edit.json()["code"] == "PUBLISHED_IMMUTABLE"
    assert client.get(f"/api/v2/releases/{release_id}", headers=headers("agent")).json() == frozen
    assert len(frozen["edges"]) and any(e["type"] == "IMPLEMENTED_BY" for e in frozen["edges"])
    assert publish(client, candidate, rev=1).json()["id"] == release_id


def test_edit_clears_approval_and_false_evidence_is_rejected(env):
    client, studio, _ = env
    snap = upload(client)
    p = parse(studio, snap)
    candidate = approve(client, seed_candidate(studio, snap, p))
    data = {"decision": "EDIT", "reason": "修订适用范围", "expected_revision": candidate["revision"], "changes": {"scope": "新的适用范围"}}
    edited = client.post(f"/api/v2/reviews/{candidate['id']}/decision", headers=headers("reviewer"), json=data).json()
    assert edited["status"] == "CANDIDATE" and "approval_id" not in edited
    assert edited["content_revision"] == 2
    assert publish(client, edited).json()["code"] == "UNAPPROVED_KNOWLEDGE"
    bad = rule_for(p)
    bad["evidence"][0]["quote"] = "return amount > 100;"
    with pytest.raises(DomainError, match="EVIDENCE_QUOTE_INVALID"):
        studio.models.validate_evidence(bad, {"src/credit/Limit.java": SOURCE}, p["entities"])


def test_failed_graph_publish_does_not_switch_pointer(env, monkeypatch):
    client, studio, _ = env
    snap = upload(client)
    p = parse(studio, snap)
    c1 = approve(client, seed_candidate(studio, snap, p))
    r1 = publish(client, c1).json()
    c2 = approve(client, seed_candidate(studio, snap, p, "candidate-second"))
    def fail(_):
        raise DomainError("GRAPH_PREPARATION_FAILED", 503)
    monkeypatch.setattr(studio.graph, "prepare", fail)
    assert publish(client, c2, 1).status_code == 503
    assert studio.get("pointer", "current")["release_id"] == r1["id"]
    assert len(studio.store.all("release")) == 1
    assert studio.get("candidate", c2["id"])["status"] == "APPROVED"


def test_pointer_rollback_revision_and_history(env):
    client, studio, _ = env
    snap = upload(client)
    p = parse(studio, snap)
    a = approve(client, seed_candidate(studio, snap, p))
    r1 = publish(client, a).json()
    b = approve(client, seed_candidate(studio, snap, p, "candidate-second"))
    r2 = publish(client, b, 1).json()
    old_content = studio.get("release", r1["id"])
    denied = client.put("/api/v2/release-pointers/current", headers=headers(), json={"release_id": r1["id"], "expected_revision": 1, "reason": "old pointer"})
    assert denied.json()["code"] == "REVISION_CONFLICT"
    ok = client.put("/api/v2/release-pointers/current", headers=headers(), json={"release_id": r1["id"], "expected_revision": 2, "reason": "回退知识版本"})
    assert ok.status_code == 200
    assert studio.get("release", r1["id"]) == old_content
    assert studio.get("release", r2["id"])["rules"][0]["id"] == b["id"]


def test_jobs_persist_cancel_and_expired_lease_is_unknown(env):
    client, studio, settings = env
    snap = upload(client)
    job = studio.job({"type": "ANALYZE", "snapshot_id": snap["id"], "model_id": None, "projection_id": None}, "developer")
    job = studio.command(job["id"], "START", job["revision"], "developer")
    assert Store(settings.database_url).get("job", job["id"])["status"] == "QUEUED"
    job = studio.command(job["id"], "CANCEL", job["revision"], "developer")
    assert not studio.process_one() and job["status"] == "CANCELLED"
    job2 = studio.job({"type": "ANALYZE", "snapshot_id": snap["id"], "model_id": None, "projection_id": None}, "developer")
    with studio.store.transaction() as c:
        job2.update(status="CANCEL_REQUESTED", lease_until=(datetime.now(timezone.utc)-timedelta(minutes=1)).isoformat())
        studio.store.update("job", job2, job2["revision"], c)
    assert not studio.process_one()
    recovered = studio.get("job", job2["id"])
    assert recovered["status"] == "EXECUTION_UNKNOWN" and recovered["pending_intent"] == "CANCEL_REQUESTED"


@pytest.fixture
def model_server():
    state = {"invalid": False, "calls": 0, "http_status": 200, "finish_reason": "stop", "empty": False}
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass
        def do_POST(self):
            payload = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            assert self.headers["Authorization"] == "Bearer test-only-key"
            if state["http_status"] != 200:
                self.send_response(state["http_status"])
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"error":{"message":"provider body intentionally not exposed"}}')
                return
            source = json.loads(payload["messages"][1]["content"])
            ent = next(e for e in source["entities"] if e["kind"] == "Method")
            text = source["SOURCE_DATA"][ent["path"]]
            line_no = 2 if ent["path"] == "Validation.java" else 4
            quote = next(row["text"] for row in text if row["line"] == line_no).strip() if isinstance(text, list) else text.splitlines()[line_no-1].strip()
            rule = {"name": "额度校验", "condition": "amount <= 100", "outcome": "允许", "scope": "额度", "exceptions": [], "uncertainties": [], "entity_version_id": ent["id"], "evidence": [{"path": ent["path"], "start_line": line_no, "end_line": line_no, "quote": quote}]}
            if state["invalid"]:
                rule["evidence"][0]["quote"] = "fabricated evidence"
            value = {"model": payload["model"], "choices": [{"finish_reason": state["finish_reason"], "message": {"content": "" if state["empty"] else json.dumps({"rules": [rule]})}}], "usage": {"prompt_tokens": 10, "completion_tokens": 20}}
            state["calls"] += 1
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps(value).encode())
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{server.server_port}/v1", state
    server.shutdown()
    server.server_close()


def register(client, endpoint):
    response = client.post("/api/v2/model-connections", headers=headers(), json={"name": "test-fixture-provider", "endpoint": endpoint, "model_id": "test-model", "allow_source": True})
    assert response.status_code == 200, response.text
    return response.json()


def test_real_http_adapter_gate_and_evidence_quarantine(env, model_server):
    client, studio, _ = env
    endpoint, state = model_server
    config = register(client, endpoint)
    snap = upload(client)
    p = parse(studio, snap)
    body = {"type": "EXTRACT", "snapshot_id": snap["id"], "model_id": config["id"], "projection_id": p["id"]}
    denied = client.post("/api/v2/knowledge-jobs", headers=headers(), json=body)
    assert denied.json()["code"] == "MODEL_VALIDATION_REQUIRED"
    validation = client.post(f"/api/v2/model-connections/{config['id']}/validations", headers=headers()).json()
    assert validation["eligible"] is True and validation["fixture_only"] is True
    assert studio.store.all("candidate") == []
    job = client.post("/api/v2/knowledge-jobs", headers=headers(), json=body).json()
    studio.command(job["id"], "START", job["revision"], "admin")
    state["invalid"] = True
    assert studio.process_one()
    assert studio.get("job", job["id"])["error"] == "EVIDENCE_QUOTE_INVALID"
    assert studio.store.all("candidate") == []
    state["invalid"] = False
    job2 = studio.job(body, "admin")
    studio.command(job2["id"], "START", job2["revision"], "admin")
    assert studio.process_one()
    assert studio.get("job", job2["id"])["status"] == "SUCCEEDED"
    assert len(studio.store.all("candidate")) == 1
    serialized = json.dumps(client.get("/api/v2/model-connections", headers=headers()).json())
    assert "test-only-key" not in serialized


def test_model_credential_fingerprint_and_disabled_gate(env, model_server, monkeypatch):
    client, studio, _ = env
    endpoint, _ = model_server
    config = register(client, endpoint)
    assert studio.models.validate(config)["eligible"]
    monkeypatch.setenv("BOS_MODEL_API_KEY", "rotated-key")
    assert studio.models.eligibility(config)[1] == "MODEL_VALIDATION_STALE"
    monkeypatch.setenv("BOS_MODEL_API_KEY", "test-only-key")
    client.post(f"/api/v2/model-connections/{config['id']}/disable", headers=headers())
    assert studio.models.eligibility(studio.get("model", config["id"]))[1] == "MODEL_DISABLED"


def test_invalid_git_url_and_source_hash_tamper(env):
    client, studio, settings = env
    result = client.post("/api/v2/sources/git", headers=headers(), json={"name": "bad", "url": "file:///etc", "ref": "main"})
    assert result.json()["code"] == "GIT_URL_NOT_ALLOWED"
    snap = upload(client)
    (settings.data_dir / "snapshots" / snap["id"] / "src/credit/Limit.java").write_text("tampered")
    with pytest.raises(DomainError, match="EVIDENCE_HASH_MISMATCH"):
        studio.sources.read(snap, "src/credit/Limit.java")


def test_git_object_snapshot_uses_exact_commit_without_checkout(env, monkeypatch, tmp_path):
    import subprocess
    from backend import sources
    client, studio, _ = env
    repo = tmp_path / "git-fixture"
    repo.mkdir()
    subprocess.run(["git", "init", str(repo)], check=True, capture_output=True)
    (repo / "Limit.java").write_text(SOURCE)
    subprocess.run(["git", "-C", str(repo), "add", "Limit.java"], check=True, capture_output=True)
    subprocess.run(["git", "-C", str(repo), "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "-c", "core.hooksPath=/dev/null", "commit", "-m", "fixture"], check=True, capture_output=True)
    real_run = sources.run
    def local_transport(args, **kwargs):
        if "fetch" in args:
            args = list(args)
            args[-2:] = [str(repo), "HEAD"]
            kwargs["env"] = {**kwargs["env"], "GIT_ALLOW_PROTOCOL": "file"}
        return real_run(args, **kwargs)
    monkeypatch.setattr(sources, "run", local_transport)
    snap = studio.sources.clone("https://github.com/test-only/transport-fixture.git", "main", "git-fixture")
    commit = subprocess.run(["git", "-C", str(repo), "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip()
    assert snap["commit"] == commit and snap["mode"] == "COMMIT"
    assert studio.sources.read(snap, "Limit.java") == SOURCE


def test_pause_checkpoint_does_not_repeat_model_call(env, model_server, monkeypatch):
    client, studio, _ = env
    endpoint, state = model_server
    config = register(client, endpoint)
    studio.models.validate(config)
    snap = upload(client)
    p = parse(studio, snap)
    body = {"type": "EXTRACT", "snapshot_id": snap["id"], "model_id": config["id"], "projection_id": p["id"]}
    job = studio.job(body, "admin")
    studio.command(job["id"], "START", job["revision"], "admin")
    real_call = studio.models.call
    def pause_after_call(*args):
        output = real_call(*args)
        current = studio.get("job", job["id"])
        studio.command(job["id"], "PAUSE", current["revision"], "admin")
        return output
    monkeypatch.setattr(studio.models, "call", pause_after_call)
    studio.process_one()
    paused = studio.get("job", job["id"])
    assert paused["status"] == "PAUSED" and paused["checkpoint"] is not None
    call_count = state["calls"]
    studio.command(job["id"], "RESUME", paused["revision"], "admin")
    studio.process_one()
    assert state["calls"] == call_count
    assert studio.get("job", job["id"])["status"] == "SUCCEEDED"


def test_admin_can_register_exact_model_host_without_environment_setup(env):
    client, studio, settings = env
    settings.model_hosts = ()
    body = {"name": "runtime-provider", "endpoint": "https://MODEL.example/v1", "model_id": "example-model"}
    denied = client.post("/api/v2/model-connections", headers=headers(), json=body)
    assert denied.status_code == 403
    assert denied.json()["params"] == {"host": "model.example", "allowed_hosts": [], "policy": "administrator"}
    assert client.post("/api/v2/model-endpoint-hosts", headers=headers("developer"), json={"host": "model.example"}).status_code == 403
    approved = client.post("/api/v2/model-endpoint-hosts", headers=headers(), json={"host": "  MODEL.example. "})
    assert approved.status_code == 200 and approved.json()["host"] == "model.example"
    result = client.post("/api/v2/model-connections", headers=headers(), json=body)
    assert result.status_code == 200, result.text
    assert studio.models.eligibility(result.json())[1] == "MODEL_VALIDATION_REQUIRED"
    assert client.get("/api/v2/me", headers=headers()).json()["model_hosts"] == ["model.example"]
    from backend.models import ModelService
    restarted_worker_models = ModelService(settings, Store(settings.database_url), studio.sources)
    assert restarted_worker_models.check_endpoint(body["endpoint"]) == body["endpoint"]
    assert client.post("/api/v2/model-connections", headers=headers(), json=body | {"endpoint": "https://model.example.evil.invalid/v1"}).status_code == 403
    assert any(a["action"] == "MODEL_HOST_AUTHORIZE" for a in studio.store.all("audit"))


def test_deployment_host_policy_cannot_be_overridden_from_ui(env):
    client, studio, settings = env
    assert settings.model_hosts == ("127.0.0.1",)
    result = client.post("/api/v2/model-endpoint-hosts", headers=headers(), json={"host": "model.example"})
    assert result.status_code == 403 and result.json()["code"] == "MODEL_ENDPOINT_POLICY_LOCKED"
    assert studio.store.all("model_host") == []
    assert client.get("/api/v2/me", headers=headers()).json()["model_host_policy"] == "environment"


@pytest.mark.parametrize("endpoint,code", [
    ("https://user:password@model.example/v1", "MODEL_ENDPOINT_INVALID"),
    ("https://model.example:invalid/v1", "MODEL_ENDPOINT_INVALID"),
    ("https://model.example/v1?key=secret", "MODEL_ENDPOINT_INVALID"),
    ("http://model.example/v1", "MODEL_HTTPS_REQUIRED"),
])
def test_bad_endpoint_reports_validation_reason_instead_of_host_denial(env, endpoint, code):
    client, _, _ = env
    result = client.post("/api/v2/model-connections", headers=headers(), json={"name": "invalid", "endpoint": endpoint, "model_id": "test"})
    assert result.status_code == 422 and result.json()["code"] == code
    assert "secret" not in result.text and "password" not in result.text


def test_host_grant_is_checked_again_by_worker_call(env, monkeypatch):
    client, studio, settings = env
    settings.model_hosts = ()
    studio.models.authorize_host("model.example", "admin")
    config = studio.models.register({"name": "test", "endpoint": "https://model.example/v1", "model_id": "test", "credential_env": "BOS_MODEL_API_KEY", "allow_source": True, "max_input_chars": 24000, "max_output_tokens": 4000, "temperature": 0.1})
    settings.model_hosts = ("other.example",)
    with pytest.raises(DomainError, match="MODEL_ENDPOINT_NOT_ALLOWED"):
        studio.models.call(config, {}, [])


def test_direct_api_key_is_encrypted_not_returned_and_shared_with_worker(env, model_server, monkeypatch):
    import os
    from backend.models import ModelService
    client, studio, settings = env
    endpoint, _ = model_server
    body = {"name": "direct-key-test", "endpoint": endpoint, "model_id": "test-model", "credential_mode": "api_key", "api_key": "test-only-key", "allow_source": True}
    response = client.post("/api/v2/model-connections", headers=headers(), json=body)
    assert response.status_code == 200, response.text
    config = response.json()
    assert "api_key" not in config and "test-only-key" not in response.text
    assert config["credential_mode"] == "api_key" and config["credential_env"] == ""
    assert config["credential_reference"].startswith("credential_")
    monkeypatch.delenv("BOS_MODEL_API_KEY")
    restarted = ModelService(settings, Store(settings.database_url), studio.sources)
    assert restarted.validate(config)["eligible"]
    for path in (settings.data_dir / "credentials").iterdir():
        assert path.stat().st_mode & 0o077 == 0
        assert b"test-only-key" not in path.read_bytes()
    for kind in ("model", "validation", "audit", "job"):
        assert "test-only-key" not in json.dumps(studio.store.all(kind))
    assert "test-only-key" not in client.get("/api/v2/model-connections", headers=headers()).text
    assert "credential_reference" not in client.get("/api/v2/model-connections", headers=headers("developer")).text


@pytest.mark.parametrize("payload,code", [
    ({"credential_mode": "environment", "credential_env": "sk-test-not-a-variable"}, "INVALID_CREDENTIAL_REFERENCE"),
    ({"credential_mode": "api_key"}, "MODEL_API_KEY_REQUIRED"),
    ({"credential_mode": "environment", "api_key": "sk-test-only-not-real"}, "CREDENTIAL_MODE_MISMATCH"),
    ({"credential_mode": "api_key", "api_key": "bad\nkey"}, "MODEL_API_KEY_INVALID"),
])
def test_credential_modes_reject_misplaced_or_missing_secret_without_echo(env, payload, code):
    client, studio, _ = env
    result = client.post("/api/v2/model-connections", headers=headers(), json={"name": "invalid", "endpoint": "http://127.0.0.1/v1", "model_id": "test", **payload})
    assert result.status_code in (400, 422) and result.json()["code"] == code
    assert "sk-test" not in result.text and "bad\\nkey" not in result.text
    assert studio.store.all("model") == []


def test_missing_vault_master_key_never_silently_replaces_credentials(env):
    client, studio, settings = env
    ref = studio.models.vault.put("test-only-key")
    (settings.data_dir / "credentials/master.key").unlink()
    with pytest.raises(DomainError, match="CREDENTIAL_STORE_UNAVAILABLE"):
        studio.models.vault.get(ref)
    assert not (settings.data_dir / "credentials/master.key").exists()


@pytest.mark.parametrize("status,code", [(401,"MODEL_AUTHENTICATION_FAILED"),(402,"MODEL_BALANCE_INSUFFICIENT"),(403,"MODEL_ACCESS_DENIED"),(404,"MODEL_NOT_FOUND"),(429,"MODEL_RATE_LIMITED")])
def test_model_provider_errors_are_distinguished_without_echoing_body(env, model_server, status, code):
    client, studio, _ = env
    endpoint, state = model_server
    config = register(client, endpoint)
    state["http_status"] = status
    validation = studio.models.validate(config)
    assert validation["error"] == code and not validation["eligible"]
    assert validation["checks"]["connection"] == "PASS"
    assert "provider body" not in json.dumps(validation)


@pytest.mark.parametrize("flag,code", [("empty", "MODEL_OUTPUT_EMPTY"), ("finish_reason", "MODEL_OUTPUT_TRUNCATED")])
def test_empty_and_truncated_final_output_are_not_accepted(env, model_server, flag, code):
    client, studio, _ = env
    endpoint, state = model_server
    config = register(client, endpoint)
    state[flag] = True if flag == "empty" else "length"
    result = studio.models.validate(config)
    assert result["error"] == code and not result["eligible"]
    assert result["checks"]["structured_output"] == "FAIL"


@pytest.mark.parametrize("base", ["https://api.deepseek.com", "https://api.deepseek.com/v1"])
def test_deepseek_request_uses_correct_base_model_and_non_thinking_json(env, monkeypatch, base):
    import httpx
    from backend import models
    client, studio, settings = env
    settings.model_hosts = ("api.deepseek.com",)
    config = studio.models.register({"name": "DeepSeek fixture", "endpoint": base, "model_id": "deepseek-flash", "credential_env": "BOS_MODEL_API_KEY", "allow_source": True, "max_input_chars": 24000, "max_output_tokens": 4000, "temperature": 0.1})
    def handler(request):
        payload = json.loads(request.content)
        assert str(request.url) == base + "/chat/completions"
        assert payload["model"] == "deepseek-flash"
        assert payload["thinking"] == {"type": "disabled"}
        assert payload["response_format"] == {"type": "json_object"}
        return httpx.Response(200, json={"model": "deepseek-flash", "choices": [{"message": {"content": '{"rules": []}'}, "finish_reason": "stop"}]})
    real_client = httpx.Client
    monkeypatch.setattr(models.httpx, "Client", lambda **kwargs: real_client(transport=httpx.MockTransport(handler), **kwargs))
    result, _ = studio.models.call(config, {}, [])
    assert result == {"rules": []}


def test_batch_plan_lossless_absolute_lines_and_atomic_methods():
    from backend.extraction import input_message, plan_extraction
    lines = ['class Large {']
    entities = []
    for i in range(30):
        start = len(lines) + 1
        lines += [f' boolean m{i}(int amount) {{', ' // ' + ('复杂 "quoted" \\ context ' * 55), ' return amount <= 100;', ' }']
        entities.append({'id': f'm{i}', 'kind': 'Method', 'name': f'm{i}', 'path': 'Large.java', 'start_line': start, 'end_line': len(lines)})
    lines.append('}')
    entities.append({'id': 'class', 'kind': 'Class', 'path': 'Large.java', 'start_line': 1, 'end_line': len(lines)})
    texts = {'Large.java': '\n'.join(lines), 'rules.md': '\n'.join(['业务说明' * 400] * 12)}
    plan, batches = plan_extraction(texts, entities, 7000, 4000)
    assert plan['total_batches'] > 1
    assert plan == plan_extraction(texts, entities, 7000, 4000)[0]
    for path, text in texts.items():
        seen = []
        for batch in batches:
            if path not in batch['ranges']:
                continue
            r = batch['ranges'][path]
            seen.extend(range(r['start_line'], r['end_line'] + 1))
            assert batch['source'][path] == '\n'.join(text.splitlines()[r['start_line'] - 1:r['end_line']])
        assert seen == list(range(1, len(text.splitlines()) + 1))
    for batch in batches:
        assert len(input_message(batch['source'], batch['entities'], batch['ranges'])) <= 7000
        for e in entities:
            if e['kind'] == 'Method' and 'Large.java' in batch['ranges']:
                r = batch['ranges']['Large.java']
                assert not (e['start_line'] < r['start_line'] <= e['end_line'])
                assert not (e['start_line'] <= r['end_line'] < e['end_line'])


def test_batch_plan_rejects_oversized_method_without_truncation():
    from backend.extraction import plan_extraction
    source = 'class Large {\nboolean f() {\n // ' + 'x' * 9000 + '\nreturn true;\n}\n}'
    entities = [{'id': 'm', 'kind': 'Method', 'path': 'Large.java', 'start_line': 2, 'end_line': 5}]
    with pytest.raises(DomainError) as error:
        plan_extraction({'Large.java': source}, entities, 4000, 4000)
    assert error.value.code == 'MODEL_INPUT_UNIT_TOO_LARGE'
    assert error.value.params['start_line'] == 2 and error.value.params['end_line'] == 5
    assert error.value.params['chars'] > error.value.params['limit']


def test_batch_evidence_cannot_cite_an_unsent_range():
    from backend.extraction import validate_batch_evidence
    batch = {'ranges': {'Large.java': {'start_line': 10, 'end_line': 15}}}
    with pytest.raises(DomainError) as error:
        validate_batch_evidence({'evidence': [{'path': 'Large.java', 'start_line': 4, 'end_line': 4}]}, batch)
    assert error.value.code == 'EVIDENCE_OUTSIDE_BATCH'


def test_multi_batch_failure_retry_checkpoint_and_atomic_candidates(env, model_server, monkeypatch):
    from backend.extraction import input_message
    client, studio, _ = env
    endpoint, _ = model_server
    config = register(client, endpoint)
    studio.models.validate(config)
    # Each file fits alone; the three together exceed the per-call budget.
    snap = upload(client, {f'src/C{i}.java': SOURCE.replace('class Limit', f'class C{i}') + '\n// ' + 'x' * 13000 for i in range(3)})
    p = parse(studio, snap)
    job = studio.job({'type': 'EXTRACT', 'snapshot_id': snap['id'], 'projection_id': p['id'], 'model_id': config['id']}, 'admin')
    assert job['extraction_plan']['total_batches'] == 3
    assert 'checkpoint' not in job
    sent, state = [], {'fail': False}
    def fake_call(config, sources, entities, ranges, line_records=False):
        assert len(input_message(sources, entities, ranges, line_records)) <= config['max_input_chars']
        path = next(iter(sources))
        sent.append(path)
        if state['fail'] and path == 'src/C1.java':
            raise DomainError('MAPPING_EVIDENCE_REQUIRED', 422)
        ent = next(e for e in entities if e['kind'] == 'Method' and e['name'] == 'allowed')
        rule = rule_for(p)
        rule['entity_version_id'] = ent['id']
        rule['evidence'][0]['path'] = path
        return {'rules': [rule]}, {'prompt_tokens': 10, 'completion_tokens': 20}
    monkeypatch.setattr(studio.models, 'call', fake_call)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    studio.process_one()
    current = studio.get('job', job['id'])
    assert current['status'] == 'QUEUED' and current['extraction_plan']['completed_batches'] == 1
    assert studio.store.all('candidate') == []
    paused = studio.command(job['id'], 'PAUSE', current['revision'], 'admin')
    assert not studio.process_one()
    studio.command(job['id'], 'RESUME', paused['revision'], 'admin')
    state['fail'] = True
    studio.process_one()
    quarantined = studio.get('job', job['id'])
    assert quarantined['status'] == 'QUEUED' and quarantined['batch_states'][1]['status'] == 'INVALID'
    assert studio.store.all('candidate') == []
    studio.process_one()  # the third batch runs despite the second batch's mapping failure
    failed = studio.get('job', job['id'])
    assert failed['status'] == 'PARTIAL_SUCCEEDED'
    assert failed['batch_summary']['SUCCEEDED'] == 2 and failed['batch_summary']['INVALID'] == 1
    assert failed['checkpoint']['completed_batches'] == 2 and len(studio.store.all('candidate')) == 2
    assert all(c['uncertainties'][-1].startswith('INCOMPLETE_EXTRACTION_COVERAGE') for c in studio.store.all('candidate'))
    assert client.post(f"/api/v2/knowledge-jobs/{job['id']}/retry", headers=headers('agent'), json={'expected_revision': failed['revision']}).status_code == 403
    retry = client.post(f"/api/v2/knowledge-jobs/{job['id']}/retry", headers=headers(), json={'expected_revision': failed['revision']}).json()
    assert retry['status'] == 'CREATED' and retry['retry_of'] == job['id']
    assert retry['extraction_plan']['completed_batches'] == 2
    assert not studio.process_one()
    state['fail'] = False
    studio.command(retry['id'], 'START', retry['revision'], 'admin')
    while studio.process_one():
        pass
    done = studio.get('job', retry['id'])
    assert done['status'] == 'SUCCEEDED' and done['extraction_plan']['completed_batches'] == 3
    assert len(done['candidate_ids']) == 3 and done['usage'] == {'prompt_tokens': 30, 'completion_tokens': 60}
    assert sent == ['src/C0.java', 'src/C1.java', 'src/C2.java', 'src/C1.java']
    assert studio.get('job', job['id'])['status'] == 'PARTIAL_SUCCEEDED'


def test_pause_during_timeout_preserves_unknown_without_replay(env, model_server, monkeypatch):
    client, studio, _ = env
    endpoint, _ = model_server
    config = register(client, endpoint)
    studio.models.validate(config)
    snap = upload(client)
    p = parse(studio, snap)
    job = studio.job({'type': 'EXTRACT', 'snapshot_id': snap['id'], 'projection_id': p['id'], 'model_id': config['id']}, 'admin')
    def timeout(*args):
        current = studio.get('job', job['id'])
        studio.command(job['id'], 'PAUSE', current['revision'], 'admin')
        raise DomainError('MODEL_TIMEOUT', 504)
    monkeypatch.setattr(studio.models, 'call', timeout)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    studio.process_one()
    current = studio.get('job', job['id'])
    assert current['status'] == 'PAUSED' and current['batch_states'][0]['status'] == 'UNKNOWN'
    studio.command(job['id'], 'RESUME', current['revision'], 'admin')
    studio.process_one()  # finish the unknown batch without making another provider call
    current = studio.get('job', job['id'])
    assert current['status'] == 'NEEDS_ATTENTION' and current['batch_states'][0]['attempts'] == 1
    retry = studio.retry_job(job['id'], current['revision'], 'admin')
    assert retry['batch_states'][0]['status'] == 'UNKNOWN'
    replay = studio.retry_job(job['id'], current['revision'], 'admin', include_unknown=True)
    assert replay['batch_states'][0]['status'] == 'PENDING' and replay['unknown_replay_authorized']


def test_extraction_defaults_admin_cas_limits_and_persistence(env):
    client, studio, settings = env
    endpoint = '/api/v2/settings/extraction'
    assert client.get(endpoint).status_code == 401
    assert client.get(endpoint, headers=headers('agent')).status_code == 403
    initial = client.get(endpoint, headers=headers('developer')).json()
    assert initial['max_source_lines'] == 100 and initial['revision'] == 0
    body = {'max_source_lines': 60, 'expected_revision': 0}
    assert client.put(endpoint, headers=headers('developer'), json=body).status_code == 403
    saved = client.put(endpoint, headers=headers(), json=body).json()
    assert saved['revision'] == 1 and saved['max_source_lines'] == 60
    assert client.put(endpoint, headers=headers(), json=body).status_code == 409
    for bad in (0, 2001, 1.5, '100'):
        assert client.put(endpoint, headers=headers(), json={'max_source_lines': bad, 'expected_revision': 1}).status_code == 422
    assert create_app(settings).state.studio.extraction_defaults()['max_source_lines'] == 60
    assert studio.store.all('audit')[-1]['action'] == 'EXTRACTION_DEFAULTS_UPDATE'
    assert studio.store.all('job') == []


def test_line_cap_complete_coverage_partial_methods_and_numbered_wire():
    from backend.extraction import input_message, plan_extraction
    source = '\n'.join(['class Long {', ' boolean allowed(int amount) {'] + [' // comment "with quotes"'] * 210 + [' return amount <= 100;', ' }', '}'])
    method = {'id': 'long-method', 'kind': 'Method', 'path': 'Long.java', 'start_line': 2, 'end_line': 214}
    texts = {'Long.java': source, 'business.md': '\n'.join(['规则文档'] * 140)}
    plan, batches = plan_extraction(texts, [method], 24000, 4000, 100)
    assert plan['max_source_lines'] == 100 and plan['partial_method_batches'] >= 2
    assert len(batches) >= 4
    for entry, batch in zip(plan['batches'], batches):
        assert 0 < entry['source_lines'] <= 100
        wire = input_message(batch['source'], batch['entities'], batch['ranges'], True)
        assert len(wire) == entry['input_chars'] <= 24000
        data = json.loads(wire)
        assert sum(map(len, data['SOURCE_DATA'].values())) == entry['source_lines']
        for path, rows in data['SOURCE_DATA'].items():
            for row in rows:
                assert row['text'] == texts[path].splitlines()[row['line'] - 1]
    for path, text in texts.items():
        seen = [line for b in batches if path in b['ranges'] for line in range(b['ranges'][path]['start_line'], b['ranges'][path]['end_line']+1)]
        assert seen == list(range(1, len(text.splitlines()) + 1))
    assert plan['digest'] != plan_extraction(texts, [method], 24000, 4000, 80)[0]['digest']


def test_line_cap_also_obeys_character_budget_and_long_lines():
    from backend.extraction import plan_extraction
    text = '\n'.join(['文字 "quoted" ' * 40] * 160)
    plan, _ = plan_extraction({'notes.md': text}, [], 4000, 1000, 100)
    assert all(b['input_chars'] <= 4000 and b['source_lines'] < 100 for b in plan['batches'])
    with pytest.raises(DomainError) as error:
        plan_extraction({'notes.md': 'x' * 9000}, [], 4000, 1000, 100)
    assert error.value.code == 'MODEL_INPUT_UNIT_TOO_LARGE'


def test_exact_quote_line_alignment_preserves_strict_evidence(env):
    from backend.extraction import align_exact_quotes
    _, studio, _ = env
    texts = {'A.java': 'header\nboolean allowed(int amount) {\n return amount <= 100;\n}\n// footer'}
    ent = {'id': 'm', 'kind': 'Method', 'path': 'A.java', 'start_line': 2, 'end_line': 4}
    batch = {'source': {'A.java': '\n'.join(texts['A.java'].splitlines()[1:4])}, 'ranges': {'A.java': {'start_line': 2, 'end_line': 4}}, 'entities': [ent]}
    rule = {'entity_version_id': 'm', 'evidence': [{'path': 'A.java', 'start_line': 1, 'end_line': 1, 'quote': 'return amount <= 100;'}]}
    repairs = align_exact_quotes(rule, texts, batch)
    assert repairs[0]['start_line'] == 3 and rule['evidence'][0]['start_line'] == 3
    studio.models.validate_evidence(rule, texts, [ent])
    rule['evidence'][0].update(quote='return amount < 100;', start_line=3)
    assert align_exact_quotes(rule, texts, batch) == []
    with pytest.raises(DomainError) as error:
        studio.models.validate_evidence(rule, texts, [ent])
    assert error.value.code == 'EVIDENCE_QUOTE_INVALID' and error.value.params['start_line'] == 3
    rule['evidence'][0].update(quote='// footer', start_line=1, end_line=1)
    assert align_exact_quotes(rule, texts, batch) == []  # original text exists but was not sent
    repeated = {'source': {'A.java': 'same\nsame'}, 'ranges': {'A.java': {'start_line': 10, 'end_line': 11}}}
    rule['evidence'][0].update(quote='same', start_line=1, end_line=1)
    assert align_exact_quotes(rule, {'A.java': '\n'*9+'same\nsame'}, repeated) == []


def test_pinned_line_defaults_retry_new_plan_and_partial_review_gate(env, model_server, monkeypatch):
    client, studio, _ = env
    endpoint, _ = model_server
    config = register(client, endpoint)
    studio.models.validate(config)
    # A real AST method larger than the default cap.
    source = 'class Long {\nboolean allowed(int amount) {\n' + ('// context\n' * 130) + 'return amount <= 100;\n}\n}'
    snap = upload(client, {'Long.java': source})
    projection = parse(studio, snap)
    body = {'type': 'EXTRACT', 'snapshot_id': snap['id'], 'projection_id': projection['id'], 'model_id': config['id']}
    job = studio.job(body, 'admin')
    assert job['extraction_settings']['max_source_lines'] == 100
    studio.update_extraction_defaults({'max_source_lines': 50, 'expected_revision': 0}, 'admin')
    seen = []
    def fake(config, texts, entities, ranges, line_records=False):
        assert line_records is True
        seen.append(sum(r['end_line'] - r['start_line'] + 1 for r in ranges.values()))
        if 'return amount <= 100;' not in texts['Long.java']:
            return {'rules': []}, {'prompt_tokens': 1}
        method = next(e for e in entities if e['kind'] == 'Method')
        # Deliberately excerpt-relative lines: the exact unique quote is safely aligned.
        rule = {'name': '测试额度规则', 'condition': 'amount <= 100', 'outcome': '允许', 'scope': '测试', 'exceptions': [], 'uncertainties': [], 'entity_version_id': method['id'], 'evidence': [{'path': 'Long.java', 'start_line': 1, 'end_line': 1, 'quote': 'return amount <= 100;'}]}
        return {'rules': [rule]}, {'prompt_tokens': 1}
    monkeypatch.setattr(studio.models, 'call', fake)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    while studio.process_one():
        pass
    done = studio.get('job', job['id'])
    assert done['status'] == 'SUCCEEDED' and max(seen) == 100  # default changed, pinned job did not
    candidate = studio.get('candidate', done['candidate_ids'][0])
    assert candidate['evidence'][0]['start_line'] == 133
    assert candidate['uncertainties'][0].startswith('PARTIAL_METHOD_CONTEXT:')
    assert done['evidence_line_corrections']
    denied = client.post(f"/api/v2/reviews/{candidate['id']}/decision", headers=headers('reviewer'), json={'decision': 'APPROVE', 'reason': 'test', 'expected_revision': candidate['revision']})
    assert denied.status_code == 409 and denied.json()['code'] == 'UNRESOLVED_UNCERTAINTIES'
    new = studio.job(body, 'admin')
    assert new['extraction_settings']['max_source_lines'] == 50
    assert new['extraction_plan']['digest'] != job['extraction_plan']['digest']
    studio.command(new['id'], 'START', new['revision'], 'admin')
    def fail(*args):
        raise DomainError('EVIDENCE_QUOTE_INVALID', 422)
    monkeypatch.setattr(studio.models, 'call', fail)
    while studio.process_one():
        pass
    failed = studio.get('job', new['id'])
    studio.update_extraction_defaults({'max_source_lines': 20, 'expected_revision': 1}, 'admin')
    retry = studio.retry_job(new['id'], failed['revision'], 'admin')
    assert retry['extraction_settings']['max_source_lines'] == 20 and retry['status'] == 'CREATED'
    assert 'checkpoint' not in retry


def retry_fixture(env, model_server, monkeypatch, files=3):
    client, studio, settings = env
    endpoint, _ = model_server
    config = register(client, endpoint)
    studio.models.validate(config)
    snap = upload(client, {f'C{i}.java': SOURCE.replace('class Limit', f'class C{i}') + '\n// ' + 'x' * 13000 for i in range(files)})
    projection = parse(studio, snap)
    job = studio.job({'type': 'EXTRACT', 'snapshot_id': snap['id'], 'projection_id': projection['id'], 'model_id': config['id']}, 'admin')
    def output(config, sources, entities, ranges, *rest):
        method = next(e for e in entities if e['kind'] == 'Method' and e['name'] == 'allowed')
        rule = rule_for(projection)
        rule['entity_version_id'] = method['id']
        rule['evidence'][0]['path'] = method['path']
        return {'rules': [rule]}, {'prompt_tokens': 10, 'completion_tokens': 20}
    return studio, job, output


def make_retries_due(studio, job_id):
    with studio.store.transaction() as conn:
        job = studio.get('job', job_id, conn)
        job['provider_retry_at'] = '2000-01-01T00:00:00+00:00'
        for batch in job['batch_states']:
            if batch['status'] == 'RETRY_WAIT':
                batch['next_retry_at'] = '2000-01-01T00:00:00+00:00'
        return studio.store.update('job', job, job['revision'], conn)


def test_transient_backoff_is_durable_bounded_and_cancellable(env, model_server, monkeypatch):
    from backend.service import Studio
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    sent = []
    def call(*args):
        sent.append(next(iter(args[1])))
        if len(sent) <= 2:
            raise DomainError('MODEL_RATE_LIMITED', 502, {'http_status': 429, 'retry_after_seconds': 30})
        return output(*args)
    monkeypatch.setattr(studio.models, 'call', call)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    studio.process_one()
    waiting = studio.get('job', job['id'])
    assert waiting['status'] == 'WAITING_RETRY' and waiting['batch_states'][0]['attempts'] == 1
    assert waiting['batch_states'][0]['next_retry_at'] > waiting['events'][-1]['at']
    assert not studio.process_one() and sent == ['C0.java']  # no blocking sleep or hammering other batches
    restarted = Studio(env[2], Store(env[2].database_url))
    assert not restarted.process_one() and restarted.get('job', job['id'])['status'] == 'WAITING_RETRY'
    paused = studio.command(job['id'], 'PAUSE', waiting['revision'], 'admin')
    assert paused['status'] == 'PAUSED'
    studio.command(job['id'], 'RESUME', paused['revision'], 'admin')
    make_retries_due(studio, job['id'])
    studio.process_one()
    waiting = studio.get('job', job['id'])
    assert waiting['status'] == 'WAITING_RETRY' and waiting['batch_states'][0]['attempts'] == 2
    make_retries_due(studio, job['id'])
    while studio.process_one():
        pass
    done = studio.get('job', job['id'])
    assert done['status'] == 'SUCCEEDED' and sent == ['C0.java'] * 3 + ['C1.java', 'C2.java']
    assert done['batch_states'][0]['attempts'] == 3


def test_retry_exhaustion_isolates_batch_and_others_finish(env, model_server, monkeypatch):
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    calls = []
    def call(*args):
        path = next(iter(args[1])); calls.append(path)
        if path == 'C0.java':
            raise DomainError('MODEL_CONNECTION_FAILED', 502, {'delivery': 'not_sent'})
        return output(*args)
    monkeypatch.setattr(studio.models, 'call', call)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    for _ in range(8):
        make_retries_due(studio, job['id'])
        if not studio.process_one():
            break
    done = studio.get('job', job['id'])
    assert done['status'] == 'PARTIAL_SUCCEEDED' and done['batch_summary']['FAILED'] == 1
    assert done['batch_summary']['SUCCEEDED'] == 2 and calls.count('C0.java') == 3
    assert len(done['candidate_ids']) == 2
    candidate = studio.get('candidate', done['candidate_ids'][0])
    assert candidate['extraction_coverage'] == 'PARTIAL'
    response = env[0].post(f"/api/v2/reviews/{candidate['id']}/decision", headers=headers('reviewer'), json={'decision':'APPROVE','reason':'test','expected_revision':candidate['revision']})
    assert response.status_code == 409 and response.json()['code'] == 'UNRESOLVED_UNCERTAINTIES'


def test_unknown_response_is_not_replayed_other_batches_continue(env, model_server, monkeypatch):
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    calls=[]
    def call(*args):
        path=next(iter(args[1]));calls.append(path)
        if path=='C1.java':raise DomainError('MODEL_TIMEOUT',504,{'delivery':'unknown'})
        return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    while studio.process_one():pass
    done=studio.get('job',job['id'])
    assert done['status']=='PARTIAL_SUCCEEDED' and done['batch_summary']['UNKNOWN']==1
    assert calls==['C0.java','C1.java','C2.java']
    retry=studio.retry_job(job['id'],done['revision'],'admin')
    assert [b['status'] for b in retry['batch_states']]==['SUCCEEDED','UNKNOWN','SUCCEEDED']
    studio.update_extraction_defaults({'max_source_lines':50,'expected_revision':0},'admin')
    with pytest.raises(DomainError) as exc:studio.retry_job(job['id'],done['revision'],'admin')
    assert exc.value.code=='UNKNOWN_REPLAY_CONFIRMATION_REQUIRED'


def test_worker_crash_records_unknown_and_continues_without_duplicate_request(env, model_server, monkeypatch):
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    with studio.store.transaction() as conn:
        current=studio.get('job',job['id'],conn)
        current.update(status='RUNNING',active_batch=1,lease_until='2000-01-01T00:00:00+00:00')
        current['batch_states'][0].update(status='RUNNING',attempts=1)
        studio.store.update('job',current,current['revision'],conn)
    calls=[]
    def call(*args):calls.append(next(iter(args[1])));return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    assert studio.process_one()
    while studio.process_one():pass
    done=studio.get('job',job['id'])
    assert calls==['C1.java','C2.java'] and done['batch_states'][0]['status']=='UNKNOWN'
    assert done['status']=='PARTIAL_SUCCEEDED'


@pytest.mark.parametrize('exception,code,delivery',[
    ('ConnectTimeout','MODEL_CONNECT_TIMEOUT','not_sent'),
    ('ConnectError','MODEL_CONNECTION_FAILED','not_sent'),
    ('ReadTimeout','MODEL_TIMEOUT','unknown'),
    ('RemoteProtocolError','MODEL_RESPONSE_INTERRUPTED','unknown'),
])
def test_model_transport_delivery_classification(env,monkeypatch,exception,code,delivery):
    import httpx
    import backend.models as models
    client,studio,_=env
    config=register(client,'http://127.0.0.1:8099/v1')
    real_client=httpx.Client
    def fail(request):raise getattr(httpx,exception)('test-only transport exception',request=request)
    monkeypatch.setattr(models.httpx,'Client',lambda **kw:real_client(transport=httpx.MockTransport(fail),**kw))
    with pytest.raises(DomainError) as exc:studio.models.call(config,{},[])
    assert exc.value.code==code and exc.value.params['delivery']==delivery


def test_provider_503_retry_after_and_pinned_timeout(env,monkeypatch):
    import httpx
    import backend.models as models
    client,studio,_=env
    config=register(client,'http://127.0.0.1:8099/v1')
    real_client=httpx.Client
    captured={}
    def factory(**kwargs):
        captured['timeout']=kwargs['timeout'].read
        return real_client(transport=httpx.MockTransport(lambda request:httpx.Response(503,headers={'Retry-After':'42'})),**kwargs)
    monkeypatch.setattr(models.httpx,'Client',factory)
    with pytest.raises(DomainError) as exc:studio.models.call({**config,'request_timeout_seconds':120},{},[])
    assert exc.value.code=='MODEL_TEMPORARILY_UNAVAILABLE' and exc.value.params['retry_after_seconds']==42
    assert captured['timeout']==120


def test_legacy_prefix_recovery_reuses_validated_output(env, model_server, monkeypatch):
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    monkeypatch.setattr(studio.models, 'call', output)
    studio.command(job['id'], 'START', job['revision'], 'admin')
    studio.process_one()
    current = studio.get('job', job['id'])
    with studio.store.transaction() as conn:
        current.update(status='FAILED', error='MAPPING_EVIDENCE_REQUIRED', error_params={'batch': 2})
        current.pop('execution_policy')
        current.pop('batch_states')
        studio.store.update('job', current, current['revision'], conn)
    original = studio.get('job', job['id'])
    retry = studio.retry_job(job['id'], original['revision'], 'admin')
    assert retry['checkpoint']['output'] == original['checkpoint']['output']
    assert [s['status'] for s in retry['batch_states']] == ['SUCCEEDED', 'PENDING', 'PENDING']
    calls = []
    def resumed(*args):calls.append(next(iter(args[1])));return output(*args)
    monkeypatch.setattr(studio.models, 'call', resumed)
    studio.command(retry['id'], 'START', retry['revision'], 'admin')
    while studio.process_one():pass
    assert calls == ['C1.java', 'C2.java'] and studio.get('job', retry['id'])['status'] == 'SUCCEEDED'


def test_global_auth_failure_stops_requests_and_preserves_checkpoint(env, model_server, monkeypatch):
    studio, job, output = retry_fixture(env, model_server, monkeypatch)
    calls=[]
    def call(*args):
        calls.append(next(iter(args[1])))
        if len(calls)>1:raise DomainError('MODEL_AUTHENTICATION_FAILED',502,{'http_status':401})
        return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    while studio.process_one():pass
    done=studio.get('job',job['id'])
    assert done['status']=='FAILED' and done['error']=='MODEL_AUTHENTICATION_FAILED'
    assert done['checkpoint']['completed_batches']==1 and len(done['checkpoint']['output']['rules'])==1
    assert len(calls)==2 and not studio.store.all('candidate')


def test_waiting_retry_can_cancel_without_more_requests(env, model_server, monkeypatch):
    studio, job, output=retry_fixture(env,model_server,monkeypatch)
    def call(*args):raise DomainError('MODEL_RATE_LIMITED',502,{'http_status':429})
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    studio.process_one()
    waiting=studio.get('job',job['id'])
    assert waiting['status']=='WAITING_RETRY'
    studio.command(job['id'],'CANCEL',waiting['revision'],'admin')
    assert not studio.process_one() and studio.get('job',job['id'])['status']=='CANCELLED'


def test_trickling_response_has_overall_deadline(env, monkeypatch):
    import httpx
    import backend.models as models
    client, studio, _ = env
    config = register(client, 'http://127.0.0.1:8099/v1')
    real_client = httpx.Client
    class SlowStream(httpx.SyncByteStream):
        def __iter__(self):
            yield b' '
            yield b' '
    clock = iter([0, 30, 121])
    monkeypatch.setattr(models, 'monotonic', lambda: next(clock))
    monkeypatch.setattr(models.httpx, 'Client', lambda **kwargs: real_client(transport=httpx.MockTransport(lambda request: httpx.Response(200, stream=SlowStream())), **kwargs))
    with pytest.raises(DomainError) as exc:
        studio.models.call({**config, 'request_timeout_seconds': 120}, {}, [])
    assert exc.value.code == 'MODEL_TIMEOUT' and exc.value.params['phase'] == 'overall_response'
    assert exc.value.params['delivery'] == 'unknown'


def subtask_api(client, job, index, command, revision, role='admin', include_unknown=False):
    return client.post(f"/api/v2/knowledge-jobs/{job['id']}/subtasks/{index}/commands", headers=headers(role), json={'command': command, 'expected_revision': revision, 'include_unknown': include_unknown})


def test_subtask_listing_filters_pagination_detail_and_permissions(env, model_server, monkeypatch):
    studio, job, _ = retry_fixture(env, model_server, monkeypatch)
    client=env[0]
    endpoint='/api/v2/extraction-subtasks'
    assert client.get(endpoint).status_code==401
    assert client.get(endpoint,headers=headers('agent')).status_code==403
    result=client.get(endpoint,headers=headers('reviewer'),params={'job_id':job['id'],'limit':2}).json()
    assert result['total']==3 and len(result['items'])==2 and result['summary']['PENDING']==3
    item=result['items'][0]
    assert item['id'].startswith('subtask_') and item['revision']==1 and item['ranges']
    detail=client.get(f"/api/v2/knowledge-jobs/{job['id']}/subtasks/1",headers=headers('developer')).json()
    assert detail['id']==item['id'] and detail['policy']['transient_retries']==2 and detail['attempt_history']==[]
    assert 'checkpoint' not in item and 'credential' not in json.dumps(item)
    assert client.get(endpoint,headers=headers(),params={'job_id':job['id'],'q':'C1.java'}).json()['total']==1
    assert client.get(endpoint,headers=headers(),params={'job_id':job['id'],'offset':2,'limit':2}).json()['items'][0]['index']==3
    assert client.get(endpoint,headers=headers(),params={'status':'bogus'}).status_code==422
    assert client.get(endpoint,headers=headers(),params={'limit':101}).status_code==422
    assert client.get(f"/api/v2/knowledge-jobs/{job['id']}/subtasks/4",headers=headers()).status_code==404
    assert subtask_api(client,job,1,'HOLD',1,role='reviewer').status_code==403


def test_subtask_hold_resume_skip_restore_do_not_run_a_created_parent(env, model_server, monkeypatch):
    studio, job, output=retry_fixture(env,model_server,monkeypatch)
    client=env[0]
    held=subtask_api(client,job,1,'HOLD',1).json()
    assert held['parent_status']=='CREATED' and held['items'][0]['status']=='HELD'
    assert subtask_api(client,job,1,'RESUME',1).status_code==409
    restored=subtask_api(client,job,1,'RESUME',held['items'][0]['revision']).json()
    assert restored['items'][0]['status']=='PENDING' and not studio.process_one()
    skipped=subtask_api(client,job,1,'SKIP',restored['items'][0]['revision']).json()
    restored=subtask_api(client,job,1,'RESTORE',skipped['items'][0]['revision']).json()
    assert restored['items'][0]['status']=='HELD' and restored['parent_status']=='CREATED'
    assert 'checkpoint' not in studio.get('job',job['id'])
    assert studio.store.all('audit')


def test_subtask_bulk_is_atomic_and_child_revision_independent(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0];endpoint=f"/api/v2/knowledge-jobs/{job['id']}/subtasks/commands"
    body={'command':'HOLD','targets':[{'index':1,'expected_revision':1},{'index':2,'expected_revision':99}]}
    assert client.post(endpoint,headers=headers(),json=body).status_code==409
    assert all(b['status']=='PENDING' for b in studio.get('job',job['id'])['batch_states'])
    body['targets'][1]['expected_revision']=1
    assert client.post(endpoint,headers=headers('developer'),json=body).status_code==200
    # Changing siblings and the parent revision does not invalidate untouched child #3.
    assert subtask_api(client,job,3,'HOLD',1).status_code==200
    body['targets']=[{'index':1,'expected_revision':2}]*2
    assert client.post(endpoint,headers=headers(),json=body).status_code==422
    assert subtask_api(client,job,1,'HOLD',1).status_code==409


def test_subtask_held_work_waits_without_false_completion(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0];calls=[]
    def call(*args):calls.append(next(iter(args[1])));return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    subtask_api(client,job,2,'HOLD',1)
    studio.command(job['id'],'START',job['revision']+1,'admin')
    while studio.process_one():pass
    waiting=studio.get('job',job['id'])
    assert waiting['status']=='WAITING_SUBTASKS' and waiting['batch_summary']['HELD']==1
    assert calls==['C0.java','C2.java'] and not studio.store.all('candidate')
    state=waiting['batch_states'][1]
    response=subtask_api(client,job,2,'RESUME',state['revision']).json()
    assert response['parent_status']=='QUEUED'
    while studio.process_one():pass
    done=studio.get('job',job['id'])
    assert done['status']=='SUCCEEDED' and calls==['C0.java','C2.java','C1.java']
    assert subtask_api(client,job,1,'RETRY',done['batch_states'][0]['revision']).status_code==409


def test_single_subtask_retry_keeps_parent_id_history_and_successes(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0];calls=[];state={'fail':True}
    def call(*args):
        path=next(iter(args[1]));calls.append(path)
        if path=='C1.java' and state['fail']:raise DomainError('MAPPING_EVIDENCE_REQUIRED',422)
        return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    while studio.process_one():pass
    partial=studio.get('job',job['id']);child=partial['batch_states'][1]
    assert child['status']=='INVALID' and partial['status']=='PARTIAL_SUCCEEDED'
    state['fail']=False
    response=subtask_api(client,job,2,'RETRY',child['revision']).json()
    assert response['job_id']==job['id'] and response['parent_status']=='QUEUED'
    assert subtask_api(client,job,2,'RETRY',child['revision']).status_code==409
    while studio.process_one():pass
    done=studio.get('job',job['id'])
    assert done['status']=='SUCCEEDED' and calls==['C0.java','C1.java','C2.java','C1.java']
    detail=client.get(f"/api/v2/knowledge-jobs/{job['id']}/subtasks/2",headers=headers()).json()
    assert len(detail['attempt_history'])==2 and detail['retry_cycles']==1 and detail['total_attempts']==2
    assert detail['control_history'][-1]['command']=='RETRY'
    assert detail['id']==child['id'] and len(studio.store.all('job'))==1


def test_subtask_unknown_requires_explicit_replay_and_cannot_skip(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0]
    def call(*args):
        if next(iter(args[1]))=='C1.java':raise DomainError('MODEL_TIMEOUT',504,{'delivery':'unknown'})
        return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    while studio.process_one():pass
    unknown=studio.get('job',job['id'])['batch_states'][1]
    assert subtask_api(client,job,2,'RETRY',unknown['revision']).json()['code']=='UNKNOWN_REPLAY_CONFIRMATION_REQUIRED'
    assert subtask_api(client,job,2,'SKIP',unknown['revision']).status_code==409
    assert subtask_api(client,job,2,'RETRY',unknown['revision'],include_unknown=True).status_code==200
    assert studio.get('job',job['id'])['batch_states'][1]['control_history'][-1]['unknown_replay_authorized']


def test_subtask_retry_terminal_global_failure_dispatches_only_selected(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0];calls=[]
    def fail(*args):raise DomainError('MODEL_AUTHENTICATION_FAILED',502)
    monkeypatch.setattr(studio.models,'call',fail)
    studio.command(job['id'],'START',job['revision'],'admin');studio.process_one()
    failed=studio.get('job',job['id']);assert failed['status']=='FAILED'
    def call(*args):calls.append(next(iter(args[1])));return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    assert subtask_api(client,job,1,'RETRY',failed['batch_states'][0]['revision']).status_code==200
    while studio.process_one():pass
    waiting=studio.get('job',job['id'])
    assert calls==['C0.java'] and waiting['status']=='WAITING_SUBTASKS'
    assert [b['status'] for b in waiting['batch_states']]==['SUCCEEDED','PENDING','PENDING']
    studio.command(job['id'],'RESUME',waiting['revision'],'admin')
    while studio.process_one():pass
    assert calls==['C0.java','C1.java','C2.java'] and studio.get('job',job['id'])['status']=='SUCCEEDED'


def test_subtask_skip_retains_coverage_gate_and_legacy_is_read_only(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0]
    assert subtask_api(client,job,2,'SKIP',1).status_code==200
    current=studio.get('job',job['id'])
    monkeypatch.setattr(studio.models,'call',output)
    studio.command(job['id'],'START',current['revision'],'admin')
    while studio.process_one():pass
    partial=studio.get('job',job['id'])
    assert partial['status']=='PARTIAL_SUCCEEDED' and partial['batch_summary']['SKIPPED']==1
    assert all(c['uncertainties'][-1].startswith('INCOMPLETE_EXTRACTION_COVERAGE:') for c in studio.store.all('candidate'))
    with studio.store.transaction() as conn:
        partial.pop('execution_policy');partial.pop('batch_states')
        partial['checkpoint']['completed_batches']=1
        partial['checkpoint']['output']['rules']=partial['checkpoint']['output']['rules'][:1]
        partial['extraction_plan']['completed_batches']=1
        partial.update(status='FAILED',error='MAPPING_EVIDENCE_REQUIRED',error_params={'batch':2})
        studio.store.update('job',partial,partial['revision'],conn)
    detail=client.get(f"/api/v2/knowledge-jobs/{job['id']}/subtasks/2",headers=headers()).json()
    assert detail['status']=='INVALID' and not detail['can_manage'] and detail['total_attempts'] is None
    assert subtask_api(client,job,2,'RETRY',detail['revision']).json()['code']=='SUBTASK_LEGACY_UPGRADE_REQUIRED'


def test_subtask_control_during_another_call_preserves_results(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0];calls=[]
    def call(*args):
        path=next(iter(args[1]));calls.append(path)
        if path=='C0.java':
            assert subtask_api(client,job,1,'HOLD',2).status_code==409  # in-flight call cannot be interrupted
            assert subtask_api(client,job,3,'HOLD',1).status_code==200
        return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    while studio.process_one():pass
    waiting=studio.get('job',job['id'])
    assert waiting['status']=='WAITING_SUBTASKS' and calls==['C0.java','C1.java']
    assert len(waiting['checkpoint']['output']['rules'])==2 and waiting['batch_states'][2]['status']=='HELD'


def test_subtask_backoff_hold_resume_retains_delay_budget_and_cancel_guard(env,model_server,monkeypatch):
    studio,job,_=retry_fixture(env,model_server,monkeypatch)
    client=env[0]
    def call(*args):raise DomainError('MODEL_RATE_LIMITED',502,{'retry_after_seconds':30})
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin');studio.process_one()
    waiting=studio.get('job',job['id']);child=waiting['batch_states'][0]
    held=subtask_api(client,job,1,'HOLD',child['revision']).json()['items'][0]
    resumed=subtask_api(client,job,1,'RESUME',held['revision']).json()['items'][0]
    assert resumed['status']=='RETRY_WAIT' and resumed['attempts']==1 and resumed['next_retry_at']==child['next_retry_at']
    assert not studio.process_one()
    current=studio.get('job',job['id']);studio.command(job['id'],'CANCEL',current['revision'],'admin')
    assert subtask_api(client,job,1,'HOLD',resumed['revision']).json()['code']=='SUBTASK_PARENT_NOT_AVAILABLE'


def test_subtask_bulk_unknown_confirmation_is_atomic(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0]
    def call(*args):
        path=next(iter(args[1]))
        if path=='C1.java':raise DomainError('EVIDENCE_QUOTE_INVALID',422)
        if path=='C2.java':raise DomainError('MODEL_TIMEOUT',504,{'delivery':'unknown'})
        return output(*args)
    monkeypatch.setattr(studio.models,'call',call)
    studio.command(job['id'],'START',job['revision'],'admin')
    while studio.process_one():pass
    partial=studio.get('job',job['id'])
    endpoint=f"/api/v2/knowledge-jobs/{job['id']}/subtasks/commands"
    body={'command':'RETRY','targets':[{'index':i+1,'expected_revision':partial['batch_states'][i]['revision']} for i in (1,2)]}
    denied=client.post(endpoint,headers=headers(),json=body)
    assert denied.status_code==409 and denied.json()['code']=='UNKNOWN_REPLAY_CONFIRMATION_REQUIRED'
    assert [b['status'] for b in studio.get('job',job['id'])['batch_states']]==['SUCCEEDED','INVALID','UNKNOWN']
    body['include_unknown']=True
    accepted=client.post(endpoint,headers=headers(),json=body)
    assert accepted.status_code==200 and studio.get('job',job['id'])['dispatch_indices']==[2,3]
    assert client.get('/api/v2/extraction-subtasks',headers=headers(),params={'job_id':job['id'],'status':'ERRORS'}).json()['total']==0


def test_subtask_unexpected_failure_after_dispatch_is_unknown(env,model_server,monkeypatch):
    studio,job,_=retry_fixture(env,model_server,monkeypatch)
    def crash(*args):raise RuntimeError('test-only adapter interruption')
    monkeypatch.setattr(studio.models,'call',crash)
    studio.command(job['id'],'START',job['revision'],'admin');studio.process_one()
    failed=studio.get('job',job['id'])
    assert failed['status']=='FAILED' and failed['batch_states'][0]['status']=='UNKNOWN'
    denied=subtask_api(env[0],job,1,'RETRY',failed['batch_states'][0]['revision'])
    assert denied.json()['code']=='UNKNOWN_REPLAY_CONFIRMATION_REQUIRED'


def test_parent_recovery_retains_explicitly_skipped_scopes(env,model_server,monkeypatch):
    studio,job,output=retry_fixture(env,model_server,monkeypatch)
    client=env[0]
    subtask_api(client,job,2,'SKIP',1)
    current=studio.get('job',job['id'])
    monkeypatch.setattr(studio.models,'call',output)
    studio.command(job['id'],'START',current['revision'],'admin')
    while studio.process_one():pass
    partial=studio.get('job',job['id'])
    retry=studio.retry_job(job['id'],partial['revision'],'admin')
    assert retry['batch_states'][1]['status']=='SKIPPED'
    assert retry['batch_states'][0]['id']!=partial['batch_states'][0]['id']
