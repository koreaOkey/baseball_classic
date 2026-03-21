# Team Records Specification

## Purpose
KBO 팀 순위와 기록을 수집, 저장, 실시간 조회하는 기능.

## Requirements

### Requirement: 팀 기록 수집
시스템은 KBO 팀 순위 데이터를 크롤러를 통해 수집해야 한다(MUST).

#### Scenario: 팀 기록 인제스트
- GIVEN 크롤러가 팀 순위 데이터를 수집했을 때
- WHEN POST /internal/crawler/team-records 를 호출하면
- THEN team_record 테이블에 최신 순위 데이터를 upsert 한다

### Requirement: 팀 기록 조회
사용자는 팀별 순위와 기록을 조회할 수 있어야 한다(MUST).

#### Scenario: 특정 팀 기록 조회
- GIVEN 팀 기록이 저장되어 있을 때
- WHEN GET /team-records/{teamId} 를 호출하면
- THEN 해당 팀의 순위, 승/패/무, 승률 등을 반환한다

### Requirement: 팀 기록 실시간 업데이트
팀 순위 변동 시 구독 클라이언트에 실시간 알림을 전달해야 한다(SHOULD).

#### Scenario: WebSocket 팀 기록 스트림
- GIVEN 클라이언트가 팀 기록을 구독 중일 때
- WHEN WS /ws/team-records/{teamId} 에 변경이 발생하면
- THEN 업데이트된 순위 데이터를 Push로 전달한다
