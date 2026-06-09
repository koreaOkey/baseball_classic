# Mobile iOS Delta

## ADDED Requirements

### Requirement: 응원팀 일정 달력 팝업
iOS 앱은 홈의 날짜/달력 영역을 누르면 응원팀 일정을 달력 형태로 보여줘야 한다(MUST).

#### Scenario: 달력 팝업 표시
- GIVEN 사용자가 응원팀을 선택했을 때
- WHEN 홈의 날짜/달력 영역을 누르면
- THEN 응원팀 일정 달력 팝업이 표시된다
- AND 경기 있는 날짜는 달력 칸에 표시된다

#### Scenario: 날짜 선택
- GIVEN 달력 팝업에 경기 있는 날짜가 표시될 때
- WHEN 사용자가 특정 날짜를 선택하면
- THEN 해당 날짜의 응원팀 경기 목록이 하단에 표시된다

#### Scenario: 경기 선택
- GIVEN 선택 날짜에 응원팀 경기가 있을 때
- WHEN 사용자가 경기 row를 누르면
- THEN 기존 경기 선택 흐름으로 이동한다

### Requirement: 일정 달력 캐시
iOS 앱은 일정 달력 데이터를 로컬에 저장하고 네트워크 실패 시 사용할 수 있어야 한다(MUST).

#### Scenario: 캐시 fallback
- GIVEN 일정 범위 조회에 실패했지만 같은 범위의 캐시가 있을 때
- WHEN 달력 팝업이 열리면
- THEN 캐시된 일정으로 달력을 표시한다
