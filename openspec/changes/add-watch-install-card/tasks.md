## 1. Wear OS 앱 capability 선언

- [ ] 1.1 `apps/watch/app/src/main/AndroidManifest.xml` 의 `<application>` 안에 capability meta-data 추가 (`com.google.android.wearable.capability` = `basehaptic_watch_app`)
- [ ] 1.2 워치 앱 빌드 후 폰에서 `CapabilityClient` 로 capability 조회되는지 실기기 검증

## 2. Android 폰 — 워치 동반 앱 상태 감지 모듈

- [ ] 2.1 `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WatchCompanionStatus.kt` 신규 — sealed class `WatchCompanionStatus { PairedNone, PairedNoApp, Installed }`
- [ ] 2.2 `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WatchCompanionStatusRepository.kt` 신규 — `CapabilityClient.getCapability("basehaptic_watch_app", FILTER_REACHABLE)` + `NodeClient.getConnectedNodes()` 조합으로 상태 결정
- [ ] 2.3 코루틴 기반 `suspend fun getStatus(): WatchCompanionStatus` 노출 + `Flow<WatchCompanionStatus>` 도 제공 (Lifecycle 갱신용)
- [ ] 2.4 PAIRED_NONE / PAIRED_NO_APP / INSTALLED 분기 단위 테스트 (가능하면 Wearable API mock)

## 3. Android 폰 — 카드 UI 구현

- [ ] 3.1 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/components/WatchInstallCard.kt` 신규 — Composable, 입력으로 `WatchCompanionStatus` + `onInstall` + `onRecheck` 콜백
- [ ] 3.2 PAIRED_NONE / PAIRED_NO_APP / INSTALLED 3가지 비주얼 분기 (회색 / 빨강 배지 / 초록 체크), 디자인 토큰 (`Gray900`, `AppShapes.md`, `teamTheme.primary`) 사용
- [ ] 3.3 INSTALLED 상태는 컴팩트 1줄 레이아웃 (다른 두 상태 대비 작은 높이)
- [ ] 3.4 PAIRED_NO_APP 카드 하단 안내 문구: "Play Store에서 설치 대상 기기를 워치로 선택해 주세요"
- [ ] 3.5 [SettingsScreen.kt:81-88] 의 "설정" 타이틀 다음, "응원 팀" 위에 카드 `item { }` 삽입
- [ ] 3.6 `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` 또는 동등한 방식으로 onResume 시 상태 재조회

## 4. Android 폰 — 설치 유도 진입점

- [ ] 4.1 `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WatchInstallLauncher.kt` 신규 — Play Store Intent (`market://` + fallback `https://play.google.com/store/apps/details?id=...`) 호출 헬퍼
- [ ] 4.2 Play Store 미설치 환경 fallback (브라우저 URL) 동작 검증
- [ ] 4.3 카드의 [워치 앱 설치하기] 버튼이 launcher 호출하도록 wiring

## 5. iOS 폰 — 워치 동반 앱 상태 감지 모듈

- [ ] 5.1 `ios/BaseHaptic/Sources/Watch/WatchCompanionStatus.swift` 신규 — `enum WatchCompanionStatus { case pairedNone, pairedNoApp, installed }`
- [ ] 5.2 `ios/BaseHaptic/Sources/Watch/WatchCompanionStatusObserver.swift` 신규 — `WCSessionDelegate` 구현, `isPaired` / `isWatchAppInstalled` 변화 관찰
- [ ] 5.3 `@Published var status: WatchCompanionStatus` 노출 (ObservableObject) → SwiftUI 바인딩 가능
- [ ] 5.4 WCSession 비활성화 환경(예: iPad) 에서 graceful 처리

## 6. iOS 폰 — 카드 UI 구현

- [ ] 6.1 `ios/BaseHaptic/Sources/Settings/WatchInstallCard.swift` 신규 — SwiftUI View, `WatchCompanionStatus` + 액션 클로저 입력
- [ ] 6.2 Android 와 동일한 3가지 비주얼 분기, 팀컬러는 iOS 의 팀 테마 시스템에 맞춰 매핑
- [ ] 6.3 INSTALLED 상태 컴팩트 1줄 레이아웃
- [ ] 6.4 iOS 설정 화면(`SettingsView.swift` 또는 동등한 위치) 최상단 첫 섹션으로 카드 삽입
- [ ] 6.5 `onAppear` + `scenePhase` 변화 시 상태 재조회

## 7. iOS 폰 — 설치 유도 진입점

- [ ] 7.1 `WatchInstallLauncher.swift` 신규 — `itms-watch://` URL scheme 호출 헬퍼
- [ ] 7.2 `UIApplication.shared.canOpenURL` 으로 가능 여부 확인, fallback 안내 텍스트 표시 분기

## 8. 통합 검증

- [ ] 8.1 Android 실기기: 워치 미페어링 / 페어링O앱X / 페어링O앱O 3가지 상태 카드 표시 확인
- [ ] 8.2 Android 실기기: [워치 앱 설치하기] 버튼이 Play Store 워치 모드로 진입하는지 확인
- [ ] 8.3 iOS 실기기: Apple Watch 미페어링 / 페어링O앱X / 페어링O앱O 3가지 상태 카드 표시 확인
- [ ] 8.4 iOS 실기기: [워치 앱 설치하기] 버튼이 Apple Watch 앱 호출하는지 확인
- [ ] 8.5 응원 팀 변경 시 두 플랫폼 모두 카드 버튼 색상이 즉시 갱신되는지 확인
- [ ] 8.6 설치 완료 후 폰 앱 포그라운드 복귀 시 자동으로 INSTALLED 상태로 전환되는지 확인 (양 플랫폼)

## 9. 릴리즈

- [ ] 9.1 Android: 워치 앱 + 폰 앱 새 버전을 동일 릴리즈 사이클로 묶어 Play Console 게시
- [ ] 9.2 iOS: 폰 앱 + 워치 앱 새 버전을 동일 App Store Connect 제출
- [ ] 9.3 [reference_release_spec.md] 절차에 따라 태그/브랜치 정리
