# venting-monetization — 재도전 Rewarded 게이트·광고 퍼널 델타

## ADDED Requirements

### Requirement: 재도전 Rewarded 광고 게이트
빠따존 진입과 경기당 첫 완파는 무료여야 하며(진입 게이트·IAP 금지), 완파 화면의
재도전(재파괴)은 Rewarded 광고 1회 시청 후에만 MUST 허용한다. 광고 로드 실패
(no-fill·네트워크 등)는 사용자 귀책이 아니므로 광고 없이 허용하고, 보상 전 광고
이탈은 거부한다.

#### Scenario: 보상 획득 시 재도전 허용
- GIVEN 완파 화면에서 "광고 보고 재도전하기"를 탭해 Rewarded 광고가 표시됐을 때
- WHEN 사용자가 보상 조건을 충족(끝까지 시청)하면
- THEN 룸으로 재진입하고 retry_ad_complete 이벤트를 보고한다

#### Scenario: 광고 로드 실패 폴백
- GIVEN 광고가 no-fill·네트워크 오류로 로드/표시되지 못했을 때
- WHEN 게이트가 판정을 반환하면
- THEN 재도전을 허용하되 retry_ad_complete로 집계하지 않는다

#### Scenario: 보상 전 이탈 시 거부
- GIVEN 사용자가 보상 조건 충족 전에 광고를 닫았을 때
- WHEN 게이트가 판정을 반환하면
- THEN 재도전을 거부하고 완파 화면에 머문다(재시도 가능)

#### Scenario: 첫 완파 무료 유지
- GIVEN 해당 경기에서 아직 완파하지 않은 사용자가 빠따존에 진입할 때
- WHEN 대상 선택 → 룸 → 첫 완파까지 진행하면
- THEN 광고 없이 완료된다(진입 게이트 없음)

### Requirement: 광고 퍼널 지표
클라이언트는 retry_prompt_shown(완파 화면 노출) · retry_ad_start(광고 버튼 탭) ·
retry_ad_complete(보상 획득) 를 `/venting/events`로 MUST 보고하고, 백엔드는
`GET /venting/ad-funnel?days=N`(X-API-Key)으로 이벤트별 건수·순 사용자 수
(로그인 user_id DISTINCT)·완료율·KST 일별 추이를 제공한다.

#### Scenario: 퍼널 집계 조회
- GIVEN 최근 7일간 재도전 광고 이벤트가 기록돼 있을 때
- WHEN 운영자가 X-API-Key와 함께 /venting/ad-funnel?days=7 을 호출하면
- THEN funnel(이벤트별 count·users), completion_rate, daily 배열을 반환한다

#### Scenario: 익명 이벤트 집계
- GIVEN 비로그인 사용자의 이벤트(user_id 없음)가 포함돼 있을 때
- WHEN 퍼널을 집계하면
- THEN count에는 포함되고 users(DISTINCT user_id)에는 포함되지 않는다
