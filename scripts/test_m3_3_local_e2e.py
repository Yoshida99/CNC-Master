from __future__ import annotations

import json
import os
import secrets
import subprocess
import sys
import time
from pathlib import Path

import httpx


ROOT = Path(__file__).resolve().parents[1]
PYTHON = sys.executable
GATEWAY_URL = "http://127.0.0.1:8790"
WORKER_WS_URL = "ws://127.0.0.1:8790/v1/worker/ws"
LOCAL_AI_URL = os.getenv("CNC_LOCAL_AI_URL", "http://127.0.0.1:8787")


def wait_for_gateway(client: httpx.Client, timeout_seconds: float = 15.0) -> dict:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            response = client.get(f"{GATEWAY_URL}/health")
            if response.status_code == 200:
                return response.json()
        except httpx.HTTPError:
            pass
        time.sleep(0.25)
    raise RuntimeError("gateway did not start")


def wait_for_worker(client: httpx.Client, timeout_seconds: float = 20.0) -> dict:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        health = client.get(f"{GATEWAY_URL}/health").json()
        if health.get("worker_connected"):
            return health
        time.sleep(0.25)
    raise RuntimeError("GPU worker did not connect")


def main() -> int:
    client_token = secrets.token_urlsafe(32)
    session_secret = secrets.token_urlsafe(48)
    worker_token = secrets.token_urlsafe(40)

    gateway_env = os.environ.copy()
    gateway_env.update({
        "CNC_CLIENT_TOKENS_JSON": json.dumps({"e2e": client_token}),
        "CNC_SESSION_SECRET": session_secret,
        "CNC_WORKER_TOKEN": worker_token,
        "CNC_ALLOW_LEGACY_TOKEN": "false",
        "CNC_REQUEST_TIMEOUT_SECONDS": "240",
        "CNC_RATE_LIMIT": "10",
    })

    worker_env = os.environ.copy()
    worker_env.update({
        "CNC_GATEWAY_WS_URL": WORKER_WS_URL,
        "CNC_WORKER_TOKEN": worker_token,
        "CNC_LOCAL_AI_URL": LOCAL_AI_URL,
        "CNC_WORKER_ID": "local-e2e-worker",
    })

    gateway = subprocess.Popen(
        [
            PYTHON,
            "-m",
            "uvicorn",
            "gateway.main:app",
            "--host",
            "127.0.0.1",
            "--port",
            "8790",
        ],
        cwd=ROOT,
        env=gateway_env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    worker: subprocess.Popen | None = None

    try:
        with httpx.Client(timeout=300.0) as client:
            wait_for_gateway(client)

            worker = subprocess.Popen(
                [PYTHON, "-m", "worker.cnc_gpu_worker"],
                cwd=ROOT,
                env=worker_env,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            wait_for_worker(client)

            bad_auth = client.post(
                f"{GATEWAY_URL}/v1/auth/session",
                headers={"X-CNC-App-Token": "wrong-token"},
            )
            if bad_auth.status_code != 401:
                raise RuntimeError(f"bad token expected 401, got {bad_auth.status_code}")

            auth = client.post(
                f"{GATEWAY_URL}/v1/auth/session",
                headers={"X-CNC-App-Token": client_token},
            )
            auth.raise_for_status()
            access_token = auth.json()["access_token"]

            request = {
                "message": (
                    "Перечисли только критически недостающие данные перед подготовкой "
                    "наружной резьбы M12x1. Не генерируй код, если данных недостаточно."
                ),
                "machine": "Токарный X/Z",
                "control": "Fanuc 0i-TF",
                "material": "C45",
                "stock": "D14, длина резьбы 18 мм",
                "tools": "16ER; корректор не указан",
                "workholding": "Патрон; вылет не указан",
                "zero_point": "Z0 по торцу",
                "current_code": "",
            }

            started = time.monotonic()
            response = client.post(
                f"{GATEWAY_URL}/v1/cnc/generate",
                headers={"Authorization": f"Bearer {access_token}"},
                json=request,
            )
            elapsed_ms = int((time.monotonic() - started) * 1000)
            response.raise_for_status()
            payload = response.json()
            health = client.get(f"{GATEWAY_URL}/health").json()

            summary = {
                "gateway_ok": True,
                "worker_connected": bool(health.get("worker_connected")),
                "bad_token_status": bad_auth.status_code,
                "session_status": auth.status_code,
                "generate_status": response.status_code,
                "request_id_present": bool(response.headers.get("X-CNC-Request-Id")),
                "elapsed_ms": elapsed_ms,
                "answer_length": len(payload.get("answer", "")),
                "code_length": len(payload.get("code", "")),
                "warnings_count": len(payload.get("warnings", [])),
                "queue_depth_after": health.get("queue_depth"),
            }
            print(json.dumps(summary, ensure_ascii=False))
            return 0
    finally:
        if worker is not None:
            worker.terminate()
            try:
                worker.wait(timeout=5)
            except subprocess.TimeoutExpired:
                worker.kill()
        gateway.terminate()
        try:
            gateway.wait(timeout=5)
        except subprocess.TimeoutExpired:
            gateway.kill()


if __name__ == "__main__":
    raise SystemExit(main())
