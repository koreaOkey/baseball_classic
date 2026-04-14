## Why
iOS 앱은 `Screen.store` enum 케이스만 남겨둔 채 탭 버튼도 화면 구현도 없었다. Android는 `ThemeStoreScreen.kt` + `SHOW_STORE_TAB` 플래그로 이미 목업 상점이 준비되어 있어 iOS만 공백 상태였다. 기능 패리티를 맞추기 위해 iOS에도 같은 목업 기반 테마 상점을 붙인다.

## What Changes
- `ios/mobile/BaseHaptic/Screens/ThemeStoreScreen.swift` 신규 — Android `ThemeStoreScreen.kt`를 SwiftUI로 포팅. 동일한 4종 목업 테마(`theme_home_crowd`, `theme_retro_sunset`, `theme_ice_blue`, `theme_dark_monochrome`)와 가격/서브타이틀/카드 레이아웃.
- `BaseHapticApp.swift`
  - `switch currentView`에 `.store` 케이스 추가. 구매 시 로컬 `purchasedThemes` state에 append 후 자동 적용.
  - 하단 탭바에 "상점" (`bag.fill`) 버튼을 홈과 설정 사이에 추가.
- `xcodegen generate`로 `BaseHaptic.xcodeproj` 재생성 (신규 Swift 파일 자동 등록).

## Non-Goals
- 실제 결제/영속화: 구매 내역은 여전히 세션 단위 `@State`. Android와 동일 제약.
- Android `SHOW_STORE_TAB` 플래그는 이번 변경에서 건드리지 않음 (여전히 `false`).
- LiveActivity·Watch 쪽 노출 변경 없음.

## Verification
- `xcodebuild -scheme BaseHaptic -destination 'platform=iOS Simulator,name=iPhone 17'` 빌드 성공.
- 설정 화면 진입 → 상점 탭 → 테마 구매 → 적용 플로우를 시뮬레이터에서 수동 확인 필요(사용자).
