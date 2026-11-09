from __future__ import annotations

import hmac
import json
import logging
import os
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request, Response, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

try:
    from .broker import WorkerBroker
    from .rate_limit import SlidingWindowRateLimiter
    from .security import SecurityConfigError, SessionSigner, TokenStore, parse_client_tokens
except ImportError:
    from broker import WorkerBroker
    from rate_limit import SlidingWindowRateLimiter
    from security import SecurityConfigError, SessionSigner, TokenStore, parse_client_tokens


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("cnc.gateway")

MAX_BODY_BYTES = int(os.getenv("CNC_MAX_BODY_BYTES", "65536"))
QUEUE_MAX = int(os.getenv("CNC_QUEUE_MAX", "8"))
REQUEST_TIMEOUT_SECONDS = float(os.getenv("CNC_REQUEST_TIMEOUT_SECONDS", "240"))
RATE_LIMIT = int(os.getenv("CNC_RATE_LIMIT", "10"))
RATE_WINDOW_SECONDS = int(os.getenv("CNC_RATE_WINDOW_SECONDS", "60"))
SESSION_TTL_SECONDS = int(os.getenv("CNC_SESSION_TTL_SECONDS", "21600"))
ALLOW_LEGACY_TOKEN = os.getenv("CNC_ALLOW_LEGACY_TOKEN", "false").strip().lower() in {"1", "true", "yes"}

try:
    client_tokens = parse_client_tokens(
        os.getenv("CNC_CLIENT_TOKENS_JSON", ""),
        os.getenv("CNC_APP_TOKEN", ""),
    )
except SecurityConfigError:
    logger.exception("client_token_configuration_invalid")
    client_tokens = {}

token_store = TokenStore(client_tokens)
session_signer = SessionSigner(os.getenv("CNC_SESSION_SECRET", ""), SESSION_TTL_SECONDS)
worker_token = os.getenv("CNC_WORKER_TOKEN", "").strip()

rate_limiter = SlidingWindowRateLimiter(RATE_LIMIT, RATE_WINDOW_SECONDS)
auth_rate_limiter = SlidingWindowRateLimiter(max(5, RATE_LIMIT), RATE_WINDOW_SECONDS)
broker = WorkerBroker(queue_max=QUEUE_MAX, request_timeout_seconds=REQUEST_TIMEOUT_SECONDS)

app = FastAPI(title="CNC Master AI Gateway", version="0.3.0-dev")


class MaxBodySizeMiddleware:
    def __init__(self, app: Any, max_body_bytes: int) -> None:
        self.app = app
        self.max_body_bytes = max_body_bytes

    async def __call__(self, scope: dict[str, Any], receive: Any, send: Any) -> None:
        if scope.get("type") != "http":
            await self.app(scope, receive, send)
            return

        content_length = None
        for key, value in scope.get("headers", []):
            if key.lower() == b"content-length":
                try:
                    content_length = int(value)
                except ValueError:
                    content_length = None
                break

        if content_length is not None and content_length > self.max_body_bytes:
            response = Response("Request body too large", status_code=413)
            await response(scope, receive, send)
            return

        received = 0

        async def limited_receive() -> dict[str, Any]:
            nonlocal received
            message = await receive()
            if message.get("type") == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_body_bytes:
                    raise HTTPException(status_code=413, detail="Request body too large")
            return message

        await self.app(scope, limited_receive, send)


app.add_middleware(MaxBodySizeMiddleware, max_body_bytes=MAX_BODY_BYTES)


class CncGenerateRequest(BaseModel):
    message: str = Field(min_length=1, max_length=6000)
    machine: str = Field(default="", max_length=500)
    control: str = Field(default="", max_length=300)
    material: str = Field(default="", max_length=500)
    stock: str = Field(default="", max_length=1000)
    tools: str = Field(default="", max_length=2000)
    workholding: str = Field(default="", max_length=1000)
    zero_point: str = Field(default="", max_length=1000)
    current_code: str = Field(default="", max_length=20000)


class CncGenerateResponse(BaseModel):
    answer: str
    code: str = ""
    warnings: list[str] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)


class SessionResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_at: int
    expires_in: int


def _client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "").split(",", 1)[0].strip()
    if forwarded:
        return forwarded[:100]
    return request.client.host[:100] if request.client else "unknown"


def _authenticate_session(
    authorization: str | None,
    legacy_token: str | None,
) -> str:
    if authorization:
        scheme, _, value = authorization.partition(" ")
        if scheme.lower() == "bearer" and value:
            identity = session_signer.verify(value.strip())
            if identity is not None:
                return identity.user_id

    if ALLOW_LEGACY_TOKEN:
        user_id = token_store.authenticate(legacy_token)
        if user_id is not None:
            return user_id

    raise HTTPException(status_code=401, detail="CNC Master session is invalid or expired")


def _enforce_rate_limit(user_id: str) -> None:
    allowed, retry_after = rate_limiter.allow(user_id)
    if not allowed:
        raise HTTPException(
            status_code=429,
            detail="Too many AI requests",
            headers={"Retry-After": str(retry_after)},
        )


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "ok": True,
        "auth_configured": token_store.configured and session_signer.configured,
        "worker_auth_configured": bool(worker_token),
        "worker_connected": broker.connected,
        "worker_id": broker.worker_id if broker.connected else "",
        "queue_depth": broker.queue_depth,
        "queue_max": broker.queue_max,
    }


@app.post("/v1/auth/session", response_model=SessionResponse)
async def create_session(
    request: Request,
    x_cnc_app_token: str | None = Header(default=None),
) -> SessionResponse:
    ip = _client_ip(request)
    allowed, retry_after = auth_rate_limiter.allow(f"auth:{ip}")
    if not allowed:
        raise HTTPException(
            status_code=429,
            detail="Too many authentication attempts",
            headers={"Retry-After": str(retry_after)},
        )

    user_id = token_store.authenticate(x_cnc_app_token)
    if user_id is None:
        logger.warning("auth_failed ip=%s", ip)
        raise HTTPException(status_code=401, detail="CNC Master access token is invalid")

    if not session_signer.configured:
        raise HTTPException(status_code=503, detail="Gateway session signing is not configured")

    access_token, expires_at = session_signer.issue(user_id)
    logger.info("auth_session_issued user_id=%s ip=%s", user_id, ip)
    return SessionResponse(
        access_token=access_token,
        expires_at=expires_at,
        expires_in=max(0, expires_at - __import__("time").time_ns() // 1_000_000_000),
    )


@app.post("/v1/cnc/generate", response_model=CncGenerateResponse)
async def generate_cnc(
    request: CncGenerateRequest,
    response: Response,
    authorization: str | None = Header(default=None),
    x_cnc_app_token: str | None = Header(default=None),
) -> CncGenerateResponse:
    user_id = _authenticate_session(authorization, x_cnc_app_token)
    _enforce_rate_limit(user_id)

    payload = request.model_dump()
    request_id, result = await broker.submit(payload, user_id)

    try:
        validated = CncGenerateResponse.model_validate(result)
    except Exception as exc:
        logger.warning("worker_response_invalid request_id=%s user_id=%s", request_id, user_id)
        raise HTTPException(status_code=502, detail="Local AI worker returned invalid response") from exc

    response.headers["X-CNC-Request-Id"] = request_id
    return validated


@app.websocket("/v1/worker/ws")
async def worker_websocket(websocket: WebSocket) -> None:
    supplied_token = websocket.headers.get("x-cnc-worker-token", "")
    worker_id = websocket.headers.get("x-cnc-worker-id", "gpu-worker")

    if not worker_token or not hmac.compare_digest(supplied_token, worker_token):
        await websocket.close(code=1008, reason="worker authentication failed")
        return

    await websocket.accept()
    attached = await broker.attach(websocket, worker_id)
    if not attached:
        await websocket.close(code=1013, reason="another worker is already connected")
        return

    try:
        while True:
            message = await websocket.receive_json()
            if isinstance(message, dict):
                broker.resolve(message)
    except WebSocketDisconnect:
        pass
    except Exception:
        logger.exception("worker_websocket_error worker_id=%s", worker_id[:80])
    finally:
        await broker.detach(websocket)
