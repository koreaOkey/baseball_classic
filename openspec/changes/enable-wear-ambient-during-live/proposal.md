## Why

Wear OS 앱은 라이브 경기 동안 `FLAG_KEEP_SCREEN_ON`으로 화면을 인터랙티브 밝기로 계속 켜 둔다(MainActivity.kt:379-385). KBO 경기는 3시간 이상이라 워치 배터리 소모의 최대 단일 요인이며, 이미 구현된 `AmbientLifecycleObserver`/`AmbientGameScreen`의 앰비언트 전환을 이 플래그가 차단해 사실상 무력화한다.

플래그를 제거해도 앱은 꺼지지 않는다: 앰비언트 모드에서 `AmbientGameScreen`이 스코어를 계속 표시하고, FGS·Data Layer 수신·진동은 화면 상태와 무관하게 동작하며, 손목을 올리면 즉시 인터랙티브로 복귀한다.

부수 버그: `isLive`가 prefs 기반이라 경기 중 폰 연결이 끊겨 FINISHED를 못 받으면 KST 자정까지 stale `isLive=true`가 유지되어, 앱을 열 때마다 화면이 계속 켜진다. 이 만료 처리도 함께 정리한다(우천 중단 등 장시간 무수신과의 구분 기준 필요).

## What Changes

- 라이브 중 `FLAG_KEEP_SCREEN_ON` 상시 유지를 제거하고 앰비언트 모드에 표시를 맡긴다. "보는 동안 밝게"가 필요하면 마지막 인터랙션/이벤트 후 N초만 유지 후 해제하는 절충안을 적용한다.
- `KEY_GAME_UPDATED_AT` 기준으로 일정 시간(예: 45분) 업데이트가 없으면 화면 유지 목적의 `isLive` 판정을 해제한다. 우천 중단 시 라이브 UI 자체는 유지하되 화면 상시 켜짐만 풀리도록 화면 정책과 데이터 정책을 분리한다.
- 이벤트 수신·진동·ongoing 알림 등 실시간 경로는 변경하지 않는다.

## Capabilities

### Modified Capabilities

- `watch-android`: 라이브 관전 중 화면 유지 정책을 "상시 인터랙티브"에서 "앰비언트 허용 + 인터랙션 기반 유지"로 변경한다.

## Impact

- `apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt` — KEEP_SCREEN_ON 플래그 관리, 앰비언트 전환
- `apps/watch/app/src/main/java/com/basehaptic/watch/WatchFinishedGameCache.kt` 또는 관련 prefs 로직 — stale isLive 화면 정책 분리
- 실기기 검증 필요(앰비언트 전환 UX, 손목 올림 복귀 지연)
