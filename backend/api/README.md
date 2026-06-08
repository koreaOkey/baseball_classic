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

## Staging iOS/watchOS Test Backend

Debug 빌드의 iOS 앱과 watchOS 앱은 Xcode build setting으로 staging Railway 백엔드를 바라보게 할 수 있습니다.
Release 빌드는 운영 Railway URL을 사용합니다.

staging 인프라는 운영과 같은 형태로 구성하되, Railway/Supabase/Redis/API key는 모두 별도 리소스를 사용합니다.
설정과 검증 절차는 `infra/staging/README.md`를 기준으로 합니다.

```bash
xcodebuild -project ios/BaseHaptic.xcodeproj -scheme BaseHaptic -configuration Debug \
  BASEHAPTIC_STAGING_BACKEND_BASE_URL=https://<staging-backend>.up.railway.app \
  BASEHAPTIC_STAGING_BACKEND_WS_URL=wss://<staging-backend>.up.railway.app \
  BASEHAPTIC_STAGING_SUPABASE_URL=https://<staging-project>.supabase.co \
  BASEHAPTIC_STAGING_SUPABASE_ANON_KEY=<staging-anon-key>
```

staging URL을 주입하지 않으면 앱은 로컬 백엔드(`http://localhost:8080`)로 폴백합니다. Supabase 설정은 Debug 빌드에서 staging 값을 주입하고, Release 빌드는 운영 Supabase 설정을 사용합니다.

로컬 백엔드에 네이버 릴레이 형태의 테스트 경기를 한 번 주입하려면:

```bash
cd backend/api
uvicorn app.main:app --host 0.0.0.0 --port 8080
python scripts/simulate_crawler.py --naver-pitch-detail-once --game-id 20260607KTSK02026
```

물리 iPhone/Apple Watch에서 로컬 백엔드를 직접 테스트할 때는 `localhost` 대신 Mac LAN IP 또는 터널 URL을 빌드 설정으로 덮어씁니다.

```bash
xcodebuild -project ios/BaseHaptic.xcodeproj -scheme BaseHaptic -configuration Debug \
  BACKEND_BASE_URL=http://<mac-lan-ip>:8080 \
  BACKEND_WS_URL=ws://<mac-lan-ip>:8080
```

## Railway Deploy Note (Python/mise)
- Pin Python version with `.python-version` to avoid unstable latest builds.
- Recommended Railway service variable: `RAILPACK_PYTHON_VERSION=3.12.8`.
- Do not install Python again via `RAILPACK_PACKAGES` (remove `python` if present).

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
  - `DB_POOL_SIZE` (default: `1`)
  - `DB_MAX_OVERFLOW` (default: `0`)
  - `DB_POOL_TIMEOUT_SEC` (default: `30`)
  - `DB_CONNECT_TIMEOUT_SEC` (default: `10`)
  - `DB_POOL_RECYCLE_SEC` (default: `1800`)
- Recommended start for Railway + Supabase Session Pooler:
  - `BASEHAPTIC_DB_POOL_SIZE=1`
  - `BASEHAPTIC_DB_MAX_OVERFLOW=0`

## Redis Pub/Sub (Multi-Instance Live Fanout)
- Optional env vars (`BASEHAPTIC_` prefix):
  - `REDIS_URL` (e.g. Railway Redis connection URL)
  - `REDIS_PUBSUB_CHANNEL` (default: `basehaptic:live_events`)
- Behavior:
  - if `REDIS_URL` is empty, backend uses in-memory `event_bus` only (single-instance mode)
  - if `REDIS_URL` is set, ingest broadcasts are also published to Redis and re-fanned out by all backend instances

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

## Recent Changes (2026-03-21)

- Team-record ingest behavior was changed from unconditional overwrite to change-aware upsert:
  - unchanged rows are skipped (no-op re-ingest does not increase `upsertedRecords`)
  - only rows with meaningful field changes are updated
- Added team-record realtime stream endpoint:
  - `WS /ws/team-records/{teamId}?categoryId=kbo&seasonCode=YYYY`
  - sends current team-record snapshot on connect
  - pushes update messages only when that team row changes
- Team-record ingest now broadcasts changed team rows only (reduced unnecessary push/load).
- Added/updated tests for:
  - no-op re-ingest (`upsertedRecords=0`)
  - websocket initial snapshot + changed-row push delivery
