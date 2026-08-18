# venting-regret-selection — 선택 화면 실명 표기 델타

## MODIFIED Requirements

### Requirement: 선택 화면 실명 표기
대상 선택 화면의 후보 행 제목은 실명이 해소된 경우 **"역할(실명)"** 형식으로 MUST 표시하고
(예: "9번 타자(구자욱)"), 실명이 없으면 역할 레이블만 표시한다. 실명 노출은 선택 화면
한정이며 룸·완파·워치 화면은 익명(역할 레이블)을 유지해야 한다.

#### Scenario: 서버 TOP5 후보 실명 표기
- GIVEN 서버 regret-top5 항목이 batting_order로 박스스코어와 조인돼 실명 "구자욱"이 해소됐을 때
- WHEN 선택 화면 후보 행을 렌더하면
- THEN 제목을 "9번 타자(구자욱)" 형식으로 표시한다

#### Scenario: 라이브 진입 후보 실명 표기
- GIVEN 라이브 진입에서 중계 이벤트의 batter/pitcher 이름이 있을 때
- WHEN LiveRegretProvider가 후보를 만들면
- THEN playerName에 그 이름을 싣고 선택 화면 제목을 "역할(실명)"으로 표시한다

#### Scenario: 실명 미해소 시 역할만 표시
- GIVEN 박스스코어 조인 실패 등으로 playerName이 비어 있을 때
- WHEN 선택 화면 후보 행을 렌더하면
- THEN 역할 레이블만 표시한다(괄호 없음)

#### Scenario: 같은 선수 중복 제거
- GIVEN 같은 선수(같은 실명)의 아쉬운 사건이 여러 건일 때
- WHEN 라이브 진입 후보를 선별하면
- THEN 가장 최근 사건 1건만 남긴다(실명이 없으면 역할 레이블 기준)

#### Scenario: 룸·완파 화면 익명 유지
- GIVEN 실명이 해소된 후보를 선택해 분풀이를 시작했을 때
- WHEN 룸·완파·워치 화면을 렌더하면
- THEN VentingTarget.roleLabel/eventDescription만 읽어 실명을 표시하지 않는다
