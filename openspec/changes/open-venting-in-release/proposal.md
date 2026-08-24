# open-venting-in-release — 빠따존(분풀이 모드) 릴리즈 오픈

## Why

빠따존은 iOS `#if DEBUG` 컴파일 게이트 + Android `BuildConfig.DEBUG` 런타임 게이트 +
로컬 토글의 이중 게이트 뒤에 있어 릴리즈 빌드에서는 전혀 노출되지 않았다.
백엔드는 이미 프로덕션에서 활성(`VENTING_BACKEND_ENABLED=true`,
`VENTING_LOSS_PUSH_ENABLED=true`, 최소 버전 1.1.7) 상태라 클라이언트 게이트가
유일한 차단막이었다. 릴리즈에 기능을 오픈한다.

## What Changes

- **iOS 컴파일 게이트 제거**: `VentingMode/` 전 파일(24개)의 파일 단위
  `#if DEBUG`/`#endif` 제거 — 릴리즈 바이너리에 포함. 단 `VentingDebugNavigator`·
  `VentingRoomDebugView`(스크린샷용 딥링크 도구)만 `#if DEBUG` 재래핑 유지.
- **iOS 진입점 오픈**: BaseHapticApp(loss_push 딥링크 onReceive + fullScreenCover,
  `ventingDeepLinkRequest` 상태), HomeScreen(홈카드 삽입 + `ventingFinishedLossGame` +
  가이드 스텝), LiveGameScreen(`VentingLiveEntryOverlay`), WatchTestScreen(휴대폰
  빠따존 버튼)의 `#if DEBUG` 제거. `--venting-screen`·`venting-debug` 딥링크 등
  디버그 도구는 DEBUG 유지.
- **피처 플래그 기본 ON (양 플랫폼)**:
  - iOS `VentingFeatureFlag.isEnabled` — 저장값 없으면 true (기존: 항상 UserDefaults
    bool, 기본 false + init에서 DEBUG만 강제 true → 강제 라인 제거로 토글 지속성 확보).
  - Android `VentingFeatureFlag.isEnabled` — `!BuildConfig.DEBUG → false` 조기 반환 제거,
    prefs 기본 true 그대로 (릴리즈 항상 ON).
- **설정 토글**: DEBUG 섹션 전용 유지(릴리즈 미노출 = 릴리즈 사용자는 끌 수 없음).
  부제 "DEBUG 전용 피처" → "로컬 토글 (릴리즈는 항상 ON)".
- **테스트 도구**: 휴대폰 빠따존 열기 버튼 릴리즈 노출로 변경 (워치 버튼과 동일).

## Impact

- **iOS 29파일** (VentingMode 24 + BaseHapticApp/HomeScreen/LiveGameScreen/
  SettingsScreen/WatchTestScreen), **Android 4파일** (VentingFeatureFlag/
  WatchTestScreen/SettingsScreen/VentingHomeCard·Coordinator 주석).
- **백엔드·워치 변경 없음** — 프로덕션 env 이미 활성, watchOS·Wear OS 빠따존 화면은
  원래 게이트 없음.
- **게이트 정책**: 현재 룸 진입은 `AlwaysAllowGate`(무제한 무료). 기획상 "경기당 무료
  1회 + Rewarded 광고" 게이트는 Phase 2 미구현 — 릴리즈 오픈 시 무제한 무료로 나간다.
- 빌드 검증: iOS Debug+**Release** 시뮬레이터 빌드 통과, Android
  `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` 통과.

## Non-Goals

- Rewarded 광고 게이트(경기당 무료 1회) 구현 — 후속 change.
- 디버그 도구(딥링크 내비게이터, 설정 DEBUG 섹션) 릴리즈 노출.
- 백엔드 변경.
