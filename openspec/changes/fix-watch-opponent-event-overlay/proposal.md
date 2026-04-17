## Why
워치 `WatchContentView`의 ZStack에서 이벤트 오버레이 표시 시 `HOMERUN`, `HIT`, `DOUBLE_PLAY`, `VICTORY`를 하드코딩 필터로 제외하고 있었다 ([BaseHapticWatchApp.swift:110-111](../../../ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift#L110-L111)).

이 필터의 원래 의도는 내 팀 이벤트에서 비디오 전환 + 오버레이가 동시에 뜨는 것을 방지하는 것이었으나, 실제로는 비디오가 재생될 때 ZStack에서 비디오 화면이 오버레이보다 상위 분기에 있어 자연스럽게 가려지므로 필터 자체가 불필요했다.

문제는 **상대팀** HOMERUN/HIT/DOUBLE_PLAY 이벤트: `showTransition`에서 `isMyTeamBatting`/`isMyTeamFielding` 조건 불일치 → else 분기(오버레이 경로) 진입 → 그런데 이 필터에 의해 오버레이도 차단 → **아무런 시각 피드백 없음**.

| 이벤트 | 내 팀 | 상대팀 (수정 전) | 상대팀 (수정 후) |
|---|---|---|---|
| HOMERUN | 비디오 | 안 보임 | 오버레이 |
| HIT | 비디오 | 안 보임 | 오버레이 |
| DOUBLE_PLAY | 비디오 | 안 보임 | 오버레이 |
| SCORE | 비디오 | 오버레이 | 오버레이 (변경 없음) |
| WALK/STEAL/OUT | 오버레이 | 오버레이 | 오버레이 (변경 없음) |

iOS와 Android Wear OS 양쪽 모두 동일한 패턴으로 동일한 버그 존재.

## What Changes
- **iOS**: [ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift](../../../ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift) `WatchContentView.body` ZStack 내 오버레이 조건
  - 기존: `!["HOMERUN", "HIT", "DOUBLE_PLAY", "VICTORY"].contains(eventType.uppercased())` 필터 제거
  - 변경: `if let eventType = visibleEventType, isEventOverlayVisible` 만으로 충분
- **Android**: [apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt](../../../apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt) `WatchEventOverlay` 호출부
  - 기존: `hideTypes = setOf("HOMERUN", "HIT", "DOUBLE_PLAY", "VICTORY") + (if (scoreTransitionToken != null) setOf("SCORE") else emptySet())` 제거
  - 변경: `hideTypes` 파라미터 미전달 (기본값 `emptySet()` 사용). 비디오 전환 활성 시 when 분기에서 전환 화면을 표시하므로 else(오버레이) 분기에 도달하지 않아 hideTypes 자체가 불필요

## Non-Goals
- 상대팀 이벤트에 별도 비디오/애니메이션 추가 — 현재는 오버레이(아이콘+라벨)로 충분
- `WatchAppEventColors.overlayStyle`에 VICTORY 추가 — VICTORY는 `showTransition`에서 무조건 비디오 경로를 타므로 오버레이 불필요

## Verification
- **iOS + Android** 상대팀 HOMERUN/HIT/DOUBLE_PLAY 이벤트 시 오버레이(아이콘+라벨) 표시 확인
- **iOS + Android** 내 팀 HOMERUN/HIT/DOUBLE_PLAY 이벤트 시 기존대로 비디오 재생 (오버레이 중복 표시 없음)
- **iOS + Android** WALK/STEAL/OUT/SCORE 등 기존 정상 동작 이벤트 회귀 없음
