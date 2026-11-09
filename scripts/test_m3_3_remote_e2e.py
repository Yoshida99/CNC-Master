from __future__ import annotations

import argparse
import json
import time
import uuid

import httpx


def main() -> int:
    parser = argparse.ArgumentParser(description="CNC Master remote gateway E2E")
    parser.add_argument(
        "--base-url",
        default="https://cnc-master-ai-gateway-main.onrender.com",
    )
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")
    installation_id = str(uuid.uuid4())

    with httpx.Client(timeout=300.0) as client:
        session = client.post(
            f"{base_url}/v1/auth/session",
            headers={"X-CNC-Install-Id": installation_id},
            json={},
        )
        session.raise_for_status()
        access_token = session.json().get("access_token", "")
        if not access_token:
            raise RuntimeError("gateway did not return access token")

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
            f"{base_url}/v1/cnc/generate",
            headers={"Authorization": f"Bearer {access_token}"},
            json=request,
        )
        elapsed_ms = int((time.monotonic() - started) * 1000)
        response.raise_for_status()
        payload = response.json()

        health = client.get(f"{base_url}/health")
        health.raise_for_status()
        health_payload = health.json()

        summary = {
            "session_status": session.status_code,
            "generate_status": response.status_code,
            "request_id_present": bool(response.headers.get("X-CNC-Request-Id")),
            "elapsed_ms": elapsed_ms,
            "answer_length": len(str(payload.get("answer", ""))),
            "code_length": len(str(payload.get("code", ""))),
            "warnings_count": len(payload.get("warnings", [])),
            "assumptions_count": len(payload.get("assumptions", [])),
            "worker_connected": bool(health_payload.get("worker_connected")),
            "queue_depth_after": health_payload.get("queue_depth"),
        }
        print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
