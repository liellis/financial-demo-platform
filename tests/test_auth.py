import json
import time
from fastapi.testclient import TestClient
from backend.api import create_app
from backend.config import Settings


def setup(tmp_path):
    settings = Settings(data_dir=tmp_path)
    app = create_app(settings)
    return app, settings


def login(client, username="admin", password="admin123"):
    return client.post("/api/v2/auth/login", json={"username": username, "password": password})


def bearer(response):
    return {"Authorization": "Bearer " + response.json()["access_token"]}


def test_login_permissions_logout_and_expiration(tmp_path):
    app, settings = setup(tmp_path)
    with TestClient(app) as client:
        assert client.get("/api/v2/me").status_code == 401
        assert login(client, password="wrong").json()["code"] == "INVALID_LOGIN"
        assert login(client, username="absent").json()["code"] == "INVALID_LOGIN"
        response = login(client)
        assert response.status_code == 200
        headers = bearer(response)
        me = client.get("/api/v2/me", headers=headers).json()
        assert me["role"] == me["username"] == "admin" and me["auth_method"] == "password"
        assert client.get("/api/v2/model-connections", headers=headers).status_code == 200
        assert client.post("/api/v2/auth/logout", headers=headers).status_code == 200
        assert client.get("/api/v2/me", headers=headers).status_code == 401
        assert client.post("/api/v2/auth/logout", headers=headers).status_code == 401
        response = login(client)
        key = app.state.auth.session_key(response.json()["access_token"])
        store = app.state.studio.store
        with store.transaction() as conn:
            session = store.get("session", key, conn)
            store.update("session", {**session, "expires_at": time.time() - 1}, session["revision"], conn)
        assert client.get("/api/v2/me", headers=bearer(response)).status_code == 401
        api_headers = {"Authorization": "Bearer " + settings.tokens["reviewer"]}
        assert client.get("/api/v2/me", headers=api_headers).json()["role"] == "reviewer"
        assert client.put("/api/v2/settings/extraction", headers=api_headers, json={"max_source_lines": 100, "expected_revision": 0}).status_code == 403


def test_password_change_persists_and_revokes_all_sessions(tmp_path):
    app, settings = setup(tmp_path)
    with TestClient(app) as client:
        first, second = login(client), login(client)
        headers = bearer(first)
        def change(current, new):
            return client.post("/api/v2/auth/password", headers=headers, json={"current_password": current, "new_password": new})
        assert change("wrong", "changed-password").json()["code"] == "CURRENT_PASSWORD_INVALID"
        assert change("admin123", "admin123").json()["code"] == "PASSWORD_UNCHANGED"
        result = change("admin123", "tiny")
        assert result.status_code == 422 and "tiny" not in result.text and "admin123" not in result.text
        assert change("admin123", "changed-password").status_code == 200
        for result in (first, second):
            assert client.get("/api/v2/me", headers=bearer(result)).status_code == 401
        assert login(client).status_code == 401
        fresh = login(client, password="changed-password")
        assert fresh.status_code == 200
        dump = json.dumps(app.state.studio.store.all("user") + app.state.studio.store.all("session"))
        assert "changed-password" not in dump and "admin123" not in dump
        assert fresh.json()["access_token"] not in dump
    with TestClient(create_app(settings)) as client:
        assert client.get("/api/v2/me", headers=bearer(fresh)).status_code == 200
        assert login(client, password="changed-password").status_code == 200
        assert login(client).status_code == 401


def test_login_throttle_validation_and_role_is_not_client_controlled(tmp_path):
    app, _ = setup(tmp_path)
    with TestClient(app) as client:
        result = client.post("/api/v2/auth/login", json={"username": "admin", "password": "admin123", "role": "admin"})
        assert result.status_code == 422 and "admin123" not in result.text
        for _ in range(5):
            assert login(client, password="bad").status_code == 401
        assert login(client).status_code == 429
        app.state.auth.failures = {"testclient": (5, time.time() - 1)}
        assert login(client).status_code == 200
        with app.state.studio.store.transaction() as conn:
            app.state.studio.store.insert("user", {"id": "review-test", "role": "reviewer", "password_hash": app.state.auth.hash_password("test-password")}, conn)
        headers = bearer(login(client, "review-test", "test-password"))
        assert client.get("/api/v2/me", headers=headers).json()["role"] == "reviewer"
        assert client.put("/api/v2/settings/extraction", headers=headers, json={"expected_revision": 0}).status_code == 403
