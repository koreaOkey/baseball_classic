## Why
5경기 동시 ingest 시 WebSocket 클라이언트 연결 실패(60%), broadcast 누락, timeout 등이 발생.
근본 원인: uvicorn 단일 워커 + WS on-connect sync DB 쿼리가 이벤트루프를 block + 좀비 트랜잭션 재발 위험.

## What Changes
- insert_events IN 쿼리를 100건 단위로 chunking하여 Supabase 대형 쿼리 부하 경감
- 전체 중복 이벤트 감지 시 COUNT 기반 early return (full object 로드 스킵)
- WS on-connect DB 쿼리를 asyncio.to_thread로 비동기화하여 이벤트루프 blocking 해소
- uvicorn 워커 1 → 3으로 증설 (멀티 프로세스)
- Supabase idle_in_transaction_session_timeout 60초 설정 (좀비 세션 자동 종료)

## Capabilities
### Modified Capabilities
- `backend-api`: insert_events chunked IN 쿼리 + COUNT 기반 fast path
- `backend-api`: WebSocket 핸들러 on-connect DB 쿼리 async화 (games, team-records)
- `infra`: uvicorn --workers 3 (NIXPACKS_START_CMD)
- `infra`: Supabase idle_in_transaction_session_timeout = 60s

## Impact
- backend/api/app/services.py — _chunked_count, _chunked_needs_merge, _chunked_load 추가, insert_events fast path
- backend/api/app/main.py — _load_game_initial_data, _load_team_record_initial_data 추가, asyncio.to_thread 적용
- Railway 환경변수 — NIXPACKS_START_CMD 추가
- Supabase DB — idle_in_transaction_session_timeout 설정

## Results (부하 테스트)
### 5경기 동시 ingest (테스트 스크립트: scripts/test_concurrent_ingest.py)
- 변경 전: 4/5 성공, WOSK timeout (좀비 세션 원인)
- 변경 후: 5/5 성공, 평균 6.31초

### 5경기 x 10 클라이언트 = 50 WebSocket (테스트 스크립트: scripts/test_ws_load.py)
- 변경 전: 20/50 연결, 6/20 broadcast 수신, ingest 7~19초
- 변경 후: 50/50 연결, 50/50 broadcast 수신, ingest 0.9~1.1초
