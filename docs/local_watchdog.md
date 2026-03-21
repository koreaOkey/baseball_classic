# Local Watchdog

This watchdog keeps local `backend` and `dispatcher` running, and auto-recovers when the backend health check fails.

## What It Does

- checks `http://localhost:8080/health` every few seconds
- if backend is unhealthy:
  - stops stale `uvicorn app.main:app --port 8080` processes
  - restarts backend with `.venv\Scripts\python.exe`
- keeps dispatcher running:
  - `crawler/live_baseball_dispatcher.py`
  - restarts when the process exits
- writes watchdog logs to `log/watchdog_YYYYMMDD.log`
- writes child process stdout/stderr logs to `log/backend_watchdog_*.log` and `log/dispatcher_watchdog_*.log`

## Script

- path: `scripts/dev/watch_local_stack.ps1`

## Run

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/watch_local_stack.ps1
```

## Common Options

```powershell
# one-cycle health check only
powershell -ExecutionPolicy Bypass -File scripts/dev/watch_local_stack.ps1 -RunOnce

# do not manage dispatcher
powershell -ExecutionPolicy Bypass -File scripts/dev/watch_local_stack.ps1 -NoDispatcher

# faster checks
powershell -ExecutionPolicy Bypass -File scripts/dev/watch_local_stack.ps1 -CheckIntervalSec 5

# override API key explicitly (otherwise loaded from backend/api/.env)
powershell -ExecutionPolicy Bypass -File scripts/dev/watch_local_stack.ps1 -BackendApiKey "dev-crawler-key"
```

## Notes

- default Python path is `.venv\Scripts\python.exe` from repo root
- default backend is `0.0.0.0:8080`
- default dispatcher target is `--league kbo`
