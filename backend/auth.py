"""Password accounts and revocable browser sessions; API role tokens remain supported."""
import hashlib
import secrets
import threading
import time
from sqlalchemy.exc import IntegrityError
from .contracts import DomainError


class AuthService:
    SESSION_SECONDS = 8 * 60 * 60
    ITERATIONS = 600_000

    def __init__(self, store):
        self.store = store
        self.lock = threading.Lock()
        self.failures = {}
        if not store.get("user", "admin"):
            account = {"id": "admin", "role": "admin", "password_hash": self.hash_password("admin123")}
            try:
                with store.transaction() as conn:
                    if not store.get("user", "admin", conn):
                        store.insert("user", account, conn)
            except IntegrityError:
                # Another API process initialized the same account first.
                if not store.get("user", "admin"):
                    raise

    @classmethod
    def hash_password(cls, password):
        salt = secrets.token_hex(16)
        result = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), cls.ITERATIONS).hex()
        return f"pbkdf2_sha256${cls.ITERATIONS}${salt}${result}"

    @staticmethod
    def check_password(password, encoded):
        _, iterations, salt, expected = encoded.split("$")
        actual = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), int(iterations)).hex()
        return secrets.compare_digest(actual, expected)

    @staticmethod
    def session_key(token):
        return hashlib.sha256(token.encode()).hexdigest()

    def login(self, username, password, peer):
        with self.lock:
            now = time.time()
            self.failures = {key: entry for key, entry in self.failures.items() if entry[1] > now}
            count, until = self.failures.get(peer, (0, now + 60))
            if count >= 5:
                raise DomainError("LOGIN_RATE_LIMITED", 429)
            account = self.store.get("user", username)
            # Use a real hash even for unknown names to avoid fast username probing.
            encoded = (account or self.store.get("user", "admin"))["password_hash"]
            valid = self.check_password(password, encoded)
            if not account or not valid:
                self.failures[peer] = (count + 1, until)
                raise DomainError("INVALID_LOGIN", 401)
            self.failures.pop(peer, None)
            token = secrets.token_urlsafe(32)
            with self.store.transaction() as conn:
                self.store.insert("session", {"id": self.session_key(token), "username": username, "account_revision": account["revision"], "expires_at": now + self.SESSION_SECONDS, "revoked": False}, conn)
            return {"access_token": token, "token_type": "bearer", "expires_in": self.SESSION_SECONDS}

    def account(self, token):
        session = self.store.get("session", self.session_key(token)) if token else None
        if not session or session["revoked"] or session["expires_at"] <= time.time():
            return None
        account = self.store.get("user", session["username"])
        return account if account and account["revision"] == session["account_revision"] else None

    def logout(self, token):
        with self.store.transaction() as conn:
            session = self.store.get("session", self.session_key(token), conn)
            if session and not session["revoked"]:
                self.store.update("session", {**session, "revoked": True}, session["revision"], conn)

    def change_password(self, token, current_password, new_password):
        account = self.account(token)
        if not account:
            raise DomainError("PASSWORD_SESSION_REQUIRED", 401)
        if not self.check_password(current_password, account["password_hash"]):
            raise DomainError("CURRENT_PASSWORD_INVALID", 400)
        if secrets.compare_digest(current_password.encode(), new_password.encode()):
            raise DomainError("PASSWORD_UNCHANGED", 422)
        with self.store.transaction() as conn:
            self.store.update("user", {**account, "password_hash": self.hash_password(new_password)}, account["revision"], conn)
        return {"changed": True}
