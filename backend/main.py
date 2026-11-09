import json
import os
import re
from typing import Any

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

try:
    from .cnc_prompt import SYSTEM_PROMPT
    from .providers import ProviderError, ProviderNotConfigured, ProviderUpstreamError, build_provider
except ImportError:
    from cnc_prompt import SYSTEM_PROMPT
    from providers import ProviderError, ProviderNotConfigured, ProviderUpstreamError, build_provider


app = FastAPI(title="CNC Master AI", version="0.3.0-dev")

CNC_APP_TOKEN = os.getenv("CNC_APP_TOKEN", "").strip()
AI_PROVIDER = build_provider()


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
    warnings: list[str] = []
    assumptions: list[str] = []


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "ok": True,
        "provider": AI_PROVIDER.name,
        "model": AI_PROVIDER.model,
        "ai_configured": AI_PROVIDER.configured,
        "auth_required": bool(CNC_APP_TOKEN),
    }


@app.post("/v1/cnc/generate", response_model=CncGenerateResponse)
async def generate_cnc(
    request: CncGenerateRequest,
    x_cnc_app_token: str | None = Header(default=None),
) -> CncGenerateResponse:
    if CNC_APP_TOKEN and x_cnc_app_token != CNC_APP_TOKEN:
        raise HTTPException(status_code=401, detail="CNC Master access token is invalid")

    user_context = {
        "task": request.message,
        "machine": request.machine,
        "control": request.control,
        "material": request.material,
        "stock": request.stock,
        "tools_and_offsets": request.tools,
        "workholding": request.workholding,
        "zero_point": request.zero_point,
        "current_code": request.current_code,
    }

    try:
        text = await AI_PROVIDER.generate(
            system_prompt=SYSTEM_PROMPT,
            user_context=user_context,
            max_output_tokens=3500,
        )
    except ProviderNotConfigured as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except ProviderUpstreamError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    except ProviderError as exc:
        raise HTTPException(status_code=502, detail=f"AI provider failed: {exc}") from exc

    parsed = _parse_model_json(text)
    if parsed is None:
        return CncGenerateResponse(
            answer=text[:8000],
            code="",
            warnings=["AI response could not be parsed as structured CNC output. No NC file was produced."],
            assumptions=[],
        )

    code = _ascii_nc(str(parsed.get("code", "")))
    warnings = _string_list(parsed.get("warnings"))
    assumptions = _string_list(parsed.get("assumptions"))
    answer = str(parsed.get("answer", "")).strip()

    if code:
        warnings.append(
            "Generated NC is a draft. Verify offsets, workholding, tool geometry, trajectory, spindle limits and run Graphics/Single Block/Dry Run before cutting."
        )

    return CncGenerateResponse(
        answer=answer,
        code=code,
        warnings=_dedupe(warnings),
        assumptions=_dedupe(assumptions),
    )


def _parse_model_json(text: str) -> dict[str, Any] | None:
    cleaned = text.strip()
    cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s*```$", "", cleaned)
    try:
        value = json.loads(cleaned)
        return value if isinstance(value, dict) else None
    except json.JSONDecodeError:
        start = cleaned.find("{")
        end = cleaned.rfind("}")
        if start < 0 or end <= start:
            return None
        try:
            value = json.loads(cleaned[start : end + 1])
            return value if isinstance(value, dict) else None
        except json.JSONDecodeError:
            return None


def _ascii_nc(code: str) -> str:
    normalized = code.replace("\r\n", "\n").replace("\r", "\n")
    chars: list[str] = []
    for char in normalized:
        if char == "\n" or char == "\t" or 32 <= ord(char) <= 126:
            chars.append(char)
        else:
            chars.append(" ")
    return "\r\n".join(line.rstrip() for line in "".join(chars).split("\n")).strip()


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item).strip()]


def _dedupe(items: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        key = item.casefold()
        if key not in seen:
            seen.add(key)
            result.append(item)
    return result
