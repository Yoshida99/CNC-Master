from __future__ import annotations

import json
import os
from typing import Any

import httpx

from .base import ProviderNotConfigured, ProviderUpstreamError


DEFAULT_BASE_URL = "http://127.0.0.1:11434"
DEFAULT_MODEL = "qwen2.5-coder:7b-instruct"
DEFAULT_NUM_CTX = 8192
DEFAULT_KEEP_ALIVE = "0s"

CNC_OUTPUT_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "answer": {"type": "string"},
        "code": {"type": "string"},
        "warnings": {
            "type": "array",
            "items": {"type": "string"},
        },
        "assumptions": {
            "type": "array",
            "items": {"type": "string"},
        },
    },
    "required": ["answer", "code", "warnings", "assumptions"],
}


class OllamaProvider:
    def __init__(
        self,
        *,
        base_url: str,
        model: str,
        num_ctx: int = DEFAULT_NUM_CTX,
        keep_alive: str = DEFAULT_KEEP_ALIVE,
        timeout_seconds: float = 300.0,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._model = model.strip()
        self._num_ctx = max(2048, num_ctx)
        self._keep_alive = keep_alive.strip() or DEFAULT_KEEP_ALIVE
        self._timeout_seconds = timeout_seconds

    @classmethod
    def from_env(cls) -> "OllamaProvider":
        raw_ctx = os.getenv("OLLAMA_NUM_CTX", str(DEFAULT_NUM_CTX)).strip()
        try:
            num_ctx = int(raw_ctx)
        except ValueError:
            num_ctx = DEFAULT_NUM_CTX

        return cls(
            base_url=os.getenv("OLLAMA_BASE_URL", DEFAULT_BASE_URL),
            model=os.getenv("OLLAMA_MODEL", DEFAULT_MODEL),
            num_ctx=num_ctx,
            keep_alive=os.getenv("OLLAMA_KEEP_ALIVE", DEFAULT_KEEP_ALIVE),
            timeout_seconds=float(os.getenv("OLLAMA_TIMEOUT_SECONDS", "300")),
        )

    @property
    def name(self) -> str:
        return "ollama"

    @property
    def model(self) -> str:
        return self._model

    @property
    def configured(self) -> bool:
        return bool(self._base_url and self._model)

    async def generate(
        self,
        *,
        system_prompt: str,
        user_context: dict[str, Any],
        max_output_tokens: int,
    ) -> str:
        if not self.configured:
            raise ProviderNotConfigured("Ollama provider is not configured")

        endpoint = f"{self._base_url}/api/chat"
        payload = {
            "model": self._model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": json.dumps(user_context, ensure_ascii=False),
                },
            ],
            "stream": False,
            "format": CNC_OUTPUT_SCHEMA,
            "keep_alive": self._keep_alive,
            "options": {
                "temperature": 0,
                "num_ctx": self._num_ctx,
                "num_predict": max_output_tokens,
            },
        }

        try:
            async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
                response = await client.post(endpoint, json=payload)
        except httpx.HTTPError as exc:
            raise ProviderUpstreamError(f"Ollama connection failed: {exc}") from exc

        if response.status_code >= 400:
            raise ProviderUpstreamError(f"Ollama error: {_error_detail(response)}")

        try:
            response_payload = response.json()
        except ValueError as exc:
            raise ProviderUpstreamError("Ollama returned invalid JSON") from exc

        message = response_payload.get("message", {})
        text = message.get("content") if isinstance(message, dict) else None
        if not isinstance(text, str) or not text.strip():
            raise ProviderUpstreamError("Ollama returned an empty response")
        return text.strip()


def _error_detail(response: httpx.Response) -> str:
    try:
        payload = response.json()
        message = payload.get("error") if isinstance(payload, dict) else None
        if message:
            return str(message)[:1000]
    except ValueError:
        pass
    return f"HTTP {response.status_code}"
