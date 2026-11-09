from __future__ import annotations

import asyncio
import json
import logging
import os
import socket
import time
from typing import Any

import httpx
import websockets

try:
    from .identity import load_or_create_worker_token
except ImportError:
    from identity import load_or_create_worker_token


logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("cnc.gpu_worker")

GATEWAY_WS_URL = os.getenv("CNC_GATEWAY_WS_URL", "").strip()
WORKER_TOKEN = os.getenv("CNC_WORKER_TOKEN", "").strip() or load_or_create_worker_token()
WORKER_ID = os.getenv("CNC_WORKER_ID", socket.gethostname()).strip()[:80]
LOCAL_CNC_URL = os.getenv("CNC_LOCAL_AI_URL", "http://127.0.0.1:8787").rstrip("/")
LOCAL_TIMEOUT_SECONDS = float(os.getenv("CNC_LOCAL_AI_TIMEOUT_SECONDS", "300"))


async def _process_job(client: httpx.AsyncClient, message: dict[str, Any]) -> dict[str, Any]:
    request_id = str(message.get("request_id", ""))
    payload = message.get("payload")
    if not request_id or not isinstance(payload, dict):
        return {
            "type": "error",
            "request_id": request_id,
            "detail": "Invalid gateway job",
        }

    started = time.monotonic()
    try:
        response = await client.post(
            f"{LOCAL_CNC_URL}/v1/cnc/generate",
            json=payload,
        )
        if response.status_code >= 400:
            detail = f"Local CNC AI HTTP {response.status_code}"
            try:
                body = response.json()
                if isinstance(body, dict) and body.get("detail"):
                    detail = str(body["detail"])[:500]
            except ValueError:
                pass
            logger.warning(
                "job_failed request_id=%s status=%s duration_ms=%d",
                request_id,
                response.status_code,
                int((time.monotonic() - started) * 1000),
            )
            return {"type": "error", "request_id": request_id, "detail": detail}

        payload_out = response.json()
        if not isinstance(payload_out, dict):
            raise ValueError("Local CNC AI returned non-object JSON")

        logger.info(
            "job_complete request_id=%s duration_ms=%d",
            request_id,
            int((time.monotonic() - started) * 1000),
        )
        return {
            "type": "result",
            "request_id": request_id,
            "payload": payload_out,
        }
    except (httpx.HTTPError, ValueError) as exc:
        logger.warning(
            "job_exception request_id=%s duration_ms=%d error=%s",
            request_id,
            int((time.monotonic() - started) * 1000),
            type(exc).__name__,
        )
        return {
            "type": "error",
            "request_id": request_id,
            "detail": "Local CNC AI is unavailable",
        }


async def run_forever() -> None:
    if not GATEWAY_WS_URL.startswith(("ws://", "wss://")):
        raise RuntimeError("CNC_GATEWAY_WS_URL must be configured")
    if len(WORKER_TOKEN) < 24:
        raise RuntimeError("CNC_WORKER_TOKEN must be configured")

    backoff = 1.0
    timeout = httpx.Timeout(LOCAL_TIMEOUT_SECONDS)
    async with httpx.AsyncClient(timeout=timeout) as client:
        while True:
            try:
                logger.info("connecting worker_id=%s", WORKER_ID)
                async with websockets.connect(
                    GATEWAY_WS_URL,
                    additional_headers={
                        "X-CNC-Worker-Token": WORKER_TOKEN,
                        "X-CNC-Worker-Id": WORKER_ID,
                    },
                    ping_interval=20,
                    ping_timeout=20,
                    close_timeout=10,
                    max_size=1024 * 1024,
                ) as websocket:
                    logger.info("connected worker_id=%s", WORKER_ID)
                    backoff = 1.0
                    async for raw in websocket:
                        try:
                            message = json.loads(raw)
                        except json.JSONDecodeError:
                            continue
                        if not isinstance(message, dict) or message.get("type") != "job":
                            continue

                        result = await _process_job(client, message)
                        await websocket.send(json.dumps(result, ensure_ascii=False))
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                logger.warning("gateway_disconnected error=%s retry_seconds=%.1f", type(exc).__name__, backoff)
                await asyncio.sleep(backoff)
                backoff = min(30.0, backoff * 2)


if __name__ == "__main__":
    asyncio.run(run_forever())
