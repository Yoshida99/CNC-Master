from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
import re
from dataclasses import dataclass


class SecurityConfigError(RuntimeError):
    pass


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _b64url_decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


@dataclass(frozen=True)
class SessionIdentity:
    user_id: str
    expires_at: int


class TokenStore:
    def __init__(
        self,
        tokens: dict[str, str] | None = None,
        token_hashes: dict[str, str] | None = None,
    ) -> None:
        self._tokens = {
            str(user_id).strip(): str(token).strip()
            for user_id, token in (tokens or {}).items()
            if str(user_id).strip() and str(token).strip()
        }
        self._token_hashes = {
            str(user_id).strip(): str(token_hash).strip().lower()
            for user_id, token_hash in (token_hashes or {}).items()
            if str(user_id).strip() and len(str(token_hash).strip()) == 64
        }

    @property
    def configured(self) -> bool:
        return bool(self._tokens or self._token_hashes)

    def authenticate(self, token: str | None) -> str | None:
        candidate = (token or "").strip()
        if not candidate:
            return None

        for user_id, expected in self._tokens.items():
            if hmac.compare_digest(candidate, expected):
                return user_id

        candidate_hash = hashlib.sha256(candidate.encode("utf-8")).hexdigest()
        for user_id, expected_hash in self._token_hashes.items():
            if hmac.compare_digest(candidate_hash, expected_hash):
                return user_id
        return None


class SessionSigner:
    def __init__(self, secret: str, ttl_seconds: int = 21600) -> None:
        self._secret = secret.encode("utf-8")
        self._ttl_seconds = max(300, ttl_seconds)

    @property
    def configured(self) -> bool:
        return len(self._secret) >= 32

    def issue(self, user_id: str, now: int | None = None) -> tuple[str, int]:
        if not self.configured:
            raise SecurityConfigError("CNC_SESSION_SECRET must be at least 32 characters")
        current = int(time.time() if now is None else now)
        expires = current + self._ttl_seconds
        payload = {
            "sub": user_id,
            "iat": current,
            "exp": expires,
        }
        body = _b64url(json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8"))
        signature = _b64url(hmac.new(self._secret, body.encode("ascii"), hashlib.sha256).digest())
        return f"{body}.{signature}", expires

    def verify(self, token: str, now: int | None = None) -> SessionIdentity | None:
        if not self.configured:
            return None
        try:
            body, signature = token.split(".", 1)
            expected = _b64url(hmac.new(self._secret, body.encode("ascii"), hashlib.sha256).digest())
            if not hmac.compare_digest(signature, expected):
                return None
            payload = json.loads(_b64url_decode(body))
            user_id = str(payload["sub"]).strip()
            expires = int(payload["exp"])
            current = int(time.time() if now is None else now)
            if not user_id or expires <= current:
                return None
            return SessionIdentity(user_id=user_id, expires_at=expires)
        except (ValueError, KeyError, TypeError, json.JSONDecodeError):
            return None


def parse_client_tokens(raw_json: str, legacy_token: str = "") -> dict[str, str]:
    raw = raw_json.strip()
    if raw:
        try:
            value = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise SecurityConfigError("CNC_CLIENT_TOKENS_JSON is invalid JSON") from exc
        if not isinstance(value, dict):
            raise SecurityConfigError("CNC_CLIENT_TOKENS_JSON must be a JSON object")
        return {str(key): str(item) for key, item in value.items()}

    if legacy_token.strip():
        return {"default": legacy_token.strip()}
    return {}


def parse_client_token_hashes(raw_json: str) -> dict[str, str]:
    raw = raw_json.strip()
    if not raw:
        return {}
    try:
        value = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SecurityConfigError("CNC_CLIENT_TOKEN_HASHES_JSON is invalid JSON") from exc
    if not isinstance(value, dict):
        raise SecurityConfigError("CNC_CLIENT_TOKEN_HASHES_JSON must be a JSON object")
    return {str(key): str(item) for key, item in value.items()}


_INSTALL_ID_RE = re.compile(r"^[A-Za-z0-9._:-]{16,128}$")


def anonymous_user_id(installation_id: str | None) -> str | None:
    value = (installation_id or "").strip()
    if not _INSTALL_ID_RE.fullmatch(value):
        return None
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:24]
    return f"anon:{digest}"
