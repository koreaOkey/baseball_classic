# AGENTS.md

## Purpose
이 파일은 다음 작업 세션에서 빠르게 문맥을 복구하기 위한 프로젝트 운영 메모입니다.

## Current Backend State (as of 2026-02-18)
- Backend stack: FastAPI + SQLAlchemy (`backend/api`)
- DB target: Supabase Postgres (Session Pooler)
- `.env` DB URL must use `postgresql+psycopg://...` scheme
- Crawler ingest endpoint:
  - `POST /internal/crawler/games/{gameId}/snapshot`
  - Header: `X-API-Key`

## Verified Results
- Mock data source: `data/mock_baseball/20250902WOSK02025`
- Ingest success:
  - First load: `receivedEvents=519`, `insertedEvents=519`
  - Re-ingest: `insertedEvents=0`, `duplicateEvents=519`
- Supabase check:
  - `public.games` = 1
  - `public.game_events` = 519

## Important Fixes Applied
- Supabase `jsonb` compatibility fix:
  - `backend/api/app/models.py`
    - `GameEvent.payload_json` mapped to JSON type
  - `backend/api/app/services.py`
    - store metadata dict directly (no `json.dumps`)

## Key Files To Review First Tomorrow
- `Daily/2026_02_18.md`
- `Test/README.md`
- `Test/supabase_result_queries.sql`
- `backend/api/.env` (local only, do not commit)

## Known Gap / Next Task
- Current crawler code does not yet POST directly to backend ingest API.
- Next implementation target:
  - add direct `crawler -> backend` sender module/options.

## Quick Re-Verification Checklist
1. Run backend (`backend/api`) with current `.env`.
2. Send mock snapshot/events to ingest API.
3. Run queries in `Test/supabase_result_queries.sql`.
4. Confirm counts and duplicate handling.

