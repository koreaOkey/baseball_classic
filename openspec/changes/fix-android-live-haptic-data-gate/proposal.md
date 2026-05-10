## Why

2026-05-10 사용자 보고: 안드로이드에서 "경기 라이브 알림"(live_haptic_enabled) 토글을 OFF한 상태에서 워치 동기화가 끊기고 폰 홈 화면 점수도 갱신되지 않음. iOS는 동일 토글 OFF여도 정상 동작.

원인은 `live-haptic-master-toggle` (2026-04-26)에서 의도된 "phone↔watch 전 경로 게이트"가 Android만 충실히 구현되어 데이터 흐름까지 막은 것. iOS는 송신 측 게이트가 있어도 백엔드 silent push가 워치 UI를 별도로 갱신하기 때문에 동결되지 않음(`AppDelegate.didReceiveRemoteNotification` line 89-90, `WatchConnectivityManager.handleGameData`는 게이트 있지만 직접 푸시 경로는 게이트 미적용).

Android에는 동등한 다이렉트 푸시 경로가 없어, `WearGameSyncManager.sendGameData()` + `DataLayerListenerService.handleGameData()` 두 게이트가 데이터 흐름의 단일 chokepoint를 막아 화면 freeze. 사용자가 토글의 "라이브 알림"이라는 이름을 햅틱·진동 차단으로 해석하는 게 자연스러움 — 점수/이닝 갱신까지 멈추는 건 의도와 어긋남.

## What Changes

iOS 동작 패턴에 맞춰 `live_haptic_enabled` 토글의 의미를 **햅틱·진동·응원 트리거 차단**으로 좁힌다. game_data(점수/이닝/BSO/주자) 전송·수신은 토글과 무관하게 항상 흐른다.

- `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WearGameSyncManager.kt` `sendGameData()` line 49-55: `live_haptic_enabled` 가드 제거. `cacheLastGameData` + `deliverGameData` 항상 실행.
- `apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt` `handleGameData()` line 104-109: `live_haptic_enabled` freeze 가드 제거. SharedPreferences/UI/Tile 갱신 항상 수행.

유지되는 게이트(햅틱 전용):
- `WearGameSyncManager.sendCheerTrigger()` line 247-253 (응원 햅틱)
- `DataLayerListenerService.triggerHapticFeedback()` line 339-346 (진동·화면 깨우기)
- `DataLayerListenerService.handleCheerTrigger()` line 471-472 (응원 햅틱)
- `GameSyncForegroundService.applyIncomingEvents()` line 217·`pushStateToWatch` VICTORY line 180-184 (햅틱 발화)

## Capabilities

### Modified Capabilities

- `mobile-android` · `watch-android` · `live-haptic-master-toggle`: 토글 의미가 "라이브 동기화 전체 마스터" → "햅틱·진동·응원 트리거 마스터"로 좁아짐. 점수/이닝 등 게임 상태는 토글과 무관하게 항상 동기화. iOS와 관찰 가능 동작 일치.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| OFF 시 워치 화면이 freeze된다는 기존 사용자 기대(`live-haptic-master-toggle` 메모리)와 충돌 | 사용자 본인이 실사용 중 "iOS와 다르다"고 보고함. iOS 패턴이 사실상 표준. 토글 부제 "실시간 경기 내용을 워치로 알림 받기"는 햅틱 알림으로 해석 가능 |
| `resyncLastGameDataToWatch()` 호출 의의 약화(OFF→ON 복원 push) | 데이터 흐름이 항상이므로 중복 push가 되나 비용 미미. 함수·캐시는 유지(워치가 도중 disconnect 후 재연결 시 1회 push 보조 용도) |
| ON 복원 시 즉시 시각 회복 동작 변화 | 변화 없음. OFF 중에도 캐시·SharedPreferences가 갱신되므로 ON 시 추가 push 없이도 최신 상태 유지 |

## Status

- [x] 코드 변경 적용 (`WearGameSyncManager.kt`, `DataLayerListenerService.kt`)
- [x] 컴파일 검증 (`:mobile:compileDebugKotlin`, `:watch:compileDebugKotlin` BUILD SUCCESSFUL)
- [ ] 실기기 검증: 토글 OFF 상태에서 ① 폰 홈 점수 갱신 ② 워치 점수/이닝/BSO 갱신 ③ 햅틱·진동 미발생 동시 확인
- [ ] iOS와 동등성 확인 (토글 OFF 시 양 플랫폼 동일 동작)
- [ ] Android 차기 릴리즈에 포함
