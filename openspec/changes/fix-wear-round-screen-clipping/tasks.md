# Tasks — fix-wear-round-screen-clipping

## 1. Wear 구현
- [x] 1.1 `WatchVentingSelectionScreen`: `ScalingLazyColumn` + `PositionIndicator` 재구성, 좌우 여백 화면 폭 5.2% 비율화
- [x] 1.2 `WatchVentingScreen` 진행 중 ✕ 버튼: 원형 화면 내접 사각형 모서리(14.6%) 들여쓰기 (사각 화면은 10dp 유지)
- [x] 1.3 Wear Compose 1.4.0 → 1.4.1 (targetSdk 35+ reduce_motion SecurityException 크래시 수정판)

## 2. 검증
- [x] 2.1 `:app:compileDebugKotlin` / `:app:compileReleaseKotlin` 통과
- [x] 2.2 Wear OS Small Round 에뮬레이터(192dp): 선택 화면 상단·하단(감독 칩 완전 노출 + 인디케이터), 룸 ✕, 완파 닫기 4개 상태 스크린샷 확인
- [x] 2.3 1.4.0에서 발생하던 선택 화면 진입 크래시가 1.4.1에서 재현 안 됨 확인
- [ ] 2.4 실기기(갤럭시 워치) 확인 후 릴리즈 AAB 재제출
