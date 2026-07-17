## Why

DB를 거치는 모든 API가 프로덕션에서 ~600–700ms의 고정 지연을 보였다 (Railway p50 692ms / p95 1.46s, `/health` 27ms·Redis 캐시 히트 7ms와 대비). 원인은 Supavisor Transaction 풀러(:6543) 사용 시 `db.py`가 **NullPool** 을 강제해 매 요청마다 원거리(ap-northeast-1) Supabase 로 TCP+TLS+인증 핸드셰이크(~600ms)를 새로 치르는 구조. 쿼리 1개짜리 `/app-config` 가 610ms, `/games` 캐시 미스가 690ms로 연결 비용이 응답 시간을 지배했다.

NullPool 은 prepared statement 캐시 충돌 회피 목적이었으나, 실제 충돌 원인은 `prepare_threshold=None` 으로 이미 별도 차단 중이며 Supavisor Transaction 모드는 서버 측에서 트랜잭션 단위로 다중화하므로 클라이언트가 연결을 유지해도 안전하다.

## What Changes

### A. 클라이언트 커넥션 풀 유지 (`backend/api/app/db.py`)
- `:6543` transaction 풀러에서도 NullPool 대신 기본 QueuePool 사용 (pool_pre_ping·pool_recycle 1800s 기존 유지).
- `prepare_threshold=None` (prepared statement 비활성화)은 기존대로 유지.
- 롤백 안전장치: `BASEHAPTIC_DB_FORCE_NULL_POOL=true` 환경변수로 이전 동작(요청마다 새 연결) 복원 가능 — 코드 재배포 없이 전환.

### B. 풀 기본값 상향 (`backend/api/app/config.py`)
- `db_pool_size` 1 → 4, `db_max_overflow` 0 → 4. 기존 기본값(1/0)은 연결이 1개뿐이라 동시 요청이 풀 대기로 직렬화되는 문제가 있었다.
- uvicorn 2 workers × (4+4) = 프로세스당 최대 8, 총 16 연결 상한 — Supavisor 클라이언트 한도 대비 여유.
- `db_force_null_pool: bool = False` 설정 추가.

## Capabilities

### Modified Capabilities
- `backend-api`: transaction 풀러 사용 시에도 DB 연결을 재사용해 요청당 재접속 비용 없이 응답해야 하며, 환경변수로 NullPool 동작을 강제 복원할 수 있어야 한다.

## Impact

- 성능(예상): DB 경유 엔드포인트 p50 ~690ms → 워밍 연결 재사용 시 ~150–250ms (pre_ping 1 RTT + 쿼리 RTT). 배포 후 Railway `http_response_time` 으로 검증 필요.
- Backend: db.py·config.py 2파일 — 97 tests passed. 인프라·환경변수·DATABASE_URL 변경 없음(구조 유지).
- 풀 클래스 검증: 6543 기본=QueuePool(size 4)·force 플래그=NullPool·5432=QueuePool 3시나리오 확인.
- DB Migration 불필요. 롤백: `BASEHAPTIC_DB_FORCE_NULL_POOL=true` 설정 후 재시작.
