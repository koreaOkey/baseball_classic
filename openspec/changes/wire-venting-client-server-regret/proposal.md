# wire-venting-client-server-regret

## Why

분풀이 클라이언트가 아직 로컬 규칙(`LiveRegretProvider`)으로 후보를 산정한다. 백엔드
regret-top5(GPT-5.6 luna 재정렬 + 사유)가 프로덕션에서 살아있으므로, 클라가 이를
실제로 호출해 쓰고, 진입/완파/재도전 지표를 백엔드로 전송해 "어떤 응원팀이 가장 많이
분풀이했는지" 랭킹의 데이터를 채운다.

## What Changes

- **서버 TOP5 소비**: `GET /games/{id}/venting/regret-top5` 호출 → 서버 items(역할·이닝·사유)로
  분풀이 컨텍스트 조립. 스코어·결과·이닝은 `LiveGameState`와 병합. 서버 items가 비면 로컬
  규칙으로 폴백.
- **실명 매핑(선택 화면 전용)**: 서버 role ref(kind·team_side·타순/등판순서)를 박스스코어와
  조인해 실명을 해소하고 **선택 화면에만** "5번 타자 이선우"로 표기. 룸·완파 화면은 역할
  레이블만(익명 방화벽 유지). 실명은 캐시/서버에 저장되지 않으며 클라가 표시 시점에만 결합.
- **지표 전송**: `POST /venting/events` 4종(room_enter·destroy_complete·retry_prompt_shown·
  retry_ad_start) + `team`(응원팀) + `entry_source`(live/home_card/loss_push). best-effort.

## Non-Goals

- 실사용자 릴리즈 노출: venting은 DEBUG 게이트 뒤 → 릴리즈 노출은 게이트 해제 + KBOP/법률(실명) 후.
- 서버 응답 스키마 변경(현행 그대로 소비).

## Decisions

- **폴백 우선**: 서버 실패/빈 items면 `LiveRegretProvider`로 폴백 → 서버 미가동에도 UI 유지.
- **team 값**: `context.myTeamId`(Team enum name, 예 "LG") — 팬 팀 랭킹 집계 키.
- **entry_source 배선**: `VentingFlowController.open(entrySource)` → 코디네이터 → 룸/완파.
  라이브 💢 = "live", 홈카드 = "home_card", 패배 푸시 딥링크 = "loss_push".
- **실명 방화벽**: `playerName`은 `RegretCandidate`의 선택-전용 필드. `VentingTarget.roleLabel`/
  `eventDescription`에 절대 넣지 않음 → 룸/완파는 자동으로 익명 유지.
