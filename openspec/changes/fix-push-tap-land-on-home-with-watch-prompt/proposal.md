## Why

2026-05-10 사용자 제보: 경기 시작 푸시 알림을 탭하면 곧장 라이브 경기 화면(LiveGame)으로 진입한다. 사용자 기대: 푸시 탭 시 **앱 홈으로 착지 → 그 위에 "워치로 관람하시겠습니까?" 팝업** 노출, "아니오" 면 홈에 머무름.

원인 분석:

- iOS `BaseHapticApp.swift:206-223` `.openLiveGameRequested` 핸들러: 워치 설치 시 `requestWatchSyncPrompt(navigateToLive: true)` 만 호출, 워치 미설치 시 곧장 `navigateTo(.liveGame)`. **홈으로 보내는 단계가 없음** → 다이얼로그가 마지막에 본 화면 위에 뜨거나, 미설치 사용자는 LiveGame 직진.
- iOS `closeWatchSyncDialog()` (line 528-535, 수정 전): 다이얼로그 종료 시 **"예/아니오" 무관**하게 `pendingWatchSyncNavigateToLive` 가 true 면 `navigateTo(.liveGame)` 실행 → "아니오" 눌러도 LiveGame 직진하는 결함 발견.
- Android `MainActivity.kt:519-545` 푸시 탭 LaunchedEffect / `closeWatchSyncDialog()` (line 339-348) 가 iOS 와 동일 구조라 동일한 결함.

홈 카드에서 LIVE 경기를 탭한 경우는 사용자가 명시적으로 "이 경기 보러 간다" 한 의도라 "아니오" 시도 LiveGame 진입이 자연스러움 → 호출처별로 동작 분기 필요.

## What Changes

### iOS (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)

- `requestWatchSyncPrompt(gameId:, navigateToLive:, navigateOnDecline: Bool = false)` 시그니처 확장. `navigateOnDecline` 은 "아니오" 시 LiveGame 진입 여부 (기본 false).
- `pendingWatchSyncNavigateOnDecline` 상태 신설.
- 다이얼로그 "예" 분기: `syncedGameId` 설정 후 `pendingWatchSyncNavigateToLive` true 인 경우에만 navigate.
- 다이얼로그 "아니오" 분기: `pendingWatchSyncNavigateOnDecline` true 인 경우에만 navigate.
- `closeWatchSyncDialog()` 에서 navigate 분기 제거 (상태 reset 만 담당).
- `.openLiveGameRequested` 핸들러: 항상 `navigateTo(.home)` 으로 강제 착지 후, 워치 설치 시에만 `requestWatchSyncPrompt(navigateToLive: true)` (navigateOnDecline 기본 false) 호출. 워치 미설치는 팝업 없이 홈에 머문다.
- 홈 카드 `onSelectGame` (LIVE 분기, line 313-319) 호출은 `navigateOnDecline: true` 추가하여 기존 동작(아니오 시 LiveGame 진입) 유지.

### Android (`apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`)

- iOS 와 대칭. `requestWatchSyncPrompt(..., navigateOnDecline: Boolean = false)` 시그니처 확장.
- `pendingWatchSyncNavigateOnDecline` state 신설.
- AlertDialog confirm/dismiss 버튼이 각각 `pendingWatchSyncNavigateToLive` / `pendingWatchSyncNavigateOnDecline` 를 보고 분기.
- `closeWatchSyncDialog()` 와 `applyWatchSyncResponse()` 에서 navigate 제거 + 두 플래그 모두 reset.
- 푸시 탭 LaunchedEffect: 항상 `navigateTo(Screen.Home)` 후 워치 설치 시에만 다이얼로그.
- 홈 카드 `onSelectGame` LIVE 분기에 `navigateOnDecline = true` 추가.

## Capabilities

### Modified Capabilities

- `mobile-ios`: 푸시 알림 탭 시 항상 홈으로 착지. 워치 설치 사용자에게는 홈 위에 워치 동기화 다이얼로그 노출, "아니오" 시 홈 유지. 워치 미설치는 팝업 없이 홈.
- `mobile-android`: 위와 동일.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 워치 미설치 사용자가 푸시 탭 후 홈만 보이고 어디로 가야 할지 모름 | 응원팀 LIVE 카드는 홈 상단에 표시되므로 사용자가 카드 탭으로 진입. 일관된 UX 가 더 중요 |
| 홈 카드 탭 동작 회귀 (기존 "아니오" → LiveGame 직진) | 호출처에서 `navigateOnDecline: true` 명시 — 기존 동작 유지 |
| `applyWatchSyncResponse` (워치 측 응답) 가 두 플래그 모두 reset 안 하면 다음 다이얼로그에 누수 | 두 플래그 모두 reset 추가 |

## Status

- [x] iOS 구현 완료 (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)
- [x] Android 구현 완료 (`apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`)
- [x] Android `./gradlew :mobile:compileDebugKotlin` 빌드 성공
- [ ] iOS Xcode 빌드 검증
- [ ] 실기기 검증: 푸시 탭 → 홈 → 팝업 / "아니오" → 홈 유지 / "예" → LiveGame
- [ ] 워치 미설치 디바이스에서 푸시 탭 → 홈 착지(LiveGame 직진 안 함) 확인
- [ ] 홈 카드 LIVE 탭 회귀: "아니오" → LiveGame 진입 (기존 동작) 확인
- [ ] 다음 iOS/Android 릴리즈에 포함
