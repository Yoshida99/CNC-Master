# CNC Master AI backend

Backend сохраняет стабильный Android-контракт:

- `GET /health`
- `POST /v1/cnc/generate`

Android не зависит от конкретной нейросети. Выбор провайдера выполняется на сервере через `CNC_AI_PROVIDER`.

## Provider layer

Структура:

- `providers/base.py` — общий интерфейс и ошибки;
- `providers/registry.py` — выбор активного provider;
- `providers/openai_provider.py` — текущий OpenAI Responses provider;
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

## M3.2

Следующий provider будет локальным и будет использовать GPU-сервер. Он подключится через registry, поэтому Android endpoint и JSON-формат не изменятся.
