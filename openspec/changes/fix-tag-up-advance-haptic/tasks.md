## Tasks

### 모바일 → 워치 매핑
- [x] iOS `mapToWatchEventType`: TAG_UP_ADVANCE → STEAL (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)
- [x] Android `mapToWatchEventType`: TAG_UP_ADVANCE → STEAL (`apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt`)

### 색상 매핑 (OUT/red500 → STEAL 그룹/green500)
- [x] iOS Mobile `AppEventColors`
- [x] Android Mobile `AppEventColors`
- [x] iOS Watch `WatchAppEventColors.color(for:)`
- [x] Android Watch `WatchEventColors.eventColor()`

### 오버레이 스타일 (STEAL과 동일 — bolt.fill / cyan500 / "STEAL" 라벨)
- [x] iOS Watch `WatchAppEventColors.overlayStyle(for:)`
- [x] Android Watch `WatchEventColors.overlayStyle()`
- [x] Android Watch `eventUiFor` (`MainActivity.kt`)

### 햅틱 분기 (STEAL과 동일 패턴)
- [x] iOS Watch `WatchConnectivityManager.triggerHaptic`
- [x] Android Watch `DataLayerListenerService.triggerHapticFeedback`

### 검증
- [ ] iOS 빌드
- [ ] Android 빌드
- [ ] 실기기 테스트: 진루 이벤트 발생 경기에서 워치 STEAL 햅틱·오버레이 확인
