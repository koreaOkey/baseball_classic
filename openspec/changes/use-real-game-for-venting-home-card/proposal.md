# Use Real Game for Venting Home Card (홈카드 목업 제거, 실제 경기 분풀이)

## Why

홈 "빠따존 가기" 카드는 Phase 1 목업(`MockRegretProvider` — iOS 번들 JSON,
Android 하드코딩 컨텍스트)으로 동작했고, 홈 경기 목록에도 확인용 가짜 패배 완료 경기
(`venting-mock-finished-game`)를 주입했다. 라이브 진입·loss_push 딥링크는 이미 서버
regret-top5(6.1) 실데이터를 쓰는데 홈카드만 목업이라, 카드를 눌러도 실제 경기가 아닌
목업 경기 분풀이가 열렸다. 목업을 제거하고 홈카드도 실제 경기 데이터로 전환한다.

## What Changes

### 홈카드 데이터 소스 (iOS + Android 공통)

- HomeScreen이 **오늘 경기 목록에서 완료된 마이팀 패배 경기**를 선별해
  `VentingHomeCardContainer(myTeam, finishedLossGame)`으로 전달한다. 없으면 카드 미표시.
- 컨테이너는 loss_push 딥링크와 **동일한 로드 경로**로 컨텍스트를 조립한다:
  1. `fetchVentingRegretTop5` + `fetchGameState` + `fetchGameBoxscore` 병렬 조회
     → `BackendVentingProvider`(iOS) / `ServerRegretProvider`(Android) 조립.
  2. regret 실패·빈 items 시 `fetchGameEvents`(after=0, limit=50)로
     `LiveRegretProvider` 로컬 규칙 폴백.
- 조립된 컨텍스트는 기존 `VentingOpenConditionChecker`(마이팀 패배 + 당일 KST)로 최종 판정.

### 목업 제거

- iOS: `Mock/MockRegretProvider.swift`, `Resources/mock_regret_candidates.json` 삭제
  (pbxproj 참조 포함), `--venting-force-show` 런치 인자 분기 삭제
  (BaseHapticApp 팀 자동 선택 + 홈카드 강제 표시).
- Android: `venting/MockRegretProvider.kt` 삭제.
- 양쪽 HomeScreen의 `ventingMockFinishedGame` 가짜 경기 주입 삭제 — 홈 목록은 실제 경기만 표시.
- 유지: `VentingDebugNavigator`(iOS 딥링크 스크린샷 도구)의 인라인 debug 컨텍스트,
  `Mock/AlwaysAllowGate`·`Mock/MockVentingHapticPlayer`(경기 데이터와 무관).

## Capabilities

### venting-mode

- 홈카드는 오늘 실제로 완료된 마이팀 패배 경기가 있을 때만 노출되고, 카드의 스코어·아쉬운
  순간 미리보기·진입 후 대상 목록 모두 그 경기의 실데이터(서버 regret-top5, 폴백: 로컬 규칙)다.
- 마이팀이 오늘 지지 않았으면(승리·무·경기 없음) 홈카드는 나타나지 않는다.

## Impact

- iOS: `Screens/HomeScreen.swift`, `VentingMode/Screens/VentingFlowCoordinator.swift`,
  `BaseHapticApp.swift`, `VentingMode/Screens/VentingHomeCard.swift`(주석),
  `VentingMode/Protocols/RegretCandidateProviding.swift`(주석),
  `BaseHaptic.xcodeproj/project.pbxproj`(목업 참조 제거).
- Android: `ui/screens/HomeScreen.kt`, `venting/ui/VentingFlowCoordinator.kt`.
- 워치(watchOS·Wear OS)·백엔드 변경 없음. 분풀이 모드 전체가 DEBUG/피처 플래그 게이트 뒤
  유지 — 게이트 자체는 이 change의 범위가 아니다.

## Non-Goals (후속)

- DEBUG 게이트 해제(정식 릴리즈 노출)는 별도 change.
- 무승부·연장 취소 경기 등에서의 카드 노출 정책 변경 없음(기존 오픈 조건 유지).
