## Why

2026-05-10 사용자 제보: 경기 시작 푸시 알림을 탭하면 곧장 라이브 경기 화면(LiveGame)으로 진입한다. 사용자 기대: 푸시 탭 시 **앱 홈으로 착지 → 그 위에 "워치로 관람하시겠습니까?" 팝업** 노출, "아니오" 면 홈에 머무름.

원인 분석:

- iOS `BaseHapticApp.swift:206-223` `.openLiveGameRequested` 핸들러: 워치 설치 시 `requestWatchSyncPrompt(navigateToLive: true)` 만 호출, 워치 미설치 시 곧장 `navigateTo(.liveGame)`. **홈으로 보내는 단계가 없음** → 다이얼로그가 마지막에 본 화면 위에 뜨거나, 미설치 사용자는 LiveGame 직진.
- iOS `closeWatchSyncDialog()` (line 528-535, 수정 전): 다이얼로그 종료 시 **"예/아니오" 무관**하게 `pendingWatchSyncNavigateToLive` 가 true 면 `navigateTo(.liveGame)` 실행 → "아니오" 눌러도 LiveGame 직진하는 결함 발견.
- Android `MainActivity.kt:519-545` 푸시 탭 LaunchedEffect / `closeWatchSyncDialog()` (line 339-348) 가 iOS 와 동일 구조라 동일한 결함.

홈 카드에서 LIVE 경기를 탭한 경우는 사용자가 명시적으로 "경기 상세를 본다"는 의도이며, 워치 동기화는 별도 토글로 제공된다. 따라서 홈 카드 탭은 워치 동기화 팝업 없이 LiveGame으로 바로 진입해야 한다.

## What Changes

### iOS (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)

- `requestWatchSyncPrompt(gameId:, navigateToLive:)` 는 푸시 진입점과 토글 진입점에서만 호출한다.
- 다이얼로그 "예" 분기: `syncedGameId` 설정 후 `pendingWatchSyncNavigateToLive` true 인 경우에만 navigate.
- 다이얼로그 "아니오" 분기: 상태를 reset 하고 화면 이동은 하지 않는다.
- `closeWatchSyncDialog()` 에서 navigate 분기 제거 (상태 reset 만 담당).
- `.openLiveGameRequested` 핸들러: 항상 `navigateTo(.home)` 으로 강제 착지 후, 워치 설치 시에만 `requestWatchSyncPrompt(navigateToLive: true)` 호출. 워치 미설치는 팝업 없이 홈에 머문다.
- 홈 카드 `onSelectGame` 은 워치 동기화 팝업을 띄우지 않고 LiveGame으로 바로 이동한다.

### Android (`apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`)

- iOS 와 대칭. `requestWatchSyncPrompt(...)` 는 푸시 진입점과 토글 진입점에서만 호출한다.
- AlertDialog confirm 버튼은 필요 시 LiveGame으로 이동하고 dismiss 버튼은 상태 reset 만 수행한다.
- `closeWatchSyncDialog()` 와 `applyWatchSyncResponse()` 에서 navigate 제거 + 두 플래그 모두 reset.
- 푸시 탭 LaunchedEffect: 항상 `navigateTo(Screen.Home)` 후 워치 설치 시에만 다이얼로그.
- 홈 카드 `onSelectGame` LIVE 분기는 워치 동기화 팝업을 띄우지 않고 LiveGame으로 바로 이동한다.

## Capabilities

### Modified Capabilities

- `mobile-ios`: 푸시 알림 탭 시 항상 홈으로 착지. 워치 설치 사용자에게는 홈 위에 워치 동기화 다이얼로그 노출, "아니오" 시 홈 유지. 워치 미설치는 팝업 없이 홈.
- `mobile-android`: 위와 동일.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 워치 미설치 사용자가 푸시 탭 후 홈만 보이고 어디로 가야 할지 모름 | 응원팀 LIVE 카드는 홈 상단에 표시되므로 사용자가 카드 탭으로 진입. 일관된 UX 가 더 중요 |
| 홈 카드 탭에서 워치 팝업이 다시 뜨는 회귀 | 카드 클릭 경로에서는 `requestWatchSyncPrompt` 를 호출하지 않고, 워치 동기화는 별도 토글 경로로만 시작 |
| `applyWatchSyncResponse` (워치 측 응답) 가 pending 상태를 reset 안 하면 다음 다이얼로그에 누수 | pending 상태 reset 유지 |

## Status

- [x] iOS 구현 완료 (`ios/mobile/BaseHaptic/BaseHapticApp.swift`)
- [x] Android 구현 완료 (`apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`)
- [x] Android `./gradlew :mobile:compileDebugKotlin` 빌드 성공
- [ ] iOS Xcode 빌드 검증
- [ ] 실기기 검증: 푸시 탭 → 홈 → 팝업 / "아니오" → 홈 유지 / "예" → LiveGame
- [ ] 워치 미설치 디바이스에서 푸시 탭 → 홈 착지(LiveGame 직진 안 함) 확인
- [ ] 홈 카드 LIVE 탭 회귀: 워치 동기화 팝업 없이 LiveGame 진입 확인
- [ ] 다음 iOS/Android 릴리즈에 포함
