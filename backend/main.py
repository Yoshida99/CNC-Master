import json
import os
import re
from typing import Any

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="CNC Master AI", version="0.1.0")

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-5.6-terra").strip() or "gpt-5.6-terra"
CNC_APP_TOKEN = os.getenv("CNC_APP_TOKEN", "").strip()
OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"


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


SYSTEM_PROMPT = r"""
You are the CNC Master programming assistant for CNC turning. Your job is to help an experienced setup operator PREPARE and REVIEW Fanuc-style lathe code, not to certify code as safe to run.

Return ONLY one JSON object with exactly these keys:
{
  "answer": "short Russian explanation or clarifying questions",
  "code": "ASCII-only NC program or fragment, or empty string",
  "warnings": ["..."],
  "assumptions": ["..."]
}

Safety and accuracy rules:
1. Never claim that generated code is safe, collision-free, verified, production-ready, or ready to run.
2. Never instruct the user to bypass guards, interlocks, dry-run, single-block, graphics/simulation, offset checks, or chuck-clearance checks.
3. Do not invent unknown tool geometry, insert orientation, tool offsets, work offsets, chuck dimensions, stock stick-out, spindle limits, machine-specific M-codes, tailstock/subspindle behavior, or control options.
4. If critical geometry or machine information is missing, ask concise clarifying questions and leave "code" empty rather than fabricating a complete runnable program.
5. When code is appropriate, target only the control explicitly stated by the user. For Fanuc 0i-TF use conservative, conventional syntax and call out controller/machine-specific assumptions.
6. G-code inside "code" must be 7-bit ASCII only. No Cyrillic comments. Keep comments short and in English/Latin characters.
7. For G96 constant surface speed, require an explicit maximum spindle clamp appropriate to the machine/workholding. If the safe value is unknown, do not invent it.
8. For threading, explicitly verify pitch, hand, start position, spindle direction, final diameter, infeed format and the exact G76/G92 format for the target control.
9. Treat G28, G53, G10, macros (# variables), custom M-codes, live tooling, C/Y axes, subspindle and bar-feeder commands as machine-specific and highlight them.
10. End every complete-program answer with warnings that it must be checked on the actual machine using graphics/simulation, safe start position, Single Block/Dry Run and close observation of the first pass.

The user may be an experienced CNC setup operator. Be technical and concise, but do not replace machine-manual verification.
""".strip()


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "ok": True,
        "model": OPENAI_MODEL,
        "ai_configured": bool(OPENAI_API_KEY),
    }


@app.post("/v1/cnc/generate", response_model=CncGenerateResponse)
async def generate_cnc(
    request: CncGenerateRequest,
    x_cnc_app_token: str | None = Header(default=None),
) -> CncGenerateResponse:
    if CNC_APP_TOKEN and x_cnc_app_token != CNC_APP_TOKEN:
        raise HTTPException(status_code=401, detail="CNC Master access token is invalid")
    if not OPENAI_API_KEY:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not configured on the server")

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

    payload = {
        "model": OPENAI_MODEL,
        "instructions": SYSTEM_PROMPT,
        "input": json.dumps(user_context, ensure_ascii=False),
        "max_output_tokens": 3500,
    }

    headers = {
        "Authorization": f"Bearer {OPENAI_API_KEY}",
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=90.0) as client:
            response = await client.post(OPENAI_RESPONSES_URL, headers=headers, json=payload)
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"OpenAI connection failed: {exc}") from exc

    if response.status_code >= 400:
        detail = _openai_error_detail(response)
        raise HTTPException(status_code=502, detail=f"OpenAI error: {detail}")

    try:
        openai_payload = response.json()
    except ValueError as exc:
        raise HTTPException(status_code=502, detail="OpenAI returned invalid JSON") from exc

    text = _extract_output_text(openai_payload).strip()
    if not text:
        raise HTTPException(status_code=502, detail="OpenAI returned an empty response")

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


def _openai_error_detail(response: httpx.Response) -> str:
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
