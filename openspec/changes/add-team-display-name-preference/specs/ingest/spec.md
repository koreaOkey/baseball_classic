## ADDED Requirements

### Requirement: 경기 시작 푸시 표시 방식
서버는 경기 시작 visible push를 수신자의 팀 표시 방식 선호에 맞춰 전송해야 한다(MUST).

#### Scenario: 팀명 선호 푸시
- **GIVEN** 수신자의 표시 방식 선호가 팀명일 때
- **WHEN** 응원팀 경기가 시작되어 visible push를 전송하면
- **THEN** 푸시 제목과 본문은 "두산" 같은 팀명을 사용한다

#### Scenario: 마스코트명 선호 푸시
- **GIVEN** 수신자의 표시 방식 선호가 마스코트명일 때
- **WHEN** 응원팀 경기가 시작되어 visible push를 전송하면
- **THEN** 푸시 제목과 본문은 "베어스" 같은 마스코트명을 사용한다

#### Scenario: 선호값 누락
- **GIVEN** 수신자의 표시 방식 선호가 저장되어 있지 않을 때
- **WHEN** 응원팀 경기가 시작되어 visible push를 전송하면
- **THEN** 서버는 팀명 표시 방식을 기본값으로 사용한다
