# Crawling Delta

## ADDED Requirements

### Requirement: 시즌 미래 일정 선적재
시스템은 공개된 KBO 미래 일정을 지정 종료일까지 수집하여 백엔드에 저장해야 한다(MUST).

#### Scenario: 지정 종료일까지 스케줄 수집
- GIVEN 관리자가 미래 일정 수집 종료일을 지정했을 때
- WHEN 디스패처가 일일 스케줄 import를 실행하면
- THEN 오늘부터 지정 종료일까지의 각 날짜 스케줄을 조회한다
- AND 조회된 경기 스냅샷을 백엔드에 저장한다

#### Scenario: 경기 없는 날짜 처리
- GIVEN 특정 날짜에 공개된 KBO 경기가 없을 때
- WHEN 디스패처가 해당 날짜를 조회하면
- THEN 실패로 간주하지 않고 다음 날짜 수집을 계속한다

### Requirement: 9월 일정 확장 갱신
시스템은 지정된 시작일 이후부터 확장된 미래 일정 범위를 반복 갱신할 수 있어야 한다(MUST).

#### Scenario: 확장 갱신 시작일 이전
- GIVEN 확장 갱신 시작일이 아직 도래하지 않았을 때
- WHEN 디스패처가 refresh import를 판단하면
- THEN 당일 스케줄 갱신만 수행한다

#### Scenario: 확장 갱신 시작일 이후
- GIVEN 확장 갱신 시작일이 도래했을 때
- WHEN 디스패처가 refresh import를 판단하면
- THEN 오늘부터 지정된 확장 종료일까지의 스케줄을 다시 조회한다
- AND 새로 공개되거나 변경된 미래 일정을 백엔드에 반영한다
