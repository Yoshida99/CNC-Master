from __future__ import annotations

from typing import Any, Protocol


class ProviderError(RuntimeError):
    """Base provider failure exposed to the API layer."""


class ProviderNotConfigured(ProviderError):
    """The selected provider exists but is not ready to serve requests."""


class ProviderUpstreamError(ProviderError):
    """The provider was contacted but returned an invalid/failed response."""


class AiProvider(Protocol):
    @property
    def name(self) -> str:
        ...

    @property
    def model(self) -> str:
        ...

    @property
    def configured(self) -> bool:
        ...

    async def generate(
        self,
        *,
        system_prompt: str,
        user_context: dict[str, Any],
        max_output_tokens: int,
    ) -> str:
        ...
