# venting-analytics — 지표 수집 정합성 델타

## MODIFIED Requirements

### Requirement: 이벤트 타입 허용 목록
`POST /venting/events`는 폰 룸 진입(`room_enter`)뿐 아니라 워치 lite 룸 진입
(`watch_room_enter`)을 MUST 수용한다. 허용 목록:
`room_enter · watch_room_enter · destroy_complete · retry_prompt_shown · retry_ad_start`.

#### Scenario: 워치 진입 이벤트 수용
- GIVEN 폰이 워치로 분풀이 트리거를 전송하며 `watch_room_enter`를 리포트할 때
- WHEN 백엔드가 이벤트를 검증하면
- THEN 200으로 수용하고 raw 테이블·팀 집계에 반영한다

### Requirement: 진입 경로 허용 목록
`entry_source` 허용 목록은 클라이언트가 실제로 전송하는 값과 MUST 일치한다:
`home_card`(홈 카드) · `live`(라이브 플로팅 버튼) · `loss_push`(패배 푸시 딥링크) ·
`whats_new`(업데이트 팝업 CTA). 목록 밖 값은 NULL로 정규화한다.

#### Scenario: 라이브 버튼·패배 푸시 경로 보존
- GIVEN 클라이언트가 `entry_source="live"` 또는 `"loss_push"`로 room_enter를 전송할 때
- WHEN 백엔드가 이벤트를 저장하면
- THEN entry_source가 유실 없이 그대로 저장된다

#### Scenario: 미지 경로 NULL 정규화
- GIVEN 허용 목록에 없는 entry_source(예: "unknown")가 들어올 때
- WHEN 백엔드가 이벤트를 저장하면
- THEN 이벤트는 수용하되 entry_source는 NULL로 저장한다

### Requirement: 순 사용자 식별
클라이언트 리포터는 로그인 세션이 있으면 Supabase access token을
`Authorization: Bearer`로 MUST 첨부하고, 백엔드는 서명 검증 후 `sub`를 `user_id`로
저장한다. 토큰이 없거나 검증에 실패해도 이벤트는 익명으로 수용한다(비차단).

#### Scenario: 로그인 유저 user_id 저장
- GIVEN 로그인된 유저가 분풀이 룸에 진입할 때
- WHEN 리포터가 토큰을 첨부해 전송하면
- THEN venting_event.user_id에 JWT sub가 저장되어 순 사용자 집계가 가능하다

#### Scenario: 비로그인 유저 익명 수용
- GIVEN 비로그인 유저(세션 없음)가 룸에 진입할 때
- WHEN 리포터가 헤더 없이 전송하면
- THEN 이벤트는 user_id NULL로 정상 저장된다
