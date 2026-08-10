## ADDED Requirements

### Requirement: 분풀이 지표 수집 endpoint

시스템은 분풀이 지표 이벤트를 수집하는 endpoint를 제공해야 한다(SHALL). 수집 이벤트 타입은 `room_enter`·`destroy_complete`·`retry_prompt_shown`·`retry_ad_start` 4종이며, 각 이벤트는 `entry_source`(home_card/live_button/loss_prompt)와 **유저 응원팀(team)**을 포함해야 한다. 저장은 신규 테이블(`venting_event`)로 하며 기존 테이블을 변경하지 않는다.

#### Scenario: 지표 이벤트 저장
- **WHEN** 클라이언트가 4종 중 한 이벤트를 `entry_source`·`team`과 함께 전송한다
- **THEN** 시스템은 이벤트를 `venting_event`에 저장한다

#### Scenario: 응원팀 태그 필수
- **WHEN** 지표 이벤트가 수신된다
- **THEN** 저장 레코드는 유저 응원팀(team)을 포함하여, 이후 팀별 집계가 가능하다

#### Scenario: 지표 수집은 사용자 플로우 비차단
- **WHEN** 지표 저장이 실패한다
- **THEN** 시스템은 오류를 사용자 분풀이 플로우에 전파하지 않는다(best-effort)

### Requirement: 팀별 분풀이 실행 랭킹 집계

시스템은 "어떤 응원팀이 분풀이 방을 가장 많이 실행했는가"를 산출하기 위해, 원시 지표 이벤트를 팀 단위 일/시즌 집계(`venting_team_daily`/`venting_team_season`)로 롤업해야 한다(SHALL). 이 랭킹은 팬덤 인게이지먼트 지표이며 특정 선수를 대상으로 하지 않는다.

#### Scenario: 팀 일별 집계 롤업
- **WHEN** 특정 일자의 지표 이벤트가 팀별로 집계된다
- **THEN** 시스템은 `venting_team_daily`에 팀별 실행 카운트를 롤업한다

#### Scenario: 팀 시즌 랭킹 산출
- **WHEN** 팀 시즌 랭킹이 요청된다
- **THEN** 시스템은 `venting_team_season` 집계 기준으로 팀별 순위를 반환하며, 선수 개인은 포함하지 않는다

### Requirement: 지표 능력 다크 배포 게이트

시스템은 지표 수집·집계 능력을 `BASEHAPTIC_VENTING_BACKEND_ENABLED`(기본 false)로 게이트해야 한다(SHALL). Firebase 등 외부 분석 SDK를 사용하지 않는다.

#### Scenario: 마스터 플래그 OFF
- **WHEN** `BASEHAPTIC_VENTING_BACKEND_ENABLED`가 false다
- **THEN** 지표 endpoint·집계는 비활성이며 기존 운영 경로에 영향을 주지 않는다
