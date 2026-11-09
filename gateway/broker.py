from __future__ import annotations

import asyncio
import logging
import time
import uuid
from typing import Any

from fastapi import HTTPException, WebSocket


logger = logging.getLogger("cnc.gateway.broker")


class WorkerBroker:
    def __init__(self, *, queue_max: int, request_timeout_seconds: float) -> None:
        self.queue_max = max(1, queue_max)
        self.request_timeout_seconds = max(5.0, request_timeout_seconds)
        self._worker: WebSocket | None = None
        self._worker_id = ""
        self._pending: dict[str, asyncio.Future[dict[str, Any]]] = {}
        self._send_lock = asyncio.Lock()
        self._state_lock = asyncio.Lock()
        self._connected_at: float | None = None

    @property
    def connected(self) -> bool:
        return self._worker is not None

    @property
    def queue_depth(self) -> int:
        return len(self._pending)

    @property
    def worker_id(self) -> str:
        return self._worker_id

    async def attach(self, websocket: WebSocket, worker_id: str) -> bool:
        async with self._state_lock:
            if self._worker is not None:
                return False
            self._worker = websocket
            self._worker_id = worker_id[:80]
            self._connected_at = time.monotonic()
            logger.info("worker_connected worker_id=%s", self._worker_id)
            return True

    async def detach(self, websocket: WebSocket) -> None:
        async with self._state_lock:
            if self._worker is not websocket:
                return
            self._worker = None
            worker_id = self._worker_id
            self._worker_id = ""
            self._connected_at = None

            pending = list(self._pending.items())
            self._pending.clear()
            for _, future in pending:
                if not future.done():
                    future.set_exception(RuntimeError("GPU worker disconnected"))

            logger.warning("worker_disconnected worker_id=%s pending_failed=%d", worker_id, len(pending))

    async def submit(self, payload: dict[str, Any], user_id: str) -> tuple[str, dict[str, Any]]:
        worker = self._worker
        if worker is None:
            raise HTTPException(status_code=503, detail="Local AI worker is offline")
        if len(self._pending) >= self.queue_max:
            raise HTTPException(status_code=429, detail="Local AI queue is full")

        request_id = uuid.uuid4().hex
        future: asyncio.Future[dict[str, Any]] = asyncio.get_running_loop().create_future()
        self._pending[request_id] = future
        started = time.monotonic()

        try:
            async with self._send_lock:
                current_worker = self._worker
                if current_worker is None:
                    raise HTTPException(status_code=503, detail="Local AI worker is offline")
                await current_worker.send_json({
                    "type": "job",
                    "request_id": request_id,
                    "payload": payload,
                })

            result = await asyncio.wait_for(future, timeout=self.request_timeout_seconds)
            logger.info(
                "job_complete request_id=%s user_id=%s duration_ms=%d",
                request_id,
                user_id,
                int((time.monotonic() - started) * 1000),
            )
            return request_id, result
        except asyncio.TimeoutError as exc:
            logger.warning(
                "job_timeout request_id=%s user_id=%s duration_ms=%d",
                request_id,
                user_id,
                int((time.monotonic() - started) * 1000),
            )
            raise HTTPException(status_code=504, detail="Local AI request timed out") from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        finally:
            self._pending.pop(request_id, None)

    def resolve(self, message: dict[str, Any]) -> None:
        request_id = str(message.get("request_id", ""))
        future = self._pending.get(request_id)
        if future is None or future.done():
            return

        message_type = message.get("type")
        if message_type == "result":
            payload = message.get("payload")
            if isinstance(payload, dict):
                future.set_result(payload)
            else:
                future.set_exception(RuntimeError("GPU worker returned invalid payload"))
        elif message_type == "error":
            detail = str(message.get("detail", "Local AI worker failed"))[:500]
            future.set_exception(RuntimeError(detail))
