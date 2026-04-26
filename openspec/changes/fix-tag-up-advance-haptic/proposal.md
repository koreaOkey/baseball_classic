## Why

사용자 보고: SSG vs KT 경기에서 진루(`TAG_UP_ADVANCE`) 이벤트가 워치에 텍스트로만 뜨고 햅틱·오버레이·애니메이션이 전혀 발생하지 않음.

원인 조사:
- 모바일(iOS·Android)에서 `mapToWatchEventType()`이 `TAG_UP_ADVANCE → "OUT"`으로 보내고 있어 워치는 "진루"인데 OUT 햅틱/색상이 트리거되는 의미상 모순.
- 워치 독립 폴링/APNs 경로에서 `TAG_UP_ADVANCE`가 그대로 워치에 도달하면 양쪽 워치 앱(iOS `WatchAppEventColors.overlayStyle`, Android `WatchEventColors.overlayStyle` / `eventUiFor` / 햅틱 분기) 모두 `nil` 또는 `else`로 떨어져 침묵 처리.

## What Changes

진루는 안타·도루와 동일한 무게의 "주자 진출" 이벤트이므로 **STEAL 그룹**으로 통일.

### 모바일 → 워치 매핑
- iOS [BaseHapticApp.swift:705](ios/mobile/BaseHaptic/BaseHapticApp.swift#L705): `TAG_UP_ADVANCE → STEAL`
- Android [GameSyncForegroundService.kt:424](apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt#L424): `TAG_UP_ADVANCE → STEAL`

### 색상 매핑 (모바일·워치 일관)
- iOS Mobile [AppEventColors.swift](ios/mobile/BaseHaptic/Theme/AppEventColors.swift): `TAG_UP_ADVANCE`를 OUT(red500) → STEAL 그룹(green500)으로 이동
- Android Mobile [EventColors.kt](apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/theme/EventColors.kt): 동일
- iOS Watch [WatchAppEventColors.swift](ios/watch/BaseHapticWatch/Theme/WatchAppEventColors.swift): 동일 + `overlayStyle`에 STEAL 동일 스타일 추가
- Android Watch [WatchEventColors.kt](apps/watch/app/src/main/java/com/basehaptic/watch/ui/theme/WatchEventColors.kt): 동일 + `overlayStyle`에 STEAL 동일 스타일 추가

### 워치 햅틱·오버레이 분기 (워치 독립 폴링 경로 안전망)
- iOS [WatchConnectivityManager.swift](ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift): `triggerHaptic` STEAL 케이스에 `TAG_UP_ADVANCE` 합치기
- Android [DataLayerListenerService.kt](apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt): `triggerHapticFeedback` STEAL 케이스에 합치기
- Android [MainActivity.kt:925](apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt#L925): `eventUiFor`에 STEAL 동일 항목 추가

## Capabilities

### Modified Capabilities
- `event-haptic-mapping`: `TAG_UP_ADVANCE`를 OUT 그룹에서 STEAL 그룹으로 재분류 (HIT=WALK=STEAL=TAG_UP_ADVANCE)

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 모바일 매핑 변경으로 기존 사용자가 진루를 OUT으로 인지하던 경험과 달라짐 | 진루는 본래 주자 전진(긍정 이벤트)이므로 STEAL 그룹이 의미상 정확. UX 개선으로 분류 |
| 워치 독립 경로(APNs/폴링)에서 백엔드 원본 `TAG_UP_ADVANCE`가 도달해도 양쪽 워치에서 STEAL과 동일 처리되도록 안전망 추가 | 모바일 매핑 + 워치 직접 분기 양쪽 모두 수정 |

## Status

- [x] 구현 완료 (iOS Mobile/Watch + Android Mobile/Watch)
- [ ] 빌드·실기기 검증
