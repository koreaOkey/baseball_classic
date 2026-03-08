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
- Added `scripts/import_wbc_schedule.py` for date-based WBC schedule import.
- Added tests for:
  - `PITCHER_CHANGE` mapping
  - 3-out B/S reset
  - event pitcher/batter backfill
  - `/games?date=` filtering
