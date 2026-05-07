## Why

설정 탭에 워치 앱 설치/연결 상태가 노출되지 않아, 사용자가 폰 앱만 설치한 채 "워치로 햅틱 받기"라는 핵심 가치를 경험하지 못하고 이탈한다. 또한 워치 앱이 설치되어 있어도 사용자가 그 사실을 확인할 방법이 없어, 알림이 안 오면 폰 앱 문제인지 워치 앱 문제인지 구분이 불가능하다. 야구봄의 핵심 약속("보지 않아도 손목으로 느끼는 현장감")을 첫 화면에서 보장하기 위해 워치 연결 상태를 설정 탭 최상단에 상시 노출한다.

## What Changes

- 설정 탭 최상단(`"설정"` 타이틀 다음, "응원 팀" 위)에 **워치 앱 상태 카드** 신규 추가 (Android + iOS)
- 카드는 3가지 상태를 표현:
  - **페어링 안 됨**: 회색 톤 + Wear OS / Apple Watch 페어링 안내 (설치 버튼 비활성)
  - **페어링됨 + 앱 미설치**: 빨간 경고 배지 + [워치 앱 설치하기] + [연결 확인]
  - **설치됨**: 초록 체크 + "워치 앱 설치됨" + [다시 확인] (보조)
- Android: `CapabilityClient`로 노드/capability 감지 + Play Store Intent로 설치 유도
- iOS: `WCSession.isPaired` / `isWatchAppInstalled` 로 상태 감지 + `itms-watch://` URL scheme 으로 시스템 Watch 앱 호출
- 워치 앱 manifest 에 capability 선언 추가 (`basehaptic_watch_app`) — Android 한정
- 디자인은 팀컬러 따름 (`teamTheme.primary` 기반 버튼/배지)

## Capabilities

### New Capabilities
- `wear-companion-status`: 폰 앱이 워치 앱의 페어링/설치 상태를 감지하고, 미설치 시 설치 경로를 안내하는 능력 (Android `CapabilityClient` + iOS `WCSession`)

### Modified Capabilities
- `mobile-android`: 설정 탭 최상단에 워치 앱 상태 카드 노출 + 워치 앱 manifest capability 선언 추가
- `mobile-ios`: 설정 탭 최상단에 워치 앱 상태 카드 노출 + WCSession 기반 상태 감지

## Impact

- **Android 코드**:
  - `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt` — 카드 노출
  - `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/` — `WatchCompanionStatusManager` (신규) `CapabilityClient` 래퍼
  - `apps/watch/app/src/main/AndroidManifest.xml` — capability 선언 추가
- **iOS 코드**:
  - `ios/BaseHaptic/Sources/Settings/SettingsView.swift` (또는 동등한 설정 화면) — 카드 노출
  - `ios/BaseHaptic/Sources/Watch/WatchCompanionStatus.swift` (신규) — `WCSessionDelegate` 래퍼
- **디자인 토큰**: 추가 토큰 없음. 기존 `Gray900` / `teamTheme.primary` / `AppShapes.md` 재사용
- **백엔드 / 데이터**: 영향 없음
- **빌드 의존성**: Android 는 이미 `play-services-wearable` 사용 중. iOS 는 `WatchConnectivity` 프레임워크 (이미 사용 중) 만으로 충분
