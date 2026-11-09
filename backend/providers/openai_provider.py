from __future__ import annotations

import json
import os
from typing import Any

import httpx

from .base import ProviderNotConfigured, ProviderUpstreamError


DEFAULT_RESPONSES_URL = "https://api.openai.com/v1/responses"
DEFAULT_MODEL = "gpt-6.1-sol"


class OpenAiResponsesProvider:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        responses_url: str = DEFAULT_RESPONSES_URL,
        timeout_seconds: float = 90.0,
    ) -> None:
        self._api_key = api_key.strip()
        self._model = model.strip() or DEFAULT_MODEL
        self._responses_url = responses_url.strip() or DEFAULT_RESPONSES_URL
        self._timeout_seconds = timeout_seconds

    @classmethod
    def from_env(cls) -> "OpenAiResponsesProvider":
        return cls(
            api_key=os.getenv("OPENAI_API_KEY", ""),
            model=os.getenv("OPENAI_MODEL", DEFAULT_MODEL),
            responses_url=os.getenv("OPENAI_RESPONSES_URL", DEFAULT_RESPONSES_URL),
        )

    @property
    def name(self) -> str:
        return "openai"

    @property
    def model(self) -> str:
        return self._model

    @property
    def configured(self) -> bool:
        return bool(self._api_key)

    async def generate(
        self,
        *,
        system_prompt: str,
        user_context: dict[str, Any],
        max_output_tokens: int,
    ) -> str:
        if not self.configured:
            raise ProviderNotConfigured("OPENAI_API_KEY is not configured on the server")

        payload = {
            "model": self._model,
            "instructions": system_prompt,
            "input": json.dumps(user_context, ensure_ascii=False),
            "max_output_tokens": max_output_tokens,
        }
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Content-Type": "application/json",
        }

        try:
            async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
                response = await client.post(self._responses_url, headers=headers, json=payload)
        except httpx.HTTPError as exc:
            raise ProviderUpstreamError(f"OpenAI connection failed: {exc}") from exc

        if response.status_code >= 400:
            raise ProviderUpstreamError(f"OpenAI error: {_error_detail(response)}")

        try:
            response_payload = response.json()
        except ValueError as exc:
            raise ProviderUpstreamError("OpenAI returned invalid JSON") from exc

        text = _extract_output_text(response_payload).strip()
        if not text:
            raise ProviderUpstreamError("OpenAI returned an empty response")
        return text


def _error_detail(response: httpx.Response) -> str:
    try:
        payload = response.json()
        error = payload.get("error", {})
        message = error.get("message") if isinstance(error, dict) else None
        if message:
            return str(message)[:1000]
    except ValueError:
        pass
    return f"HTTP {response.status_code}"


def _extract_output_text(payload: dict[str, Any]) -> str:
    direct = payload.get("output_text")
    if isinstance(direct, str) and direct.strip():
        return direct

    parts: list[str] = []
    output = payload.get("output", [])
    if isinstance(output, list):
        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content", [])
            if not isinstance(content, list):
                continue
            for part in content:
                if not isinstance(part, dict):
                    continue
                if part.get("type") in {"output_text", "text"}:
                    text = part.get("text")
                    if isinstance(text, str):
                        parts.append(text)
    return "\n".join(parts)
