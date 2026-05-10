# Tasks

## iOS 코드 변경 (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)
- [x] `pendingWatchSyncNavigateOnDecline` State 추가 (line 156)
- [x] `requestWatchSyncPrompt(navigateOnDecline: Bool = false)` 시그니처 확장 (line 520-526)
- [x] `closeWatchSyncDialog()` 에서 navigate 제거, 두 플래그 모두 reset (line 528-535)
- [x] alert "예/아니오" 분기 각각 자체 navigate 처리 (line 433-448)
- [x] `.openLiveGameRequested` 핸들러: 항상 홈 강제 진입 + 워치 설치 시만 팝업 (line 206-223)
- [x] 홈 카드 `onSelectGame` LIVE 분기 호출에 `navigateOnDecline: true` 추가 (line 313-319)

## Android 코드 변경 (`apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`)
- [x] `pendingWatchSyncNavigateOnDecline` state 추가 (line 307)
- [x] `requestWatchSyncPrompt(navigateOnDecline: Boolean = false)` 시그니처 확장 (line 320-334)
- [x] `closeWatchSyncDialog()` 에서 navigate 제거, 두 플래그 모두 reset (line 340-347)
- [x] `applyWatchSyncResponse()` 도 `pendingWatchSyncNavigateOnDecline = false` reset (line 358-364)
- [x] AlertDialog confirm/dismiss 버튼 각각 navigate 분기 (line 692-722)
- [x] 푸시 탭 LaunchedEffect: 항상 `navigateTo(Screen.Home)` 후 워치 설치 시만 팝업 (line 520-548)
- [x] 홈 카드 `onSelectGame` LIVE 분기에 `navigateOnDecline = true` 추가 (line 581-589)

## 검증
- [x] `./gradlew :mobile:compileDebugKotlin` 통과
- [ ] Xcode iOS 빌드 통과
- [ ] iOS 실기기: 푸시 탭 → 홈 진입 + 팝업 / "아니오" → 홈 유지 / "예" → LiveGame
- [ ] Android 실기기: 동일 시나리오
- [ ] 워치 미설치 환경: 푸시 탭 → 홈 착지(LiveGame 직진 X)
- [ ] 홈 카드 LIVE 탭 회귀: "예"/"아니오" 모두 LiveGame 진입 (기존 동작)

## 후속
- [ ] 다음 iOS/Android 릴리즈에 포함 (versionCode bump)
