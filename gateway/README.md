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

1. Private bootstrap token is supplied in `X-CNC-App-Token`.
2. `POST /v1/auth/session` returns a short-lived HMAC-signed bearer session.
3. AI requests use `Authorization: Bearer <session>`.

Preferred deployment stores only SHA-256 hashes on the gateway:

```text
CNC_CLIENT_TOKEN_HASHES_JSON={"yoshida":"<sha256-hex>"}
```

The private bootstrap token itself stays only on the client device. Raw `CNC_CLIENT_TOKENS_JSON` remains available only for local/dev migration.

For transitional private builds only, `CNC_ALLOW_LEGACY_TOKEN=true` allows the old header directly on AI requests. Public builds should keep it false.

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
