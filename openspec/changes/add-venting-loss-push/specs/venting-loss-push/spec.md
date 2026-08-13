# venting-loss-push

## ADDED Requirements

### Requirement: 경기 종료 시 패배팀 유도 푸시
The system SHALL send exactly one venting-prompt visible push to the LOSING team's
subscribers when a game transitions to FINISHED and `venting_loss_push_enabled` is on.
(경기가 FINISHED로 전이될 때 플래그가 켜져 있으면 패배팀 응원팀 구독자에게 분풀이 유도
푸시를 1회 발송한다.)

#### Scenario: 패배팀 구독자에게 발송
- **WHEN** 경기가 FINISHED로 처음 전이되고 승패가 갈렸을 때(무승부 아님)
- **THEN** 패배팀(`_team_codes_for_match(losing_label)`)의 `TeamSubscriptionToken` 대상에게만
  `kind=venting_loss` payload로 visible push가 발송된다
- **AND** 승리팀 구독자에게는 발송되지 않는다

#### Scenario: 무승부 스킵
- **WHEN** 종료 시 홈/원정 스코어가 같을 때
- **THEN** 아무 푸시도 발송하지 않는다

#### Scenario: 플래그 OFF 다크
- **WHEN** `venting_loss_push_enabled=false`
- **THEN** 종료 전이 훅에서 loss-push 태스크가 스케줄되지 않아 기존 운영 경로에 무영향

#### Scenario: 커넥션 비점유 발송
- **WHEN** loss-push를 발송할 때
- **THEN** 토큰 조회는 짧은 세션에서 끝내고, APNs/FCM 발송 구간에는 DB 커넥션을 점유하지 않는다
- **AND** 영구 실패 토큰은 구독 테이블에서 정리된다

### Requirement: 패배 푸시 탭 시 분풀이 방 진입
The client SHALL open the venting flow (instead of landing on home) when the user taps a
`kind=venting_loss` push. (클라이언트는 해당 푸시 탭 시 홈 착지 대신 분풀이 플로우를 연다.)

#### Scenario: 딥링크 라우팅 (DEBUG)
- **WHEN** DEBUG 빌드에서 `kind=venting_loss` 푸시를 탭
- **THEN** 해당 경기 컨텍스트로 분풀이 방(선택/룸)을 연다(iOS `openFlow`, Android `VentingFlowController.open`)

#### Scenario: 릴리즈 무동작(다크 정합)
- **WHEN** 릴리즈 빌드(venting DEBUG 게이트 OFF)에서 동일 푸시를 탭
- **THEN** 분풀이 진입은 일어나지 않는다(기존 홈 착지 동작 유지)
