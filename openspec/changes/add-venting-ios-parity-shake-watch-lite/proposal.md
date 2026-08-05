# Add Venting iOS Parity + Shake Input + Watch Lite Rooms

## Why

`add-venting-live-entry`(Android)로 경기 중 분풀이 진입이 생겼지만 iOS에는 없었고, 컨셉 승인(2026-08-05)된 흔들기 연타와 워치 분풀이 lite도 미구현이었다. 이 change는 ① iOS 폰 진입점 동등성, ② 양 폰 흔들기 연타 입력, ③ watchOS·Wear OS 분풀이 룸(폰 테스트 도구 트리거)을 한 번에 구현한다.

## What Changes

### 1. iOS 폰 진입점 동등성 (Android add-venting-live-entry 포팅)

- `LiveGameScreen.swift` 루트를 ZStack으로 감싸고 `VentingLiveEntryOverlay` 추가 — 왼쪽 하단 플로팅 💢 버튼(마이팀 경기 + 플래그 ON, 경기 상태 무관 상시), 패배 종료 알림(경기당 1회, UserDefaults `venting_loss_prompted_game_ids`), `fullScreenCover`로 플로우 표시(backLabel "경기").
- `LiveRegretProvider.swift` 신설 — 중계 이벤트·박스스코어 기반 후보 산출(병살·삼진 타자 / 실점 투수, 역할 레이블만·실명 미표기).
- `VentingTarget.custom(String)` — 선수명 직접 입력(표시 전용·미저장), 선택 화면 `CustomTargetRow` + 라이브 라벨("지금까지의 아쉬운 순간", 이닝 표시) + `backLabel` 파라미터.
- `VentingGameResult.inProgress` + `VentingGameContext.inningLabel` 추가.

### 2. 흔들기 연타 (양 폰)

- Android `VentingShakeDetector`(SensorManager 선형가속도, 12m/s² 임계 + 220ms 디바운스) / iOS `VentingShakeDetector`(CoreMotion userAcceleration 1.2g, 동일 밸런스).
- 세기 → 히트 1~3 환산(세게 흔들수록 데미지 큼). `VentingRoomState.recordShake` / `VentingRoomViewModel.recordShake(hits:)` — 진동은 버스트당 1회.
- 룸이 보이는 동안만 센서 점유. 안내 문구 "폰을 꽉 잡고 흔들어도 데미지!" 상시 노출(놓침 방지).

### 3. 워치 분풀이 lite (watchOS + Wear OS)

- **진입**: 폰 > 설정 > 테스트 도구 > "워치 분풀이 룸 열기" 버튼.
  - Android: Data Layer 신규 path `/venting/trigger` (`WearGameSyncManager.sendVentingTrigger`) → 워치 `DataLayerListenerService.handleVentingTrigger`(매니페스트 pathPrefix `/venting` 추가) → pending pref + 브로드캐스트 + `launchMainActivity`. 10분 유효(낡은 트리거로 룸이 뜨는 것 방지).
  - iOS: WCSession 메시지 `type:"venting_trigger"` (`WatchGameSyncManager.sendVentingTrigger`) → 워치 `handleMessage` → `WatchVentingCoordinator.dispatch` → `WatchContentView` zIndex(11) 오버레이.
- **룸(공통 설계)**: 대상 선택 생략(트리거가 보낸 대상 1개 고정, 기본 "감독") → 화면 전체 탭 영역 + 베젤/크라운 회전 히트 환산 + 테두리 원형 게이지 링 + 3단계 스프라이트 + 완파 시 닫기. 도구 트레이 없음.
- **밸런스**: 폰과 동일(40히트 완파, 33%/66% 전환). 경진동은 히트 3회당 1회(배터리·모터 배려), 단계 전환은 강진동(Wear Vibrator 웨이브폼 / watchOS WKInterfaceDevice .directionUp/.retry/.success).
- **에셋**: 기존 인형 스프라이트(복구본) 512px 다운스케일 재사용 — Wear `drawable-nodpi/venting_doll_*.png`, watchOS `VentingDoll*.imageset`. 신규 디자이너 에셋 불필요.

## Capabilities

### venting-mode (modified)

- iOS 경기 상세에서도 Android와 동일하게 경기 상태 무관 플로팅 버튼·패배 알림·직접 입력으로 분풀이에 진입할 수 있다.
- 양 폰 분풀이 룸에서 기기를 흔들어 게이지를 올릴 수 있다(세기 비례).
- 폰 테스트 도구 버튼으로 페어링된 워치(Galaxy Watch/Apple Watch)에 분풀이 룸을 즉시 띄워 탭·베젤/크라운으로 완파할 수 있다.

## Impact

- iOS: `VentingMode/**`(모델·선택 화면·코디네이터·LiveRegretProvider·ShakeDetector·LiveEntry 신설), `Screens/LiveGameScreen.swift`, `Screens/WatchTestScreen.swift`, `WatchSync/WatchGameSyncManager.swift` — 전부 `#if DEBUG` 게이트 유지(라이브 진입·룸). xcodegen 재생성.
- watchOS: `Screens/WatchVentingScreen.swift` 신설, `WatchConnectivityManager.swift`, `BaseHapticWatchApp.swift`, Assets 4종.
- Android phone: `venting/VentingShakeDetector.kt` 신설, `VentingRoomState/Screen`, `wear/WearGameSyncManager.kt`, `WatchTestScreen.kt`.
- Wear: `venting/WatchVenting.kt`·`WatchVentingScreen.kt` 신설, `DataLayerListenerService.kt`, `MainActivity.kt`, 매니페스트, drawable 4종.
- 백엔드 영향 없음.

## Non-Goals (후속)

- 워치 분풀이의 실전 진입(패배 알림 탭 → 워치 룸) — 테스트 도구 트리거로 UX 검증 후.
- 무료 1회 폰·워치 통합 정산, 광고 게이트 위치(보류 결정).
- 로터리/크라운 감도 실기기 튜닝(ROTARY_PX_PER_HIT=36px, crownUnitsPerHit=1.0 초기값).
- 인형 스프라이트 완전 재생성(팔 프린지) — 별도 디자이너 건.
