# CNC Master — Roadmap

Обновлено: 09.11.2026.

## M2.2 — стабилизация v0.2.1

Статус: **завершено и слито в `main`**.

- [x] Ограничить хранение новых GitHub Actions APK-артефактов.
- [x] Синхронизировать README с фактическим v0.2.1.
- [x] Добавить QA для AI-программиста.
- [x] Исправить несовпадение backend `CNC_APP_TOKEN` и Android-клиента.
- [x] Добавить unit-тесты заголовка AI app token.
- [x] Проверить CI ветки и слить M2.2 в `main`.

## M3 — Local AI / собственная GPU-инфраструктура

### M3.1 — AI Provider Layer
Статус: **завершено и слито в `main`**.

- [x] отделить CNC API от конкретного OpenAI provider;
- [x] единый интерфейс provider;
- [x] provider registry;
- [x] health/status активного provider;
- [x] совместимость с текущим Android API;
- [x] перенести CNC system prompt в отдельный модуль;
- [x] CI compileall для всех backend-модулей.

### M3.2 — локальная LLM
Статус: **реализовано и проверено на RTX 3080 10 GB**.

- [x] Ollama provider в backend;
- [x] structured JSON output;
- [x] стартовая модель `qwen2.5-coder:7b-instruct`;
- [x] контекст 8192 для 10 GB VRAM;
- [x] автоматическая выгрузка модели из VRAM после ответа;
- [x] PowerShell-скрипт локального запуска;
- [x] Ollama 0.40.15 установлен на рабочем ПК;
- [x] модель 4.7 GB скачана;
- [x] RTX 3080 обнаружена через CUDA;
- [x] полный путь FastAPI → Ollama → RTX 3080 проверен;
- [x] initial CNC safety benchmark: при недостаточных данных код не генерируется;
- [x] VRAM после ответа освобождается автоматически.

Baseline при активном майнинге: около 49.5 с на первый safety-запрос; VRAM ~1.36 → ~6.49 → ~1.36 GB. Производительность с автоматической паузой майнинга будет измерена на M3.5.

### M3.3 — AI Gateway
- HTTPS;
- пользовательская авторизация;
- rate limit;
- лимиты размера запросов;
- queue/timeout;
- журнал технических ошибок без хранения лишнего содержимого программ.

### M3.4 — Android ↔ Local AI
- production endpoint;
- status UI;
- retry;
- понятный offline/fallback режим.

### M3.5 — GPU arbitration
- интеграция с Yoshida Mining Agent;
- пауза/снижение майнинга перед inference;
- освобождение VRAM;
- автоматическое восстановление майнинга после ответа.

### M3.6 — очередь запросов
- последовательная обработка при нехватке VRAM;
- позиции/статусы очереди;
- защита от перегрузки.

### M3.7 — CNC benchmark
- реальные задачи Fanuc 0i-TF;
- точение, расточка, резьба, сверление, канавки;
- проверка опасных предположений;
- сравнение качества моделей.

### M3.8 — v0.3.0
- signed APK;
- update manifest;
- обновление страницы CNC Master на Yoshida App Studio.

## M4 — Machine Intelligence / v0.4.0

- профили FELLER FTC-10/20L/25/35;
- параметры стойки Fanuc 0i-TF;
- библиотека инструментов/корректоров;
- ограничения оборотов и станкоспецифичные M-коды;
- передача профиля станка в AI.

## M5 — Safety Engine 2.0 / v0.5.0

- полноценный parser G-code;
- модальные состояния;
- X/Z state;
- G00/G01/G02/G03;
- G90/G91, G96/G97, G50;
- шпиндель/подача/инструмент;
- 2D X/Z trajectory preview.

## M6 — расширенный Fanuc / v0.6.0

- G70, G71, G72, G74, G75, G76, G83, G92;
- фаски, радиусы, канавки, сверление и расточка;
- объяснение блоков программы.

## M7 — база технолога / v0.7.0

- переход от SharedPreferences + JSON к локальной БД;
- деталь → операция → станок → программа → инструмент → режим;
- история версий;
- backup/import/export;
- поиск.

## M8 — AI Copilot / v0.8.0

- AI в диагностике;
- AI в режимах;
- AI в Fanuc;
- проверка сохранённых программ;
- контекст из деталей и инструмента.

## M9 — Public Beta / v0.9.0

- нагрузочные тесты;
- несколько пользователей;
- отказ/перезагрузка AI-сервера;
- USB OTG;
- update migration;
- crash/ANR cleanup.

## M10 — Stable / v1.0.0

- стабильный CNC toolkit;
- AI Copilot;
- Local AI;
- G-code safety engine;
- X/Z preview;
- signed update pipeline;
- USB export.
