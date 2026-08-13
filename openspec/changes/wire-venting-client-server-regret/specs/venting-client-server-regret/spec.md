# venting-client-server-regret

## ADDED Requirements

### Requirement: 클라이언트는 서버 regret-top5를 사용한다
The client SHALL fetch venting candidates from `GET /games/{id}/venting/regret-top5` and fall
back to the local rule engine only when the server returns no items. (서버 items가 있으면
그것으로 컨텍스트를 조립하고, 비었거나 실패면 로컬 규칙으로 폴백한다.)

#### Scenario: 서버 items 사용
- **WHEN** 서버가 items를 반환할 때
- **THEN** 서버 role_label·inning·reason으로 후보를 구성하고 manager를 6번째로 붙인다

#### Scenario: 폴백
- **WHEN** 서버 응답 실패 또는 items 없음
- **THEN** 로컬 `LiveRegretProvider`로 폴백해 UI가 정상 동작한다

### Requirement: 실명은 선택 화면에서만 결합한다
The client SHALL resolve real player names from the boxscore for the selection screen only, and
SHALL NOT expose names in the room or destroyed screens. (실명은 박스스코어 조인으로 선택
화면 TargetRow에만 표기하고, 룸·완파 화면은 역할 레이블만 노출한다.)

#### Scenario: 선택 화면 실명
- **WHEN** 서버 항목의 kind·team_side·타순/등판순서로 박스스코어를 조인해 실명을 찾았을 때
- **THEN** 선택 화면에 "5번 타자 이선우"처럼 표기한다

#### Scenario: 룸·완파 익명
- **WHEN** 룸·완파 화면을 렌더할 때
- **THEN** 역할 레이블만 노출하고 실명은 표시하지 않는다

### Requirement: 분풀이 지표를 백엔드로 전송한다
The client SHALL send the four venting metric events with the cheering team and entry source,
best-effort without blocking the UI. (room_enter·destroy_complete·retry_prompt_shown·
retry_ad_start를 team·entry_source와 함께 best-effort로 전송한다.)

#### Scenario: 지표 전송
- **WHEN** 룸 진입/완파/재도전 프롬프트 노출/재도전 시작 각 시점
- **THEN** 해당 event_type + team + entry_source로 `POST /venting/events`를 fire-and-forget 전송한다
- **AND** 전송 실패는 UI를 블로킹하지 않고 조용히 무시한다
