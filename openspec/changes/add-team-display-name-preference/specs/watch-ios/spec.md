## ADDED Requirements

### Requirement: watchOS 팀 표시 방식 적용
watchOS 워치 앱은 iPhone에서 동기화된 팀 표시 방식에 따라 스코어보드 팀 라벨을 표시해야 한다(MUST).

#### Scenario: 팀명 스코어보드
- **GIVEN** iPhone에서 팀명 표시 방식이 동기화되었을 때
- **WHEN** 워치 라이브 경기 화면이 스코어보드를 표시하면
- **THEN** 워치는 "두산" 같은 팀명을 표시한다

#### Scenario: 마스코트명 스코어보드
- **GIVEN** iPhone에서 마스코트명 표시 방식이 동기화되었을 때
- **WHEN** 워치 라이브 경기 화면이 스코어보드를 표시하면
- **THEN** 워치는 "베어스" 같은 마스코트명을 표시한다

#### Scenario: 테마 팀 코드 유지
- **GIVEN** 표시 방식이 변경되었을 때
- **WHEN** 워치가 팀 테마를 결정하면
- **THEN** 워치는 표시명이 아닌 응원팀 코드를 사용한다
