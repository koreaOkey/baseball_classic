## Why

워치 앱을 종료했다가 다시 실행하거나 모바일 실시간 스트림이 재연결될 때, 과거 경기 이벤트가 새 알림처럼 연속 재생되어 햅틱과 이벤트 영상이 몰려오는 문제가 있다. 실시간 알림은 현재 발생한 이벤트에만 반응해야 하므로 재시작/재연결 시 초기 스냅샷과 stale 이벤트를 구분해야 한다.

## What Changes

- 모바일 Android 실시간 동기화는 WebSocket 연결 직후 수신하는 초기 이벤트 목록을 워치 햅틱/영상 알림으로 재전송하지 않고 커서 기준선만 갱신한다.
- 모바일 Android 햅틱 Data Layer 항목은 누적 경로 대신 최신 항목 경로로 갱신하며 이벤트 발생 시각을 함께 전달한다.
- Wear OS 워치는 햅틱 이벤트의 발생 시각 또는 기존 경로 timestamp를 기준으로 오래된 이벤트를 무시한다.
- 모바일 iOS 실시간 동기화도 WebSocket 연결 직후 수신하는 초기 이벤트 목록을 Apple Watch 햅틱/영상 알림으로 재전송하지 않고 커서 기준선만 갱신한다.
- 실시간 `update`로 도착한 신규 이벤트는 기존처럼 워치 햅틱/영상 알림으로 처리한다.

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `mobile-android`: 워치 실시간 동기화가 재연결 초기 이벤트 스냅샷을 신규 햅틱 이벤트로 전송하지 않도록 요구사항을 보강한다.
- `watch-android`: 워치가 앱 재시작 또는 Data Layer 재전달 시 오래된 햅틱 이벤트를 무시하도록 요구사항을 보강한다.
- `mobile-ios`: Apple Watch 실시간 동기화가 재연결 초기 이벤트 스냅샷을 신규 햅틱 이벤트로 전송하지 않도록 요구사항을 보강한다.

## Impact

- Android mobile: `GameSyncForegroundService`, `WearGameSyncManager`
- Wear OS: `DataLayerListenerService`
- iOS mobile: `BaseHapticApp`
- watchOS: 기존 WatchConnectivity timestamp stale 방어 확인, 추가 코드 변경 없음
- Backend API/DB 마이그레이션 영향 없음
