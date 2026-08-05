# Tasks

## 1. iOS 폰 진입점 동등성

- [x] 1.1 `VentingGameResult.inProgress` + `VentingGameContext.inningLabel` + `VentingTarget.custom`
- [x] 1.2 `LiveRegretProvider.swift` 포팅 (역할 레이블·실명 미표기·최근 5건 dedupe)
- [x] 1.3 선택 화면: `CustomTargetRow`(직접 입력) + 라이브 제목/이닝 라벨 + 후보 0건 안내 + `backLabel`
- [x] 1.4 `VentingLiveEntryOverlay`(플로팅 💢 + 패배 알림 경기당 1회 + fullScreenCover) + `LiveGameScreen` ZStack 통합

## 2. 흔들기 연타

- [x] 2.1 Android `VentingShakeDetector` + `VentingRoomState.recordShake` + 룸 연결 + 안내 문구
- [x] 2.2 iOS `VentingShakeDetector`(CoreMotion) + `VentingRoomViewModel.recordShake(hits:)` + 룸 onAppear/onDisappear + 안내 문구

## 3. Wear OS 분풀이 룸

- [x] 3.1 `WatchVenting.kt`(상수·단계·상태·진동) + `WatchVentingScreen.kt`(전면 탭 + 로터리 + 게이지 링)
- [x] 3.2 Data Layer `/venting/trigger`: 폰 sender + 워치 handler + 매니페스트 pathPrefix + WatchApp 오버레이/브로드캐스트/pending pref(10분 유효)
- [x] 3.3 폰 WatchTestScreen "워치 분풀이 룸 열기" 버튼
- [x] 3.4 스프라이트 512px 4종 drawable-nodpi 추가

## 4. watchOS 분풀이 룸

- [x] 4.1 `WatchVentingScreen.swift`(코디네이터 + 상태 + 크라운 digitalCrownRotation + 게이지 링 + WKInterfaceDevice 햅틱)
- [x] 4.2 WCSession `venting_trigger`: 폰 `WatchGameSyncManager.sendVentingTrigger` + 워치 handleMessage + WatchContentView zIndex(11) 오버레이
- [x] 4.3 iOS WatchTestScreen `ventingTestCard` 버튼
- [x] 4.4 `VentingDoll*` imageset 4종 (512px)

## 4b. 인형 스포트라이트 (스프라이트 결함 최종 해법)

- [x] 4b.1 인페인트 복구본 롤백(git restore, 실기기에서 실루엣 밖 흰 블록 노출) → 디자이너 원본 복원 + 워치 512px 재생성
- [x] 4b.2 `VentingDollSpotlight`(크림 radial gradient) — Android 룸·완파 / iOS 룸·완파 / Wear 룸 / watchOS 룸 인형 뒤 적용
- [x] 4b.3 글로우 톤 다운(사용자 피드백 "연출이 너무 쌤") — 인형의 56% 크기·가슴 위치(offsetY +11%)로 실루엣 뒤에 숨김, 완파 화면은 글로우 제거(찢김 연출이 어두운 배경과 어울림). 시뮬레이션 비교로 검증

## 5. Verification

- [x] 5.1 Android `:mobile:compileDebugKotlin` + `:watch:compileDebugKotlin` 통과
- [x] 5.2 iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과 (xcodegen 재생성 포함)
- [x] 5.3 watchOS `BaseHapticWatch Watch App` 스킴 빌드 통과
- [x] 5.3b Android `:mobile:assembleDebug` + `:watch:assembleDebug` APK 빌드 통과
- [ ] 5.4 실기기(Android): 라이브 상세 흔들기 데미지 + Z Fold6→Galaxy Watch 분풀이 트리거 확인
- [ ] 5.5 실기기(iOS): 플로팅 버튼→선택→룸 플로우 + 흔들기 + iPhone→Apple Watch 트리거 확인
- [ ] 5.6 로터리/크라운 감도 튜닝 (초기값: Wear 36px/히트, watchOS 1.0/히트)
