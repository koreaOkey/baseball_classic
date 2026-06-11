## ADDED Requirements

### Requirement: 홈 응원팀 일정 팝업
iOS 모바일 앱은 홈 화면에서 사용자가 응원팀 경기 일정을 팝업으로 확인할 수 있는 진입점을 제공해야 한다(MUST).

#### Scenario: 달력 영역 탭
- **GIVEN** 사용자가 iOS 모바일 홈 화면을 보고 있을 때
- **WHEN** 홈의 달력 영역을 탭하면
- **THEN** 응원팀 일정 팝업이 표시된다

#### Scenario: iOS 일정 카드 표시
- **GIVEN** 응원팀 일정 팝업에 경기 목록이 로드되었을 때
- **WHEN** 팝업이 렌더링되면
- **THEN** iOS 모바일 앱은 경기별 날짜, 시간, 상대팀, 홈/원정, 상태를 카드 형태로 표시한다

#### Scenario: iOS 경기 항목 선택
- **GIVEN** 응원팀 일정 팝업에 경기 항목이 표시되어 있을 때
- **WHEN** 사용자가 경기 항목을 탭하면
- **THEN** iOS 모바일 앱은 기존 경기 상세 화면 진입 동작을 실행한다

#### Scenario: iOS 워치 영향 없음
- **GIVEN** 사용자가 응원팀 일정 팝업을 열거나 닫을 때
- **WHEN** Apple Watch 동기화 상태가 존재하면
- **THEN** iOS 모바일 앱은 기존 Apple Watch 동기화 상태를 변경하지 않는다
