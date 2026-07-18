# Tasks

## 1. 원인 진단
- [x] 1.1 Railway 응답 시간 확인 (p50 692ms / p95 1.46s, CPU 0.7%·메모리 0.49GB로 자원 문제 배제)
- [x] 1.2 로그 패턴 분석: `/health` 27ms · Redis 캐시 히트 7ms vs DB 경유 610–930ms → 요청당 DB 연결 비용으로 특정
- [x] 1.3 `db.py` NullPool 강제 경로(:6543) 확인 — 2026-04-15 도입 이후 지속

## 2. 구현
- [x] 2.1 `db.py`: transaction 풀러에서도 QueuePool 사용, `BASEHAPTIC_DB_FORCE_NULL_POOL` 롤백 플래그 분기
- [x] 2.2 `config.py`: `db_pool_size` 4 · `db_max_overflow` 4 · `db_force_null_pool` 추가

## 3. 검증
- [x] 3.1 backend 97 tests passed
- [x] 3.2 풀 클래스 3시나리오 확인 (6543=QueuePool, force=NullPool, 5432=QueuePool, prepare_threshold=None 유지)
- [x] 3.3 배포 후 재측정 완료 (2026-07-18 08:55 KST): p50 692→312ms, p95 1460→833ms. `/app-config` 610→~225ms, `/games` 미스 690→~310ms. 에러 로그 없음, 워커 2개 정상 기동. 잔여 ~225ms는 도쿄 DB 왕복(RTT×2) — 리전 이동 없이는 하한.
