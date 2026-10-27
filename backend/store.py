import json
from contextlib import contextmanager
from datetime import datetime, timezone
from sqlalchemy import create_engine, text
from sqlalchemy.pool import StaticPool


def now():
    return datetime.now(timezone.utc).isoformat()


class Store:
    """The relational record is authoritative; immutable graph projections are rebuildable."""

    def __init__(self, url):
        args = {"connect_args": {"check_same_thread": False, "timeout": 30}} if url.startswith("sqlite:") else {}
        if url == "sqlite://":
            args["poolclass"] = StaticPool
        self.engine = create_engine(url, pool_pre_ping=True, **args)
        with self.engine.begin() as c:
            if self.engine.dialect.name == "postgresql":
                c.execute(text("SELECT pg_advisory_xact_lock(72843301)"))
            c.execute(text("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at VARCHAR(64) NOT NULL)"))
            c.execute(text("CREATE TABLE IF NOT EXISTS records (kind VARCHAR(50) NOT NULL, id VARCHAR(128) NOT NULL, revision INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind,id), CHECK(revision >= 1))"))
            if not c.execute(text("SELECT version FROM schema_migrations WHERE version=1")).first():
                c.execute(text("INSERT INTO schema_migrations(version,applied_at) VALUES(1,:at)"), {"at": now()})

    @contextmanager
    def transaction(self):
        with self.engine.begin() as conn:
            yield conn

    def get(self, kind, key, conn=None):
        if conn is None:
            with self.engine.connect() as c:
                return self.get(kind, key, c)
        row = conn.execute(text("SELECT payload FROM records WHERE kind=:k AND id=:i"), {"k": kind, "i": key}).first()
        return json.loads(row[0]) if row else None

    def all(self, kind, conn=None):
        if conn is None:
            with self.engine.connect() as c:
                return self.all(kind, c)
        rows = conn.execute(text("SELECT payload FROM records WHERE kind=:k ORDER BY id"), {"k": kind}).all()
        return [json.loads(row[0]) for row in rows]

    def insert(self, kind, value, conn):
        value = dict(value)
        value.setdefault("revision", 1)
        value.setdefault("created_at", now())
        conn.execute(text("INSERT INTO records(kind,id,revision,payload) VALUES(:k,:i,:r,:p)"), {"k": kind, "i": value["id"], "r": value["revision"], "p": json.dumps(value, ensure_ascii=False)})
        return value

    def update(self, kind, value, expected, conn):
        value = dict(value)
        value["revision"] = expected + 1
        result = conn.execute(text("UPDATE records SET payload=:p,revision=:r WHERE kind=:k AND id=:i AND revision=:e"), {"p": json.dumps(value, ensure_ascii=False), "r": value["revision"], "k": kind, "i": value["id"], "e": expected})
        if result.rowcount != 1:
            raise Conflict("REVISION_CONFLICT")
        return value


class Conflict(Exception):
    pass

