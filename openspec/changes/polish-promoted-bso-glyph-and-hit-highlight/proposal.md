# Polish Promoted BSO Glyph + Highlight Hits

## Why

1. promoted 카드 BSO 줄의 빈 슬롯이 텍스트 글리프 `○`(U+25CB)라 이모지 원(🟢🟡🔴)과 폰트 크기·베이스라인이 달라 "공백 있는 동그라미"처럼 정렬이 어긋나 보였다.
2. 테스트 자동 시뮬레이션의 하이라이트 판정(`shouldHighlightLiveScorePreview`)이 득점·홈런만 강조하고 **안타(HIT)는 빠져 있어**, 프로덕션 LOCK_SCREEN 필터 기본값(score/homerun/hit = ON)과 어긋났다.

## What Changes

- `LiveScoreNotificationManager.bsoEmojiLine()`: 빈 슬롯 글리프 `○`(U+25CB) → 이모지 `⚪`(U+26AA). 채운 슬롯과 크기·기준선 일치. (promoted 경로 전용, 리치 폴백 카드 무영향.)
- `WatchTestScreen.shouldHighlightLiveScorePreview()`: 강조 대상에 `EventType.HIT` 추가 → 자동 시뮬레이션에서 득점·홈런·안타 강조(프로덕션 동등).
- `LiveScoreNotificationManager.post(bypassEventFilter)`: 테스트 미리보기 전용 플래그. `shouldHighlight`가 `highlightEvent && (bypassEventFilter || EventFilterGate.isAllowed(...))`가 되어, 사용자가 "안타" 필터를 껐어도 시뮬레이션 HIT가 강조된다. 프로덕션 경로는 bypass=false로 기존 필터 동작 유지.

프로덕션 게시 하이라이트는 이미 `EventFilterGate` LOCK_SCREEN 필터(score/homerun/hit 기본 ON)로 세 이벤트를 강조한다. 다만 테스트 도구가 같은 필터로 **이중 게이트**되어, 사용자가 "안타" 필터를 끈 기기에서는 시뮬 HIT 강조가 막혔다 — 이를 bypass로 해소.

## Capabilities

### lock-screen-live-score

- promoted 카드 BSO 빈 슬롯이 채운 슬롯과 동일한 이모지 원(⚪)으로 렌더링된다.
- 테스트 자동 시뮬레이션이 득점·홈런·안타를 강조(heads-up + 진동 + 이벤트 문구)하여 프로덕션과 동일하게 동작한다.

## Impact

- `apps/mobile/app/src/main/java/com/basehaptic/mobile/push/LiveScoreNotificationManager.kt` — BSO 글리프 1곳.
- `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/WatchTestScreen.kt` — 강조 판정 1곳.
- 백엔드·iOS·Watch 영향 없음.

## Non-Goals

- 프로덕션 하이라이트 이벤트 집합 변경(이미 필터 기본값으로 동일).
- 이모지 원 색/테두리 커스텀(플랫폼 기본 이모지 사용).
