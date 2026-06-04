## MODIFIED Requirements

### Requirement: 팀별 체크인 집계 캐시
시스템은 팀별 일/주/시즌 단위 valid 체크인 누적 카운트를 집계 캐시 테이블에 보관해야 한다(SHALL). **또한 동일 검증 시점에 사용자별 일/시즌 단위 valid 체크인 누적 카운트도 집계 캐시 테이블에 보관해야 한다(SHALL).** 캐시는 워커에 의해 갱신된다. 팀 집계는 **iOS와 Android 사용자 이벤트를 합산한 단일 카운트**여야 한다(SHALL). raw 이벤트 테이블에는 platform 컬럼이 보존되어 사후 분리 분석이 가능해야 한다.

#### Scenario: 일간 팀 집계
- **WHEN** 워커가 valid 이벤트를 처리한다
- **THEN** 해당 `(team_code, date)` 카운트를 +1 증가시킨다
- **AND** 이벤트의 platform(`ios`/`android`)에 무관하게 동일한 카운트로 집계된다

#### Scenario: 시즌 팀 집계
- **WHEN** 일간 팀 집계가 갱신된다
- **THEN** 해당 팀의 시즌 누적 카운트도 +1 증가시킨다
- **AND** iOS·Android 이벤트는 단일 카운트로 합산된다

#### Scenario: 일간 사용자 집계
- **WHEN** 워커가 valid 이벤트를 처리한다
- **THEN** 해당 `(user_id, date)` 카운트를 +1 증가시킨다
- **AND** 같은 트랜잭션 안에서 팀 집계와 함께 갱신된다

#### Scenario: 시즌 사용자 집계
- **WHEN** 일간 사용자 집계가 갱신된다
- **THEN** 해당 사용자의 시즌 누적 카운트도 +1 증가시킨다

#### Scenario: 사용자 집계 백필
- **WHEN** 백엔드가 startup 한다
- **AND** `user_checkin_season` 테이블에 행이 0건이다
- **THEN** 기존 `cheer_events` 중 validity_status가 `valid`인 이벤트를 KST 기준 (user_id, date) / (user_id, season)으로 GROUP BY 하여 두 집계 테이블에 일괄 INSERT 한다
- **AND** 이미 백필이 수행된 이후 startup에서는 백필을 스킵한다

#### Scenario: 사용자별 시즌 카운트 조회 최적화
- **WHEN** 향후 보상 트리거가 "시즌 N회 이상 사용자 목록"을 조회한다
- **THEN** `idx_user_checkin_season_count(season, count DESC)` 인덱스가 사용되어야 한다(SHALL)
