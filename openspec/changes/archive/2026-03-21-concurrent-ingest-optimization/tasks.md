## 1. insert_events 쿼리 최적화
- [x] 1.1 IN 쿼리 100건 단위 chunking (_chunked_count, _chunked_load)
- [x] 1.2 전체 중복 감지 시 COUNT 기반 early return (full object 로드 스킵)
- [x] 1.3 backfill 필요 여부 체크 (_chunked_needs_merge: null pitcher/batter, OTHER 타입)
- [x] 1.4 기존 테스트 24건 통과 확인

## 2. WebSocket on-connect 비동기화
- [x] 2.1 _load_game_initial_data: sync DB 쿼리를 별도 함수로 분리
- [x] 2.2 _load_team_record_initial_data: sync DB 쿼리를 별도 함수로 분리
- [x] 2.3 asyncio.to_thread로 threadpool 위임 적용
- [x] 2.4 기존 테스트 24건 통과 확인

## 3. 인프라 설정
- [x] 3.1 uvicorn --workers 3 설정 (NIXPACKS_START_CMD)
- [x] 3.2 Supabase idle_in_transaction_session_timeout = 60s 설정
- [x] 3.3 좀비 세션(PID 969773, idle in transaction 19분+) 수동 종료

## 4. 부하 테스트
- [x] 4.1 5경기 동시 ingest 테스트 스크립트 작성 (scripts/test_concurrent_ingest.py)
- [x] 4.2 5경기 x N클라이언트 WebSocket 부하 테스트 스크립트 작성 (scripts/test_ws_load.py)
- [x] 4.3 5경기 동시 ingest 5/5 성공 확인
- [x] 4.4 50 WebSocket 클라이언트 50/50 연결 + 50/50 broadcast 수신 확인
