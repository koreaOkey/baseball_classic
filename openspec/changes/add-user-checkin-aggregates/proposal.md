## Why

향후 "체크인 N회 사용자에게 응원팀 테마 무료 지급" 류의 **체크인 기반 보상 정책**을 빠르게 운영하기 위해, 정책이 확정되기 전에 **사용자 단위 집계 데이터·인덱스만 미리 깐다.** 보상 화면·관리자 API·실제 지급 정책은 본 change scope 밖이며 별도 후속 change로 분리한다.

현재 백엔드는 `cheer_events` raw 테이블과 **팀 단위 집계**(`team_checkin_daily`, `team_checkin_season`)만 갖고 있어 다음과 같은 한계가 있다:

- `cheer_events.user_id`에 인덱스가 없어 사용자별 조회(`WHERE user_id = ?`)가 풀스캔이다. 데이터가 쌓이면 `GET /cheer-events/me`가 느려진다.
- 사용자 단위 시즌·일자 카운트가 raw 테이블에서 매번 `GROUP BY`로 계산된다. 보상 트리거가 자주 호출될수록 비용이 누적된다.
- "시즌 N회 이상" / "연속 N일" / "오늘 첫 체크인" 류 조건을 효율적으로 평가할 인덱스/집계 구조가 없다.

## What Changes

- `cheer_events.user_id` 인덱스 추가 (`idx_cheer_events_user_id`). 이미 존재하는 테이블이므로 `CREATE INDEX IF NOT EXISTS` 패턴으로 멱등 보강.
- 신규 테이블 `user_checkin_daily(user_id, date, count, updated_at)`, PK `(user_id, date)`. 1일 1체크인 정책상 count는 사실상 1이지만 향후 정책 변동에 대비해 컬럼 유지.
- 신규 테이블 `user_checkin_season(user_id, season, count, updated_at)`, PK `(user_id, season)`. 보조 인덱스 `(season, count DESC)`로 "시즌 N회 이상 유저 목록" 쿼리 최적화.
- 검증 워커(`workers/cheer_validator.py::_increment_aggregates`)가 `valid` 판정 시 기존 `team_checkin_*` 갱신 직후 `user_checkin_*`도 동일하게 +1.
- 백필: `init_db()` 단계에서 멱등 backfill 헬퍼 `_ensure_user_checkin_backfill()` 호출. `user_checkin_season` 행이 0건일 때만 raw `cheer_events`(valid 한정)를 스캔해 채움. 이후 startup에서는 no-op.
- 운영 무중단. 스키마 추가만, 기존 컬럼/제약/엔드포인트 변경 없음.

## Capabilities

### Modified Capabilities
- `team-checkin-ranking`: 기존 팀 단위 집계 옆에 **사용자 단위 집계 캐시**를 추가한다. 검증 워커는 `valid` 승격 시 두 차원(팀·사용자)을 동시에 갱신한다.

### Non-Goals (본 change 밖)
- 보상 정책 자체 (몇 회 / 어떤 보상 / 트리거 시점 등) — 정책 확정 후 별도 change.
- 운영자/배치용 admin API (예: `GET /admin/users-checked-in?min=N`) — 보상 정책과 함께 결정.
- 사용자 노출 UI (개인 체크인 통계 화면, 보상 알림 등).
- 1일 1체크인 enforcement 강화 (spec에는 명시되어 있으나 현 검증 워커 미구현, 본 change 밖).
- `user_checkin_*` 데이터의 PII 익명화 정책 — `cheer_events` 90일 익명화와 별개로 별도 논의 필요(집계는 user_id 의존이므로 익명화 시 카운트는 보존하되 user_id를 가명화 토큰으로 치환 등).

## Impact

- **DB (Supabase)**: 인덱스 1개 + 테이블 2개 추가. 백필은 일회성, idempotent.
- **백엔드 (FastAPI)**:
  - `app/models.py`: `UserCheckinDaily`, `UserCheckinSeason` 모델 정의 + `CheerEvent.__table_args__`에 user_id 인덱스 추가.
  - `app/workers/cheer_validator.py::_increment_aggregates`: user 차원 갱신 추가.
  - `app/db.py::init_db`: `_ensure_user_checkin_backfill()` 헬퍼 호출.
- **테스트**: `backend/api/tests/test_api.py`에 (a) valid 승격 시 user 집계가 +1 되는지, (b) 백필 재호출이 카운트를 중복 증가시키지 않는지(멱등) 추가.
- **클라이언트**: 변경 없음. 본 change는 백엔드 단독.
- **운영**: 백필 시간은 데이터 크기 비례. 현재 운영 데이터가 소규모(파일럿 단계)이므로 startup 지연 무시 가능. 미래 데이터가 커지면 별도 admin endpoint로 분리 검토.
