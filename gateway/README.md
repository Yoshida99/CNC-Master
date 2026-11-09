# CNC Master AI Gateway

M3.3 public gateway sits between Android and the private GPU PC.

## Network model

```text
Android
  |
  | HTTPS
  v
Render gateway
  |
  | WSS (outbound connection from PC)
  v
GPU worker on PC
  |
  | localhost HTTP
  v
CNC Master backend -> Ollama -> RTX 3080
```

Ports `11434` (Ollama) and `8787` (local CNC API) remain bound to localhost and are never exposed to the internet.

## Authentication

### Android / client

Публичный режим не требует общего секрета в APK.

1. При первой установке CNC Master генерирует случайный installation ID и хранит его локально.
2. Приложение отправляет его только в `POST /v1/auth/session` через `X-CNC-Install-Id`.
3. Gateway преобразует ID в псевдонимный hash-based user ID и выдаёт короткоживущую HMAC-signed bearer session.
4. AI-запросы используют только `Authorization: Bearer <session>`.

Для этого режима включается:

```text
CNC_ALLOW_ANONYMOUS_CLIENTS=true
```

Для закрытой beta также остаётся режим private bootstrap token. В production можно хранить на gateway только SHA-256:

```text
CNC_CLIENT_TOKEN_HASHES_JSON={"yoshida":"<sha256-hex>"}
```

`CNC_ALLOW_LEGACY_TOKEN=true` разрешён только как временный migration mode.

### GPU worker

The PC opens an outbound WebSocket with its private worker token. Render stores only `CNC_WORKER_TOKEN_SHA256`; the raw worker token stays on the PC and is never embedded into Android.

## Limits

Defaults:

- request body: 64 KiB;
- client rate: 10 AI requests / 60 s;
- pending queue: 8;
- worker request timeout: 240 s;
- session lifetime: 6 h.

All values are configurable with environment variables.

## Logging

Technical logs contain request ID, user ID, status and duration. Request bodies, NC programs, prompts and generated code are not written to gateway logs.

## Render

Build:

```bash
pip install -r gateway/requirements.txt
```

Start:

```bash
uvicorn gateway.main:app --host 0.0.0.0 --port $PORT
```

Recommended region: Frankfurt.

## Worker

On the GPU PC:

```powershell
$env:CNC_GATEWAY_WS_URL="wss://<gateway>/v1/worker/ws"
$env:CNC_WORKER_TOKEN="<secret>"
.\scripts\start-gpu-worker.ps1
```

The local AI backend must already be running on `http://127.0.0.1:8787`.
