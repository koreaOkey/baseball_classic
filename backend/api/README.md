# backend/api

BaseHaptic MVP 백엔드 API 서버입니다.

## Stack
- Python 3.11+
- FastAPI
- SQLAlchemy
- SQLite (dev 기본값) / Supabase Postgres (운영/연동)

## Quick Start
```bash
cd <repo-root>
python -m venv .venv
. .venv/Scripts/Activate.ps1
cd backend/api
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload --port 8080
```

- API 문서: `http://localhost:8080/docs`
- 상세 구조/연동 가이드는 `../README.md` 참고

## Supabase 전환
- `BASEHAPTIC_DATABASE_URL`은 `postgresql+psycopg://...` 형식으로 설정해야 합니다.
- Session Pooler 사용 예:
  - `postgresql+psycopg://postgres.<project-ref>:<password>@aws-<region>.pooler.supabase.com:5432/postgres`
- Direct host 사용 예:
  - `postgresql+psycopg://postgres:<password>@db.<project-ref>.supabase.co:5432/postgres`


## Recent Changes (2026-03-07)

- Snapshot ingest now supports and persists `startTime` (`HH:MM`) to `games.start_time`.
- `GET /games` now supports `date` query filtering by game-id date prefix.
- Event normalization now includes `PITCHER_CHANGE` aliases:
  - `PITCHER_CHANGE`, `PITCHING_CHANGE`, `PITCHER_SUBSTITUTION`
- Event normalization now includes `HALF_INNING_CHANGE` aliases:
  - `HALF_INNING_CHANGE`, `OFFENSE_CHANGE`
- `game_events` now stores `pitcher` and `batter`.
- Duplicate ingest path backfills missing `pitcher`/`batter` values when later snapshots provide them.
- Game-state response normalizes B/S to `0/0` when out count is `>= 3`.
- Added `scripts/import_wbc_schedule.py` for date-based schedule import (WBC/KBO via `--league` or `--schedule-url`).
- Added tests for:
  - `PITCHER_CHANGE` mapping
  - 3-out B/S reset
  - event pitcher/batter backfill
  - `/games?date=` filtering

## Connection Pool Tuning (Session Pooler)
- If you see `MaxClientsInSessionMode: max clients reached`, reduce app-side SQLAlchemy pool size.
- Supported env vars (`BASEHAPTIC_` prefix):
  - `DB_POOL_SIZE` (default: `2`)
  - `DB_MAX_OVERFLOW` (default: `0`)
  - `DB_POOL_TIMEOUT_SEC` (default: `30`)
  - `DB_CONNECT_TIMEOUT_SEC` (default: `10`)
  - `DB_POOL_RECYCLE_SEC` (default: `1800`)
- Recommended start for Railway + Supabase Session Pooler:
  - `BASEHAPTIC_DB_POOL_SIZE=1`
  - `BASEHAPTIC_DB_MAX_OVERFLOW=0`

## Incident Notes (2026-03-14)

- Snapshot ingest lock contention (`/internal/crawler/games/{gameId}/snapshot`)
  - Symptom: intermittent `503`, lock-timeout logs, and delayed game updates.
  - Root cause: concurrent writers for same `gameId` (`schedule import` + live crawler).
  - Backend mitigation:
    - lock-timeout detection and bounded retry loop in ingest path.
    - returns `503 snapshot ingest busy; retry shortly` after retry exhaustion.
    - safe rollback handling when DB connection is already broken.
  - Dispatcher mitigation:
    - `LIVE` games are skipped in schedule-import snapshot sync (live crawler remains the writer).

- Dispatcher duplicate-run pressure
  - Symptom: duplicate dispatcher processes increased duplicate ingest pressure.
  - Mitigation:
    - single-instance lock file support (`--dispatcher-lock-file`).
    - optional leader replica guard (`--leader-replica-id`, env: `DISPATCHER_LEADER_REPLICA_ID`).

- Supabase Session Pooler saturation
  - Symptom: `MaxClientsInSessionMode: max clients reached`.
  - Mitigation:
    - tightened SQLAlchemy pool defaults and env-driven tuning (see section above).
    - operational recommendation: keep backend replicas/workers conservative during high-ingest windows.
