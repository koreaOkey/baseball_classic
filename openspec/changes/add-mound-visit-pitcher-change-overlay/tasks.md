## Tasks

### 백엔드 (crawler)
- [x] `_classify_event_type()`에 `has_mound_visit` 키워드 매칭 추가 (`crawler/backend_sender.py`)
- [x] `MOUND_VISIT` 분기 우선순위 배치 (PITCHER_CHANGE 다음, HALF_INNING_CHANGE 이전)
- [x] `PITCHER_CHANGE` 이벤트 페이로드에 `metadata.inName` / `metadata.outName` 명시적 추가 (playerChange 블록 활용)

### 모바일 → 워치 화이트리스트
- [x] iOS `mapToWatchEventType`: `PITCHER_CHANGE`, `MOUND_VISIT` 추가 (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)
- [x] Android `mapToWatchEventType`: 동일 (`apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt`)

### 색상 매핑
- [x] iOS Mobile `AppEventColors`: PITCHER_CHANGE=green500, MOUND_VISIT=yellow500
- [x] Android Mobile `AppEventColors`: 동일
- [x] iOS Watch `WatchAppEventColors.color(for:)`: PITCHER_CHANGE=green500, MOUND_VISIT=yellow400

### 오버레이 매핑
- [x] iOS Watch `WatchAppEventColors.overlayStyle(for:)`: PITCHER_CHANGE("투수 교체", `arrow.left.arrow.right.circle.fill`, blue400), MOUND_VISIT("마운드 방문", `figure.baseball`, yellow400)
- [x] Android Watch `eventUiFor` (`MainActivity.kt`): SwapHoriz/blue400, Sports/yellow400

### 햅틱 분기
- [x] iOS Watch `WatchConnectivityManager.triggerHaptic`: PITCHER_CHANGE = WALK 패턴(.click x2), MOUND_VISIT = break(무음)
- [x] Android Watch `DataLayerListenerService.triggerHapticFeedback`: PITCHER_CHANGE = WALK 패턴, MOUND_VISIT = early return

### MOUND_VISIT 지속 표시 (다음 이벤트까지)
- [x] Android Watch `WatchEventOverlay`: MOUND_VISIT일 때 자동 dismiss delay 스킵 (`MainActivity.kt`)
- [x] iOS Watch `BaseHapticWatchApp`: MOUND_VISIT일 때 `eventOverlayDuration` asyncAfter 스킵

### 검증
- [x] Android 모바일 모듈 컴파일 (compileDebugKotlin)
- [x] Android 워치 모듈 컴파일 (compileDebugKotlin)
- [ ] iOS 빌드 (Xcode)
- [ ] 실기기 테스트: 백엔드 mock fixture 또는 실제 경기에서 두 이벤트 오버레이 확인
- [ ] 백엔드 회귀: 기존 비디오 판독 분류는 OUT/OTHER 그대로, 새 마운드 방문은 MOUND_VISIT으로 분리되는지 확인 (sanity-test 통과)

### 후속 (별도 change로 분리)
- [ ] PITCHER_CHANGE 오버레이에 `inName/outName` 표시 (워치 페이로드 확장 + UI 2단 레이아웃)
- [ ] MOUND_VISIT → PITCHER_CHANGE 시퀀스 추적 (마운드 방문 후 60초 내 교체 발생률 텔레메트리)
