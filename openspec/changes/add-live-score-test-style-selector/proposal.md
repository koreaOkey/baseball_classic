# Add Live Score Test-Tool Style Selector (Wire Selection Into Start/Sim)

## Why

WatchTestScreen의 "Promoted 버전"/"Ongoing 버전" 버튼이 **일회성 강제 게시**일 뿐 선택 상태를 남기지 않아, 이어서 누른 "Live Score 시작"·"득점 강조"·"자동 시뮬레이션"은 `forceStyle` 없이(null) 게시했다. 그 경로는 `canPostPromotedNotifications()`를 타므로 **승격 미지원 기기(삼성 One UI 8.0 등)에서는 항상 ongoing 카드로 폴백** — promoted를 눌러도 시뮬레이션이 ongoing으로 진행되는 것처럼 보였다.

테스트 도구에서 promoted/ongoing 중 하나를 **선택**하면 시작·강조·자동 시뮬레이션 전체가 그 스타일로 결정적으로 게시되게 한다(승격 조건·설정과 무관). 승격 미지원 기기에서도 forceStyle=PROMOTED는 비승격 시스템 템플릿(BigText)으로 표시되어 두 스타일을 육안 비교할 수 있다.

## What Changes

- `WatchTestScreen`:
  - 신규 상태 `selectedPreviewStyle`(기본 `Style.PROMOTED`).
  - "Promoted 버전"/"Ongoing 버전"을 일회성 버튼 → **선택 토글**로 전환(선택 시 채운 색 + `●`, 선택 스타일 즉시 미리보기 게시).
  - "Live Score 시작"·"득점 강조"가 `forceStyle = selectedPreviewStyle`로 게시.
  - 자동 시뮬레이션 루프의 `postLiveScorePreviewState(...)` 호출에 `forceStyle = selectedPreviewStyle` 추가.
  - 자동 시뮬레이션 시작 시 미리보기가 비활성이면 자동 활성화 → "Live Score 시작" 선행 없이도 선택 스타일 카드가 시뮬 내내 갱신되고 득점·홈런·안타 강조가 적용된다.

로직·프로덕션 게시 경로(`LiveScoreNotificationManager.post`)는 변경 없음 — 테스트 도구 UI 배선만 수정.

## Capabilities

### lock-screen-live-score

- 테스트 도구에서 선택한 미리보기 스타일이 "Live Score 시작"·"득점 강조"·자동 시뮬레이션에 forceStyle로 적용된다.
- 승격 미지원 기기에서도 선택 스타일로 결정적으로 게시된다(PROMOTED=비승격 시스템 템플릿, CLASSIC=이전 리치 카드).

## Impact

- `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/WatchTestScreen.kt` — 선택 상태 + 버튼 토글 + forceStyle 전달 3곳.
- `LiveScoreNotificationManager`·백엔드·iOS·Watch 영향 없음.

## Non-Goals

- 실제 프로덕션 라이브 스코어의 스타일 결정 로직 변경(그건 `default-promoted-live-score-style-all-devices`가 담당).
- 삼성 기기에서 강제 승격(불가) — 런타임 폴백 유지.
