# Lock Screen Live Score

## ADDED Requirements

### Requirement: Test tool applies selected preview style to start/highlight/simulation

WatchTestScreen의 미리보기 스타일 선택은 "Live Score 시작", "득점 강조", "자동 시뮬레이션" 게시 시 `forceStyle`로 적용되어야 한다(SHALL). 선택은 승격 조건·사용자 설정과 무관하게 해당 스타일을 강제 게시해야 한다(MUST).

#### Scenario: Promoted 선택 후 자동 시뮬레이션

- **WHEN** 테스트 도구에서 "Promoted 버전"을 선택하고 자동 시뮬레이션을 시작할 때
- **THEN** 시뮬레이션 각 이벤트가 `forceStyle = PROMOTED`로 게시된다
- **AND** 승격 미지원 기기에서는 비승격 시스템 템플릿(BigText)으로 표시된다(회귀 없음)

#### Scenario: Ongoing 선택 후 Live Score 시작

- **WHEN** 테스트 도구에서 "Ongoing 버전"을 선택하고 "Live Score 시작"을 누를 때
- **THEN** 이전 리치 커스텀 카드(CLASSIC)가 `forceStyle = CLASSIC`으로 게시된다

#### Scenario: 기본 선택값

- **WHEN** 테스트 화면을 처음 열 때
- **THEN** 선택 스타일 기본값은 PROMOTED이다

#### Scenario: 자동 시뮬레이션이 라이브 스코어 카드를 자동 활성화

- **WHEN** 미리보기가 비활성 상태에서 "자동 시뮬레이션 시작"을 누를 때
- **THEN** "Live Score 시작"을 따로 누르지 않아도 선택 스타일로 라이브 스코어 카드가 활성화된다
- **AND** 시뮬레이션의 각 이벤트마다 선택 스타일로 카드가 갱신되고 득점·홈런·안타는 강조된다
