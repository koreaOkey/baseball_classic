# Tasks

## 코드 변경
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WearGameSyncManager.kt`: `sendGameData()` 진입부의 `live_haptic_enabled` 가드 제거. cache + deliver 항상 실행.
- [x] `apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt`: `handleGameData()` 진입부의 `live_haptic_enabled` freeze 가드 제거. UI/SharedPreferences/Tile 갱신 항상.

## 유지(검토 완료)
- [x] `WearGameSyncManager.sendCheerTrigger()` 응원 햅틱 게이트 — 유지
- [x] `DataLayerListenerService.triggerHapticFeedback()` 진동·wakeScreen 게이트 — 유지
- [x] `DataLayerListenerService.handleCheerTrigger()` 응원 햅틱 게이트 — 유지
- [x] `GameSyncForegroundService.applyIncomingEvents()` 햅틱 발화 게이트, VICTORY 게이트 — 유지
- [x] handleGameData 내부의 eventType→triggerHapticFeedback 호출은 triggerHapticFeedback() 자체에서 게이트되므로 의도대로 햅틱만 차단됨 (이중 보장)

## 검증
- [x] `:mobile:compileDebugKotlin` BUILD SUCCESSFUL
- [x] `:watch:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] 실기기: 토글 OFF + 라이브 경기 진행 중 — 폰 홈 점수 갱신 확인
- [ ] 실기기: 토글 OFF + 라이브 경기 진행 중 — 워치 화면 점수/이닝/BSO 갱신, 햅틱·진동 미발생 확인
- [ ] 실기기: 토글 ON 복원 시 즉시 최신 상태 반영(추가 push 1회로도, 그 이전에 이미 흐르고 있어도 OK)
- [ ] iOS 토글 OFF 동작과 비교 — 양 플랫폼 동일 결과

## 후속
- [ ] `WearGameSyncManager.resyncLastGameDataToWatch()` 호출부(`SettingsScreen.kt:367-368`)는 데이터 흐름이 상시이므로 의의가 약해짐. 다음 릴리즈에서 호출 제거 검토(현재 무해하므로 본 변경에는 미포함).
- [ ] `live-haptic-master-toggle` 메모리(2026-04-26)의 "phone↔watch 전 경로 게이트" 표현을 "햅틱·진동·응원 트리거 게이트"로 갱신.
- [ ] Android 차기 릴리즈(versionCode bump)에 포함.
