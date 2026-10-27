import io
import json
import os
import shutil
import stat
import subprocess
import tempfile
import zipfile
from pathlib import Path, PurePosixPath
from urllib.parse import urlparse
from .contracts import DomainError, digest, identity

EXTENSIONS = {".java", ".md", ".txt", ".sql", ".xml"}
EXCLUDED = {".git", ".env", "node_modules", "target", "build", ".idea", ".venv"}


def run(args, **kwargs):
    try:
        return subprocess.run(args, check=True, capture_output=True, timeout=90, **kwargs).stdout
    except subprocess.TimeoutExpired:
        raise DomainError("SOURCE_TIMEOUT", 504)
    except (subprocess.CalledProcessError, FileNotFoundError):
        raise DomainError("SOURCE_OPERATION_FAILED", 422)


def safe_path(name):
    if "\\" in name or "\x00" in name:
        raise DomainError("INVALID_UPLOAD_PATH")
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or not path.parts or ":" in name:
        raise DomainError("INVALID_UPLOAD_PATH")
    if any(part in EXCLUDED for part in path.parts) or path.suffix.lower() not in EXTENSIONS:
        return None
    return str(path)


class SourceService:
    def __init__(self, settings, store):
        self.settings, self.store = settings, store

    def upload_zip(self, content, name):
        if len(content) > self.settings.max_upload:
            raise DomainError("UPLOAD_TOO_LARGE", 413)
        files, total, scanned = {}, 0, 0
        try:
            with zipfile.ZipFile(io.BytesIO(content)) as z:
                for entry in z.infolist():
                    if entry.is_dir():
                        continue
                    scanned += 1
                    if scanned > self.settings.max_files:
                        raise DomainError("TOO_MANY_FILES", 413)
                    if stat.S_ISLNK(entry.external_attr >> 16):
                        raise DomainError("UPLOAD_SYMLINK_FORBIDDEN")
                    path = safe_path(entry.filename)
                    if path is None:
                        continue
                    if entry.file_size > self.settings.max_file:
                        raise DomainError("FILE_TOO_LARGE", 413)
                    total += entry.file_size
                    if total > self.settings.max_upload:
                        raise DomainError("UPLOAD_TOO_LARGE", 413)
                    if path in files:
                        raise DomainError("DUPLICATE_UPLOAD_PATH")
                    files[path] = z.read(entry)
        except (zipfile.BadZipFile, RuntimeError, NotImplementedError):
            raise DomainError("INVALID_ZIP")
        return self.persist(files, name, "UPLOAD", None, None)

    def persist(self, files, name, mode, commit, origin):
        if not files or not any(p.endswith(".java") for p in files):
            raise DomainError("NO_JAVA_FILES", 422)
        for value in files.values():
            try:
                value.decode("utf-8")
            except UnicodeDecodeError:
                raise DomainError("UTF8_SOURCE_REQUIRED", 422)
        manifest = [{"path": p, "sha256": __import__("hashlib").sha256(v).hexdigest(), "size": len(v)} for p, v in sorted(files.items())]
        content_hash = digest(manifest)
        repo = identity("repo", origin or name)
        sid = identity("snapshot", [repo, mode, commit, content_hash])
        previous = self.store.get("snapshot", sid)
        if previous:
            return previous
        target = self.settings.data_dir / "snapshots" / sid
        target.mkdir(parents=True, exist_ok=True)
        for path, value in files.items():
            dest = target / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(value)
        result = {"id": sid, "repository_id": repo, "name": name, "mode": mode, "commit": commit, "origin": origin, "content_hash": content_hash, "scope_manifest_hash": digest({"extensions": sorted(EXTENSIONS), "excluded": sorted(EXCLUDED)}), "files": manifest, "status": "SNAPSHOT_READY"}
        with self.store.transaction() as conn:
            # Concurrent identical uploads resolve to the original immutable record.
            existing = self.store.get("snapshot", sid, conn)
            return existing or self.store.insert("snapshot", result, conn)

    def clone(self, url, ref, name):
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.hostname not in self.settings.git_hosts or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.port not in (None, 443):
            raise DomainError("GIT_URL_NOT_ALLOWED", 403)
        if not ref or ref.startswith("-") or len(ref) > 150:
            raise DomainError("INVALID_GIT_REF")
        env = {**os.environ, "GIT_TERMINAL_PROMPT": "0", "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull, "GIT_ALLOW_PROTOCOL": "https"}
        # No checkout, submodules, LFS smudge filters or repository build scripts.
        with tempfile.TemporaryDirectory(dir=self.settings.data_dir) as temp:
            run(["git", "-c", "http.followRedirects=false", "init", "--bare", temp], env=env)
            run(["git", "-c", "http.followRedirects=false", "-C", temp, "fetch", "--depth=1", "--", url, ref], env=env)
            commit = run(["git", "-C", temp, "rev-parse", "FETCH_HEAD^{commit}"], env=env).decode().strip()
            tree = run(["git", "-C", temp, "ls-tree", "-rz", "--full-tree", commit], env=env)
            files, total = {}, 0
            for item in tree.split(b"\0"):
                if not item:
                    continue
                meta, raw_path = item.split(b"\t", 1)
                mode, kind, oid = meta.decode().split()
                path = safe_path(raw_path.decode("utf-8"))
                if path is None or kind != "blob" or mode == "120000":
                    continue
                size = int(run(["git", "-C", temp, "cat-file", "-s", oid], env=env))
                total += size
                if size > self.settings.max_file or total > self.settings.max_upload or len(files) >= self.settings.max_files:
                    raise DomainError("REPOSITORY_SCOPE_TOO_LARGE", 413)
                files[path] = run(["git", "-C", temp, "cat-file", "blob", oid], env=env)
            return self.persist(files, name, "COMMIT", commit, url)

    def read(self, snapshot, path):
        entry = next((f for f in snapshot["files"] if f["path"] == path), None)
        if not entry:
            raise DomainError("EVIDENCE_NOT_IN_SNAPSHOT", 422)
        value = (self.settings.data_dir / "snapshots" / snapshot["id"] / path).read_bytes()
        if __import__("hashlib").sha256(value).hexdigest() != entry["sha256"]:
            raise DomainError("EVIDENCE_HASH_MISMATCH", 409)
        return value.decode("utf-8")

    def analyze(self, snapshot, engine=None):
        if engine == "joern" or engine is None and self.settings.codegraph_url:
            if not self.settings.codegraph_url:
                raise DomainError("CODEGRAPH_NOT_CONFIGURED", 409)
            from .codegraph import analyze
            return analyze(self,snapshot)
        from .config import ROOT
        source = ROOT / "analyzers/javac/SourceAnalyzer.java"
        version = digest({"source": source.read_text(), "normalizer": Path(__file__).read_text(), "contract": "m1-code-facts-1", "java": run(["java", "--version"]).decode()})
        pid = identity("projection", [snapshot["id"], version])
        previous = self.store.get("projection", pid)
        if previous:
            return previous
        build = ROOT / "analyzers/javac/build"
        build.mkdir(exist_ok=True)
        run(["javac", "-d", str(build), str(source)])
        paths = [f["path"] for f in snapshot["files"] if f["path"].endswith(".java")]
        if len(paths) > 200:
            raise DomainError("JAVA_ANALYSIS_LIMIT", 422, {"max_java_files": 200})
        result = json.loads(run(["java", "-Xmx512m", "-cp", str(build), "SourceAnalyzer", str(self.settings.data_dir / "snapshots" / snapshot["id"]), *paths]).decode())
        entities, mapping, edges = [], {}, []
        for path in paths:
            key = "file:" + path
            mapping[key] = identity("entity", [snapshot["id"], pid, key])
            entities.append({"id": mapping[key], "local_key": key, "kind": "File", "name": path.rsplit("/", 1)[-1], "signature": path, "path": path, "start_line": 1, "end_line": len(self.read(snapshot, path).splitlines()), "stable_key": identity("logical", [snapshot["repository_id"], key]), "snapshot_id": snapshot["id"], "projection_id": pid})
        for ent in result["entities"]:
            # Compiler may synthesize implicit constructors with no end position; do not invent source.
            if ent["end_line"] < ent["start_line"]:
                continue
            eid = identity("entity", [snapshot["id"], pid, ent["kind"], ent["key"]])
            mapping[ent["key"]] = eid
            entities.append({**ent, "id": eid, "local_key": ent["key"], "stable_key": identity("logical", [snapshot["repository_id"], ent["path"], ent["kind"], ent["signature"]]), "snapshot_id": snapshot["id"], "projection_id": pid})
        def edge(a, b, kind, extra=None):
            edges.append({"id": identity("edge", [pid, a, b, kind, extra]), "source": a, "target": b, "type": kind, "projection_id": pid, "snapshot_id": snapshot["id"], **(extra or {})})
        for ent in entities:
            if ent["kind"] != "File":
                parent = mapping.get(ent.get("parent")) or mapping.get("file:" + ent["path"])
                edge(parent, ent["id"], "DECLARES", {"resolution_status": "RESOLVED"})
        unresolved = []
        for call in result["calls"]:
            if call["target_key"] in mapping and call["from"] in mapping:
                edge(mapping[call["from"]], mapping[call["target_key"]], "CALLS", {"resolution_status": "RESOLVED", "dispatch_semantics": "STATIC_DECLARATION_ONLY", "path": call["path"], "start_line": call["start_line"]})
            else:
                unresolved.append(call)
        projection = {"id": pid, "snapshot_id": snapshot["id"], "analyzer": "javac-ast-1", "analyzer_revision": version, "jdk": result["jdk"], "entities": entities, "edges": edges, "unresolved_calls": unresolved, "diagnostics": result["diagnostics"], "limitations": result["limitations"]}
        with self.store.transaction() as conn:
            return self.store.insert("projection", projection, conn)
