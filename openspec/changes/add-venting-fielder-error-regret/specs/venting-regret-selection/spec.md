# venting-regret-selection — 수비 실책 fielder 후보 델타

## ADDED Requirements

### Requirement: 수비 실책 fielder 후보
regret 후보 선별은 이벤트 metadata의 수비 실책(`isError`)을 수비팀(=패배팀)의 fielder 후보로 MUST 산출하고, 룸·완파 화면에는 포지션 역할 레이블만 노출(실명 방화벽)해야 한다.

부연: 실명은 캐시에 저장하지 않고, 클라이언트가 선택 화면에서만 타순으로 박스스코어와 조인해 표시한다.

#### Scenario: 실책을 실책 담당 포지션에 귀속
- GIVEN 패배팀 수비 중 이벤트 payload에 isError=true, errorPosition="유격수"가 있을 때
- WHEN regret 후보를 선별하면
- THEN kind="fielder", role_label="유격수", event_type="ERROR" 후보를 만든다
- AND 라인업(game_lineup_slots)에서 그 팀 유격수의 타순을 해소해 batting_order로 싣는다
- AND 클라이언트는 그 batting_order로 박스스코어를 조인해 선택 화면에만 실명을 표시한다

#### Scenario: 실책성 실점은 투수보다 fielder 우선
- GIVEN 실책이 SCORE(실점)로 분류된 이벤트일 때
- WHEN 후보를 선별하면
- THEN 그 이벤트는 fielder 후보로만 귀속되고 같은 이벤트로 투수 후보를 중복 생성하지 않는다

#### Scenario: 포지션 미상 실책은 익명 유지
- GIVEN isError=true 이나 errorPosition이 없을 때
- WHEN 후보를 선별하면
- THEN role_label="수비수", batting_order 없음으로 익명 유지한다

#### Scenario: 캐시에 실명 미저장
- WHEN fielder 후보를 캐시에 저장하면
- THEN 역할 레이블·타순·사유만 저장하고 선수 실명은 저장하지 않는다
