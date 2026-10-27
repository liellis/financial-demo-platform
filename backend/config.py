import json
import os
import secrets
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@dataclass
class Settings:
    data_dir: Path = field(default_factory=lambda: Path(os.getenv("BOS_DATA_DIR", ROOT / ".data")))
    database_url: str = field(default_factory=lambda: os.getenv("BOS_DATABASE_URL", ""))
    git_hosts: tuple = field(default_factory=lambda: tuple(os.getenv("BOS_GIT_HOSTS", "github.com,gitlab.com,gitee.com").split(",")))
    model_hosts: tuple = field(default_factory=lambda: tuple(h.strip() for h in os.getenv("BOS_MODEL_HOSTS", "").split(",") if h.strip()))
    neo4j_uri: str = field(default_factory=lambda: os.getenv("BOS_NEO4J_URI", ""))
    neo4j_user: str = field(default_factory=lambda: os.getenv("BOS_NEO4J_USER", "neo4j"))
    neo4j_password: str = field(default_factory=lambda: os.getenv("BOS_NEO4J_PASSWORD", ""))
    codegraph_url: str = field(default_factory=lambda: os.getenv("BOS_CODEGRAPH_URL", ""))
    codegraph_token: str = field(default_factory=lambda: os.getenv("BOS_CODEGRAPH_TOKEN", ""))
    codegraph_timeout: int = 1200
    baseline_public_key_file: str = field(default_factory=lambda: os.getenv('BOS_BASELINE_PUBLIC_KEY_FILE',''))
    max_upload: int = 25 * 1024 * 1024
    max_files: int = 1000
    max_file: int = 1024 * 1024
    tokens: dict = field(default_factory=dict)

    def __post_init__(self):
        self.data_dir = self.data_dir.resolve()
        self.data_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.database_url = self.database_url or f"sqlite:///{self.data_dir / 'studio.db'}"
        if not self.tokens:
            token_file = self.data_dir / "access-tokens.json"
            if token_file.exists():
                self.tokens = json.loads(token_file.read_text())
            else:
                self.tokens = {r: secrets.token_urlsafe(32) for r in ("admin", "reviewer", "developer", "agent")}
                fd = os.open(token_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(fd, "w") as out:
                    json.dump(self.tokens, out)
        self.tokens = {role: os.getenv(f"BOS_{role.upper()}_TOKEN", token) for role, token in self.tokens.items()}

