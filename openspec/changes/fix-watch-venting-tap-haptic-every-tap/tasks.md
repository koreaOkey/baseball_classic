# Tasks

## 1. 탭 햅틱 매 탭 재생

- [x] 1.1 iOS `WatchVentingScreen.WatchVentingState.recordHits` — `hits == 1`은 매 탭 `.click`, `hits > 1`만 3틱 throttle
- [x] 1.2 Wear `WatchVenting.WatchVentingState.recordHits` — `hits == 1`은 매 탭 `playHitFeedback()`, `hits > 1`만 throttle

## 2. Verification

- [x] 2.1 Wear `:watch:compileDebugKotlin` 클린 통과
- [ ] 2.2 iOS 워치 빌드(watchOS 스킴) 통과
- [ ] 2.3 실기기(Apple Watch): 룸에서 탭마다 진동 체감 확인 (필요 시 햅틱 타입 상향)
- [ ] 2.4 실기기(Galaxy Watch): 동일 확인
- [ ] 2.5 크라운/로터리 빠른 스크롤 시 모터 과부하·드롭 없는지 확인
