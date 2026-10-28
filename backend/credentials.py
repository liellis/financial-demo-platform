"""Local development credential vault; public records contain opaque references only."""
import os
import re
import uuid
from cryptography.fernet import Fernet, InvalidToken
from .contracts import DomainError
from .filelock import file_lock


class CredentialVault:
    def __init__(self, data_dir):
        self.root = data_dir / "credentials"

    def cipher(self):
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(self.root, 0o700)
        # API and Worker can initialize concurrently. Never replace an existing key.
        fd = os.open(self.root / ".lock", os.O_RDWR | os.O_CREAT, 0o600)
        with os.fdopen(fd, "r+") as lock, file_lock(lock):
            key_file = self.root / "master.key"
            if not key_file.exists():
                if any(self.root.glob("*.enc")):
                    raise DomainError("CREDENTIAL_STORE_UNAVAILABLE", 503)
                key_fd = os.open(key_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                with os.fdopen(key_fd, "wb") as out:
                    out.write(Fernet.generate_key())
            os.chmod(key_file, 0o600)
            try:
                return Fernet(key_file.read_bytes())
            except (ValueError, OSError):
                raise DomainError("CREDENTIAL_STORE_UNAVAILABLE", 503)

    def put(self, value):
        reference = "credential_" + uuid.uuid4().hex
        ciphertext = self.cipher().encrypt(value.encode("utf-8"))
        fd = os.open(self.root / (reference + ".enc"), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as out:
            out.write(ciphertext)
        return reference

    def get(self, reference):
        if not re.fullmatch(r"credential_[0-9a-f]{32}", reference or ""):
            raise DomainError("INVALID_CREDENTIAL_REFERENCE", 422)
        try:
            return self.cipher().decrypt((self.root / (reference + ".enc")).read_bytes()).decode("utf-8")
        except (OSError, InvalidToken, UnicodeDecodeError):
            raise DomainError("CREDENTIAL_STORE_UNAVAILABLE", 503)
