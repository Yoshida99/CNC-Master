# CNC Master GPU Worker

GPU worker держит **исходящее** WSS-соединение с CNC Master gateway и передаёт задания только локальному AI на `127.0.0.1:8787`.

## M3.5 — GPU arbitration

Арбитраж выключен по умолчанию.

Для тестового включения:

```powershell
.\scripts\start-gpu-worker.ps1 -EnableMiningArbitration
```

При включённом режиме:

1. worker проверяет, работал ли Fl4shMiner до AI-запроса;
2. если майнинг уже был вручную остановлен/поставлен на паузу — ничего не меняет;
3. через существующий signed lifecycle Yoshida Mining Monitor отправляет `pause`;
4. ждёт подтверждения privileged lifecycle agent;
5. выполняет Local AI inference;
6. в `finally` отправляет `start`, только если именно этот AI-запрос временно остановил майнинг;
7. если после AI-паузы появился отдельный ручной `pause/stop`, автоматический `start` отменяется;
8. если восстановление временно блокирует safety guard, lease сохраняется для recovery.

CNC gateway не получает Telegram token, owner id или lifecycle HMAC. Они читаются только локальным arbiter на ПК. В памяти worker хранится только производный HMAC-ключ.

## Crash recovery

`worker/mining_arbitration.py` пишет локальный lease в `%LOCALAPPDATA%\Yoshida\CNC-Master`.

Установка recovery-задачи:

```powershell
.\scripts\install-mining-arbitration-recovery.ps1
```

Задача раз в минуту проверяет просроченный lease и пытается восстановить майнинг штатной командой `start`. STOP/ручная PAUSE и safety guards не обходятся.

## Live benchmark

Только на рабочем GPU-ПК:

```powershell
$env:CNC_GPU_ARBITRATION_ENABLED="true"
.\.venv\Scripts\python.exe scripts\test_m3_5_live_arbitration.py
```

Скрипт выполняет `pause → Local AI → resume` и печатает только технические метрики GPU/времени без секретов.
