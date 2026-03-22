## 1. 완료된 최적화
- [x] 1.1 insert_events IN 쿼리 100건 단위 chunking
- [x] 1.2 COUNT 기반 중복 early return (backfill/OTHER 타입 안전 체크 포함)
- [x] 1.3 WS on-connect DB 쿼리 asyncio.to_thread 비동기화
- [x] 1.4 event_bus broadcast asyncio.gather 병렬화
- [x] 1.5 Redis 캐시로 WS on-connect DB 쿼리 제거
- [x] 1.6 uvicorn --workers 3 설정
- [x] 1.7 Supabase idle_in_transaction_session_timeout = 60s
- [x] 1.8 좀비 세션 발견 및 제거

## 2. 부하 테스트 완료
- [x] 2.1 5경기 동시 ingest 테스트 (scripts/test_concurrent_ingest.py)
- [x] 2.2 50/100/200/500/1000/1200/1400/1500/2000 클라이언트 부하 테스트 (scripts/test_ws_load.py)
- [x] 2.3 한계선 측정: 500명 무부하, 1000명 부하 시작, 1400명 연결 실패

## 3. 향후 과제 (1000명+ 지원 필요 시)
- [ ] 3.1 Railway Hobby 플랜 전환 + replica 2~3 스케일아웃
- [ ] 3.2 uvicorn 워커 수 조정 (메모리 여유 확인 후 5~6)
- [ ] 3.3 broadcast batching 검토
- [ ] 3.4 WebSocket 서버 분리 (장기 과제)
