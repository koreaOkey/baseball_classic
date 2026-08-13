# add-venting-loss-push

## Why

분풀이 모드의 진입 퍼널에 **경기 종료 → 패배팀 팬에게 유도 푸시**가 빠져 있다. 현재는
백엔드가 종료 시 regret만 산정·캐시하고 푸시를 보내지 않으며, 클라이언트 인앱 유도
다이얼로그는 사용자가 그 경기 상세 화면을 열어둔 채 종료를 맞이할 때만 뜬다(대부분의
사용자에게 도달 못 함). 패배팀 팬에게 "오늘 아쉽게 졌어요 💢 분풀이할까요?" 푸시를
보내 탭 시 바로 분풀이 방으로 진입시키면 퍼널이 완성된다.

## What Changes

- **백엔드(다크)**: `venting_loss_push_enabled: bool = False` 신규 플래그. 경기 FINISHED
  전이 1회 한정으로, **패배팀** 응원팀 구독자(`TeamSubscriptionToken`)에게 visible push
  발송. 기존 `_send_game_start_notification` 패턴 재사용하되 대상은 패배팀 코드만.
  out-of-band(토큰 로드 짧은 세션 → 커넥션 비점유 상태로 APNs/FCM 발송), 무승부 스킵.
  payload `kind=venting_loss`, iOS category `OPEN_VENTING`.
- **클라(양 플랫폼) 딥링크 라우팅**: 푸시 탭 시 `kind=venting_loss`를 감지해 홈 착지 대신
  분풀이 방을 연다(iOS `openFlow()`, Android `VentingFlowController.open`).

## Non-Goals

- regret-top5 서버 연동/지표 전송(별도 change `wire-venting-client-server-regret`).
- 실사용자 릴리즈 노출: 클라 venting은 여전히 DEBUG 게이트 뒤 → 릴리즈 노출은
  DEBUG 게이트 해제 + KBOP/법률(실명) 통과 후. 이 change는 다크/디버그에서 완성.
- 푸시 카피 최종 확정(마케터 검토는 활성화 전 별도).

## Decisions

- **대상 테이블**: `TeamSubscriptionToken`(경기-수명-독립, `my_team` not null) — 게임 스코프
  `DeviceToken` 아님. `_load_team_subscriptions(losing_codes)` 재사용.
- **패배팀 판정**: `state_payload`의 `homeScore/awayScore`로 종료 시점 산정, 동점=스킵.
  대상 코드는 `_team_codes_for_match(losing_label)`.
- **운영 안전**: 플래그 기본 OFF(다크). 발송은 `_send_game_start_notification`과 동일하게
  `asyncio.to_thread`로 토큰 로드 후 커넥션 비점유 상태에서 `asyncio.gather` 발송,
  영구 실패 토큰 `_prune_dead_team_subscription_tokens` 정리 → 7/28 FCM/풀 패턴 회피.
- **딥링크**: 기존 "홈 착지" 라우팅에 `kind` 분기 추가(iOS 새 category `OPEN_VENTING`,
  Android `data["kind"]` 판독 + intent/bus에 venting 플래그). venting UI가 DEBUG 게이트라
  릴리즈에선 무동작(다크와 정합).
