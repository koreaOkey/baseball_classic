# Tasks

## 1. AdMob 콘솔 (선행 작업)

- [x] 1.1 "Watch Sync Rewarded - iOS" Rewarded 광고 단위 발급 → `ca-app-pub-7935544989894266/6602098213`
- [x] 1.2 "Watch Sync Rewarded - Android" Rewarded 광고 단위 발급 → `ca-app-pub-7935544989894266/8231864339`
- [x] 1.3 앱 ID 등록 확인 — iOS Info.plist `GADApplicationIdentifier` = `ca-app-pub-7935544989894266~4763404952`, Android Manifest `com.google.android.gms.ads.APPLICATION_ID` = `ca-app-pub-7935544989894266~1737773050`

## 2. RewardedAdManager 시그니처 확장 (iOS·Android 공통)

- [x] 2.1 iOS `RewardedAdManager.loadAndShowAd` 콜백을 `onComplete: (rewardEarned: Bool) -> Void` 로 변경 — `FullScreenContentDelegate` 도입으로 dismiss·실패 모두 콜백
- [x] 2.2 iOS `RewardedAdManager.loadAndShowAd` 시그니처에 `adUnitID: String` 매개변수 추가 + `themeStoreAdUnitID` / `watchSyncAdUnitID` static 분리
- [x] 2.3 iOS 기존 테마 스토어 호출처(`BaseHapticApp.swift:344`)를 새 시그니처에 맞춰 업데이트 (rewardEarned == false 면 reward 로직 skip)
- [x] 2.4 Android `RewardedAdManager.loadAndShowAd` 콜백을 `onComplete: (rewardEarned: Boolean) -> Unit` 로 변경
- [x] 2.5 Android `RewardedAdManager.loadAndShowAd` 시그니처에 `adUnitId: String` 매개변수 추가
- [x] 2.6 Android 기존 테마 스토어 호출처(`MainActivity.kt:625`, `ThemeStoreScreen.kt`)를 새 시그니처에 맞춰 업데이트
- [x] 2.7 iOS — 워치 동기화 ad unit 상수 추가: DEBUG `ca-app-pub-3940256099942544/1712485313`, RELEASE `ca-app-pub-7935544989894266/6602098213`
- [x] 2.8 Android — 워치 동기화 ad unit 상수 추가: DEBUG `ca-app-pub-3940256099942544/5224354917`, RELEASE `ca-app-pub-7935544989894266/8231864339`

## 3. 광고 시청 플래그 영구 저장

- [x] 3.1 iOS — `UserDefaults` 키 컨벤션 `watchSyncAdViewed.<gameId>` (Bool) + `WatchSyncAdLedger` enum (RewardedAdManager.swift 동일 파일 내 정의 — `.pbxproj` 누락 회피)
- [x] 3.2 Android — `SharedPreferences` 키 컨벤션 `watch_sync_ad_viewed_<gameId>` (Boolean) 정의 + 헬퍼 `WatchSyncAdLedger.hasViewed(gameId)` / `markViewed(gameId)`
- [x] 3.3 rewardEarned == true 인 경우에만 `markViewed` 호출 (실패 ON 은 다음 ON 에 광고 재시도)

## 4. iOS 라이브 경기 상세 토글 UI

- [x] 4.1 `LiveGameScreen.swift` 상단바에 `WatchSyncBadge` 통합 — 시계 아이콘 + scale 0.6 Toggle + ON/OFF 라벨 (테두리·배경 제거)
- [x] 4.2 토글 OFF→ON 탭: `onChange(of: visualOn)` 트리거 → "워치로 보시겠습니까? / 광고 관람 후 동기화됩니다" alert 표시
- [x] 4.3 alert 의 [확인]/[취소] 좌우 순서 — SwiftUI `.alert` 에 [확인] 먼저 추가, [취소] role: .cancel 로 시스템 배치
- [x] 4.4 [확인] 액션 — `WatchSyncAdLedger.hasViewed(gameId)` 가 true 면 광고 생략하고 즉시 `onSetSyncedGame(gameId)`
- [x] 4.5 [확인] 액션 — 미시청 시 `isAdLoading` ProgressView 표시 + `RewardedAdManager.shared.loadAndShowAd(adUnitID: .watchSyncAdUnitID) { rewardEarned in ... }`
- [x] 4.6 광고 콜백 — `onSetSyncedGame(gameId)` 갱신; rewardEarned == true 면 `markViewed(gameId)` 저장
- [x] 4.7 [취소] 액션 — `ignoreNextChange` flag 로 무한 루프 방지하며 `visualOn = isSyncedToCurrent` 슬라이드 백
- [x] 4.8 토글 ON→OFF 탭: 토글 슬라이드 → "워치 동기화를 끄시겠습니까?" alert
- [x] 4.9 OFF alert [확인] — `onSetSyncedGame(nil)` (광고 없음); [취소] — 토글 ON 으로 슬라이드 백

## 5. Android 라이브 경기 상세 토글 UI

- [x] 5.1 `LiveGameScreen.kt` 상단바 캡슐 뱃지 옆에 Compose `Switch` 추가 — 색상 `Green500` (디자인 토큰)
- [x] 5.2 토글 OFF→ON 탭: `Switch` checked 상태 즉시 변경 → "워치로 보시겠습니까? / 광고 관람 후 동기화됩니다" AlertDialog 표시
- [x] 5.3 AlertDialog 의 [확인]/[취소] 좌우 순서를 "확인 → 취소" 로 배치 (Material3 `confirmButton`/`dismissButton`)
- [x] 5.4 [확인] 액션 — `WatchSyncAdLedger.hasViewed(gameId)` 가 true 면 광고 생략하고 즉시 `syncedGameId = gameId` 갱신
- [x] 5.5 [확인] 액션 — 미시청 시 광고 로드 indicator 표시 + `RewardedAdManager.loadAndShowAd(context, adUnitId = WATCH_SYNC_AD_UNIT) { rewardEarned -> ... }` 호출
- [x] 5.6 광고 콜백 — `syncedGameId = gameId` 갱신; rewardEarned == true 면 `markViewed(gameId)` 저장
- [x] 5.7 [취소] 액션 — 토글 슬라이드 백 (Compose Switch `checked` 상태 복귀)
- [x] 5.8 토글 ON→OFF 탭: `Switch` checked false 로 변경 → "워치 동기화를 끄시겠습니까?" AlertDialog
- [x] 5.9 OFF AlertDialog [확인] — `syncedGameId = null` 갱신 (광고 없음); [취소] — 토글 ON 으로 슬라이드 백

## 6. Cross-Platform Consistency

- [x] 6.1 iOS·Android 토글 위치(LIVE 뱃지 옆 캡슐 뱃지 옆)·크기·색상 통일 확인
- [x] 6.2 양 플랫폼 팝업 카피("워치로 보시겠습니까?", "광고 관람 후 동기화됩니다", "워치 동기화를 끄시겠습니까?") 일치 확인
- [x] 6.3 양 플랫폼 [확인]/[취소] 버튼 좌우 순서 일치 확인
- [x] 6.4 양 플랫폼 모두 경기당 1회 광고 정책 동일 동작 확인
- [x] 6.5 메인 홈 화면의 `syncedGameId` 기반 "관람중" 카드 표시가 새 토글 흐름과 자동 연동되는지 확인

## 7. Validation

- [x] 7.1 iOS BaseHaptic 앱 빌드 실행 (`xcodebuild -scheme BaseHaptic -destination 'generic/platform=iOS' -configuration Debug build` BUILD SUCCEEDED)
- [x] 7.2 Android 모바일 Kotlin 컴파일 실행 (`./gradlew :mobile:compileDebugKotlin` BUILD SUCCESSFUL)
- [x] 7.3 iOS 실기기 (DEBUG): 토글 OFF→ON 탭 → 팝업 → 확인 → 테스트 광고 재생 → 시청 완료 → 홈 카드에 "관람중" 표시 확인
- [x] 7.4 iOS 실기기 (DEBUG): 같은 경기 다시 OFF→ON → 광고 없이 즉시 ON 되는지 확인
- [x] 7.5 iOS 실기기 (DEBUG): 광고 실패 시뮬레이션(항공 모드) → 동기화 ON 허용 + 광고 미시청 플래그 유지 확인
- [x] 7.6 Android 실기기 (DEBUG): 위 시나리오 동일 검증
- [x] 7.7 양 플랫폼: OFF 팝업 [확인] → 즉시 OFF + 홈 카드 "관람중" 표시 사라짐 확인
- [x] 7.8 양 플랫폼: 팝업 [취소] → 토글 슬라이드 백 + `syncedGameId` 미변경 확인
- [ ] 7.9 RELEASE 빌드로 양 플랫폼 재빌드 → 실 ad unit(`…/6602098213`, `…/8231864339`)로 실광고 재생 확인
