# Tasks

## iOS

- [ ] `ios/mobile/BaseHaptic/Models/ReleaseNotes.swift` 신규: `ReleaseNote` 구조체(`version`, `subtitle`, `bullets`) + `enum ReleaseNotes` (`all: [ReleaseNote]`, `notes(for:)`).
- [ ] `ios/mobile/BaseHaptic/Components/WhatsNewSheet.swift` 신규: ScrollView 기반 시트. NEW 배지(버전), 제목 "업데이트 안내", 서브타이틀, bullet 체크 리스트, "다시 보지 않기" 토글, "나중에" / "확인" 두 버튼. 디자인 토큰(`AppColors`, `AppFont`, `AppSpacing`, `AppRadius`) 사용.
- [ ] `ios/mobile/BaseHaptic/BaseHapticApp.swift` `ContentView`:
  - 기존 `.alert("업데이트 안내", isPresented: $showUpdateAnnouncement)` 제거.
  - `onAppear` 의 트리거 로직을 신규 헬퍼 함수로 분리(빈 lastSeen → 즉시 저장 + 스킵, `release_notes_globally_dismissed` 가드, `ReleaseNotes.notes(for:)` 존재 여부 가드).
  - `.sheet(isPresented: $showUpdateAnnouncement)` 로 `WhatsNewSheet` 노출.
  - "확인 + 다시 보지 않기" 시 `release_notes_globally_dismissed = true` 저장.

## Android

- [ ] `apps/mobile/app/src/main/java/com/basehaptic/mobile/data/model/ReleaseNotes.kt` 신규: iOS 와 동일 구조체 (`ReleaseNote`, `object ReleaseNotes { val all; fun notes(version) }`).
- [ ] `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/components/WhatsNewDialog.kt` 신규: Compose `Dialog` 또는 `AlertDialog`. iOS 시트와 동일 레이아웃. `AppFont`, `AppSpacing`, `AppShapes`, `Gray*`, `Yellow*` 사용. NEW 배지·체크 아이콘은 `Icons.Default.Check` 또는 토큰 색의 동그란 박스로.
- [ ] `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`:
  - 새 SharedPreferences 헬퍼 추가: `loadLastSeenUpdateVersion()`, `persistLastSeenUpdateVersion()`, `loadReleaseNotesDismissed()`, `persistReleaseNotesDismissed()`. 기존 `USER_PREFS_NAME` 재사용.
  - `BaseHapticApp` Composable 에 `var showWhatsNew by remember { mutableStateOf(false) }` 추가.
  - `LaunchedEffect(Unit)` 에서 (showOnboarding=false 보장된 시점에) `BuildConfig.VERSION_NAME` 과 lastSeen 비교 → 트리거 결정 + lastSeen 저장.
  - `if (showWhatsNew) { WhatsNewDialog(...) }` 렌더링. "확인+다시 보지 않기" 시 dismissed 영속화.

## QA

- [ ] iOS 시뮬레이터: lastSeen 비우고 앱 실행 → 모달 미노출 (신규 설치 시나리오). lastSeen 을 이전 버전으로 세팅 후 재실행 → 모달 노출. "확인" 누른 뒤 재실행 → 미노출.
- [ ] iOS 시뮬레이터: "다시 보지 않기" + "확인" 후 lastSeen 삭제 + 재실행 → 미노출.
- [ ] Android 에뮬레이터: 동일 시나리오 검증.
- [ ] 양 플랫폼: 온보딩 화면 위에는 절대 안 뜨는지 확인.
- [ ] 양 플랫폼: 다크 모드 가독성, 작은 화면(iPhone SE / 5인치 안드로이드) 에서 ScrollView 동작.

## 빌드 / 배포

- [ ] iOS: Xcode 빌드 통과 (`xcodebuild -workspace ios/BaseHaptic.xcworkspace -scheme BaseHaptic build` 또는 IDE 빌드).
- [ ] Android: `./gradlew :app:assembleDebug` 통과.
- [ ] 카피 수령 후 `ReleaseNotes.all` 양 플랫폼 동기 갱신 + 버전 bump.
- [ ] 커밋: `feat(update-popup): 업데이트 안내 모달 추가 (iOS · Android)`.
