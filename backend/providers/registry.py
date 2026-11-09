from __future__ import annotations

import os
from typing import Any

from .base import AiProvider, ProviderNotConfigured
from .openai_provider import OpenAiResponsesProvider
from .ollama_provider import OllamaProvider


class UnavailableProvider:
    def __init__(self, requested_name: str) -> None:
        self._name = requested_name or "unknown"

    @property
    def name(self) -> str:
        return self._name

    @property
    def model(self) -> str:
        return ""

    @property
    def configured(self) -> bool:
        return False

    async def generate(
        self,
        *,
        system_prompt: str,
        user_context: dict[str, Any],
        max_output_tokens: int,
    ) -> str:
        raise ProviderNotConfigured(
            f"AI provider '{self._name}' is not implemented/configured on this server"
        )


def build_provider(requested_name: str | None = None) -> AiProvider:
    name = (requested_name or os.getenv("CNC_AI_PROVIDER", "openai")).strip().lower()

    if name in {"openai", "openai-responses", "openai_responses"}:
        return OpenAiResponsesProvider.from_env()

    if name in {"ollama", "local", "local-ollama", "local_ollama"}:
        return OllamaProvider.from_env()

    return UnavailableProvider(name)
