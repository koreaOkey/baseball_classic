## 1. Mobile Android

- [x] 1.1 스트림 연결 직후 첫 이벤트 목록을 워치 햅틱/영상 알림으로 전송하지 않고 기준 커서로만 반영한다.
- [x] 1.2 연결 이후 신규 update 이벤트는 기존 사용자 설정에 따라 워치 알림으로 전송되도록 유지한다.
- [x] 1.3 워치 햅틱 이벤트 Data Layer 항목을 최신 이벤트 기준으로 갱신하고 발생 시각을 포함한다.

## 2. Wear OS

- [x] 2.1 햅틱 이벤트 발생 시각이 freshness 기준을 초과하면 진동과 화면 깨우기를 실행하지 않는다.
- [x] 2.2 기존 경로 기반 햅틱 이벤트도 경로 timestamp를 fallback으로 사용해 stale 여부를 판단한다.
- [x] 2.3 최신 이벤트와 커서 중복 방지 동작은 기존처럼 유지한다.

## 3. Mobile iOS

- [x] 3.1 iOS 모바일 실시간 동기화 루프에서 연결 직후 첫 이벤트 목록을 Apple Watch 햅틱/영상 알림으로 전송하지 않고 기준 커서로만 반영한다.
- [x] 3.2 연결 이후 신규 update 이벤트는 기존 사용자 설정에 따라 Apple Watch 알림으로 전송되도록 유지한다.
- [x] 3.3 watchOS WatchConnectivity 햅틱 수신에는 timestamp stale 방어가 이미 있어 추가 코드 변경 없이 동작을 확인한다.

## 4. Platform Impact

- [x] 4.1 Android 모바일 영향 확인: 실시간 동기화 서비스와 Wear Data Layer 전송 경로만 변경한다.
- [x] 4.2 Android 워치 영향 확인: Data Layer 햅틱 수신 stale 판단만 변경한다.
- [x] 4.3 iOS 모바일 영향 확인: 실시간 동기화 루프의 초기 스냅샷 처리만 변경한다.
- [x] 4.4 iOS 워치 영향 확인: 기존 stale 방어 확인 외 추가 코드 변경 없음.
- [x] 4.5 DB 마이그레이션 영향 확인: 스키마 변경이 없어 마이그레이션을 추가하지 않는다.

## 5. Verification

- [x] 5.1 모바일 Android `./gradlew :app:compileDebugKotlin` 검증을 실행한다.
- [x] 5.2 Wear OS `./gradlew :app:compileDebugKotlin` 검증을 실행한다.
- [ ] 5.3 iOS `xcodebuild` 기반 컴파일 검증을 실행한다.
