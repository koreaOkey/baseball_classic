# Game State spec — delta

## MODIFIED Requirements

### Requirement: 경기 상태 전이
경기 상태는 SCHEDULED → LIVE → FINISHED 순서로 전이되거나, SCHEDULED/LIVE 에서 terminal 상태인 CANCELED/POSTPONED 로 전이되어야 한다(MUST). 진행도(progress) 가 같거나 더 높은 incoming 상태만 적용한다.

진행도 정의:
- SCHEDULED = 0
- LIVE = 1
- POSTPONED = 2 (terminal)
- CANCELED = 2 (terminal)
- FINISHED = 3 (terminal)

#### Scenario: 정상 전이
- GIVEN 경기 상태가 SCHEDULED일 때
- WHEN 첫 번째 릴레이 데이터가 수신되면
- THEN 상태를 LIVE로 전환한다

#### Scenario: 우천 취소 전이
- GIVEN 경기 상태가 SCHEDULED일 때
- WHEN 크롤러가 `statusInfo="경기취소"` 또는 `cancel=true` 를 감지해 `status=CANCELED` 스냅샷을 전송하면
- THEN 상태를 CANCELED로 전환하고 더 이상 LIVE/FINISHED로 되돌리지 않는다(MUST).

#### Scenario: 경기 연기 전이
- GIVEN 경기 상태가 SCHEDULED일 때
- WHEN 크롤러가 `statusInfo="경기연기"` 또는 `statusCode∈{POSTPONED,SUSPENDED,DELAYED}` 를 감지해 `status=POSTPONED` 스냅샷을 전송하면
- THEN 상태를 POSTPONED로 전환한다.

#### Scenario: terminal 상태에서 회귀 거부
- GIVEN 경기 상태가 CANCELED 또는 POSTPONED 일 때
- WHEN 같거나 낮은 진행도의 incoming 상태(SCHEDULED, LIVE)가 도착하면
- THEN 기존 terminal 상태를 유지한다.

#### Scenario: terminal 상태에서 FINISHED 승격
- GIVEN 경기 상태가 POSTPONED 일 때 (예: 일시 중단되었다가 재개되어 종료까지 진행된 경우)
- WHEN `status=FINISHED` 스냅샷이 도착하면
- THEN 진행도가 더 높으므로 상태를 FINISHED 로 전환한다.

### Requirement: 경기 상태 저장 가능 집합
DB 레이어(`public.games.status`) 는 다음 5개 값만 저장할 수 있어야 한다(MUST):
`'LIVE'`, `'SCHEDULED'`, `'FINISHED'`, `'CANCELED'`, `'POSTPONED'`.

#### Scenario: 미허용 값 거부
- GIVEN `games.status` 컬럼에 위 5개 외의 값을 INSERT/UPDATE 하려 할 때
- WHEN `games_status_check` 제약이 평가되면
- THEN PostgreSQL 이 check constraint violation 으로 거부한다.
