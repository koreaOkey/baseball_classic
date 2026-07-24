# Hide iOS Stadium Check-in Behind Feature Flag

## Why

구장 체크인/경기장 응원은 아직 미오픈 기능인데 플랫폼 상태가 어긋나 있었다. Android는 미배선(dead code) + 설정 토글 숨김(`SHOW_STADIUM_CHEER_TOGGLE = false`)으로 완전 비노출인 반면, iOS는 앱 실행 시 지오펜스 모니터가 실제로 켜져(기본 ON, 끌 토글도 없음) 구장 진입 시 체크인 카드 + 로컬 알림이 떴다. Always 위치 권한 요청과 백그라운드 위치 갱신도 함께 발생.

## What Changes

- iOS `BaseHapticApp`에 `stadiumCheerFeatureEnabled = false` 컴파일 타임 플래그 추가 — `activateStadiumCheer()` 진입을 차단해 지오펜스 등록·체크인 카드·로컬 알림·응원 트리거 전부 비활성.
- `StadiumRegionMonitor.stopAndClear()` 신설 — CoreLocation 에 영구 보존되는 기존 등록 지오펜스와 백그라운드 위치 갱신을 앱 실행 시 정리 (이전 버전 사용자 기기 잔존 대응).
- 기능 오픈 시 플래그만 true 로 전환하면 기존 코드 그대로 복원.

## Capabilities

### stadium-checkin

- 양 플랫폼 모두 기능 오픈 전까지 사용자에게 완전 비노출 (카드·알림·위치 권한 요청 없음).
- 이전 버전에서 등록된 iOS 지오펜스는 업데이트 후 첫 실행 시 제거된다.

## Impact

- `ios/mobile/BaseHaptic/BaseHapticApp.swift`, `Stadium/StadiumRegionMonitor.swift`
- Android 변경 없음 (이미 비노출). 백엔드 영향 없음.
