# venting-loss-push

## MODIFIED Requirements

### Requirement: 경기 종료 시 패배팀 유도 푸시
The system SHALL send exactly one venting-prompt visible push to the LOSING team's
subscribers when a game transitions to FINISHED and `venting_loss_push_enabled` is on,
AND — when `venting_loss_push_min_version` is set — only to subscribers whose registered
app version is greater-than-or-equal to that minimum. (버전 게이트 지정 시 그 버전 이상으로
등록한 구독자에게만 발송한다.)

#### Scenario: 패배팀 구독자에게 발송
- **WHEN** 경기가 FINISHED로 처음 전이되고 승패가 갈렸을 때(무승부 아님)
- **THEN** 패배팀 `TeamSubscriptionToken` 대상에게만 `kind=venting_loss` payload로 발송된다

#### Scenario: 무승부 스킵
- **WHEN** 종료 시 홈/원정 스코어가 같을 때
- **THEN** 아무 푸시도 발송하지 않는다

#### Scenario: 버전 게이트 — 지원 버전만
- **WHEN** `venting_loss_push_min_version`이 "8.6.0"으로 설정돼 있을 때
- **THEN** app_version ≥ 8.6.0 으로 등록한 구독자에게만 발송하고, 구버전·버전 미상(NULL) 구독자는 제외한다

#### Scenario: 게이트 미설정
- **WHEN** `venting_loss_push_min_version`이 빈 값일 때
- **THEN** 버전과 무관하게 전 패배팀 구독자에게 발송한다(기존 동작)

#### Scenario: 플래그 OFF 다크
- **WHEN** `venting_loss_push_enabled=false`
- **THEN** 종료 전이 훅에서 loss-push 태스크가 스케줄되지 않아 기존 운영 경로에 무영향

## ADDED Requirements

### Requirement: 구독 등록 시 앱 버전 기록
The client SHALL send its app version on team-subscription registration, and SHALL
re-register when the app version changes so the backend records the current version. (클라는
구독 등록 시 앱 버전을 보내고, 버전이 바뀌면 재등록해 백엔드가 최신 버전을 기록하게 한다.)

#### Scenario: 업데이트 후 재등록
- **WHEN** 사용자가 앱을 새 버전으로 업데이트하고 실행했을 때
- **THEN** token/team 이 동일해도 버전 변경으로 재등록되어 백엔드의 app_version 이 갱신된다
