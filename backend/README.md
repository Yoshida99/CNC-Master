# CNC Master AI backend

Backend сохраняет стабильный Android-контракт:

- `GET /health`
- `POST /v1/cnc/generate`

Android не зависит от конкретной нейросети. Выбор провайдера выполняется на сервере через `CNC_AI_PROVIDER`.

## Provider layer

Структура:

- `providers/base.py` — общий интерфейс и ошибки;
- `providers/registry.py` — выбор активного provider;
- `providers/openai_provider.py` — OpenAI Responses provider;
- `providers/ollama_provider.py` — локальный Ollama provider;
- `cnc_prompt.py` — единый CNC safety/system prompt;
- `main.py` — HTTP API, авторизация и нормализация NC-ответа.

### Текущий provider: OpenAI

Переменные:

- `CNC_AI_PROVIDER=openai`
- `OPENAI_API_KEY`
- `OPENAI_MODEL` — по умолчанию `gpt-6.1-sol`
- `OPENAI_RESPONSES_URL` — необязательный override endpoint
- `CNC_APP_TOKEN` — временный shared gate

Запуск из корня репозитория:

```bash
uvicorn backend.main:app --host 0.0.0.0 --port 8000
```

Или из каталога `backend`:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

### Local provider: Ollama

Рекомендуемая стартовая конфигурация для RTX 3080 10 GB:

- `CNC_AI_PROVIDER=ollama`
- `OLLAMA_BASE_URL=http://127.0.0.1:11434`
- `OLLAMA_MODEL=qwen2.5-coder:7b-instruct`
- `OLLAMA_NUM_CTX=8192`
- `OLLAMA_KEEP_ALIVE=0s`

`OLLAMA_KEEP_ALIVE=0s` намеренно выгружает модель из VRAM после каждого ответа, чтобы не держать память GPU занятой между запросами.

Ollama вызывается через `/api/chat` с JSON Schema для полей `answer`, `code`, `warnings`, `assumptions`.

Для локального запуска из PowerShell:

```powershell
.\scripts\start-local-ai.ps1
```

Публично открывать порт Ollama `11434` нельзя. В M3.3 внешний доступ будет идти только через защищённый CNC Master gateway.
