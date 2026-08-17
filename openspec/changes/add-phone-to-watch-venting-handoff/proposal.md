# Add Phone → Watch Venting Handoff (폰 대상 선택 후 워치로 분풀이 시작)

## Why

분풀이 모드는 폰(홈카드·라이브·패배 팝업·패배 푸시)과 워치(스와이프 진입·폰 테스트 트리거)가
각각 독립 진입점을 갖는다. 하지만 **폰에서 이미 대상을 고른 사용자가 손목 위 워치로 이어서
분풀이하는 경로**는 없었다. 폰→워치 룸 트리거 배관(`sendVentingTrigger`)은 이미 존재하나
개발용 `WatchTestScreen`에서만 호출됐다. 그 배관을 정식 진입 버튼에 연결한다.

2026-08-17 사용자 결정:
1. 워치로 시작 후 폰은 룸에 진입하지 않고 **안내 화면(ⓑ)** 을 띄운다.
2. **워치 연동(앱 설치, installed)** 상태일 때만 버튼을 노출한다.
3. 직접 입력(custom) 대상은 **입력값 그대로** 워치에 전달한다.

## What Changes

### 진입 (iOS + Android 공통)

- 대상 선택 화면(`VentingTargetSelectionScreen`) 하단 "분풀이 시작하기" **아래에**
  "⌚ 워치로 분풀이 시작하기" 버튼을 추가한다. 둘 중 하나를 눌러 분풀이를 시작한다.
  - 순서: ① 분풀이 모드 진입 → ② 화난 대상 선택 → ③ (워치 연동 시) 폰/워치 중 선택.
- 워치 버튼은 **워치 연동(installed)일 때만** 노출.
  - iOS: `WCSession` `activationState==.activated && isPaired && isWatchAppInstalled`.
  - Android: `WatchCompanionStatusRepository.getStatus() == Installed`
    (캐시값으로 초기화 후 실제 조회로 갱신, 깜빡임 방지).
- 워치 버튼 탭 → 기존 `sendVentingTrigger(gameId, targetLabel, eventDescription)` 발송
  (테스트 도구와 동일 경로) → 워치측 수신 핸들러가 룸을 연다(**워치 코드 변경 없음**).
- 발송 후 폰은 룸으로 진입하지 않고 새 상태 `watchHandoff`로 전환 → 안내 화면 표시 → "닫기"로 종료.

### 대상 → 워치 페이로드 매핑

- `targetLabel = VentingTarget.roleLabel`, `eventDescription = VentingTarget.eventDescription`
  (양 플랫폼에 이미 존재하는 계산 속성 재사용).
- 선수는 **역할 라벨**만 전송(실명 금지 원칙 준수), 감독은 "감독", 직접 입력은 **입력값 그대로**.

### 지표

- 워치 진입은 `watch_room_enter` 이벤트로 별도 집계(기존 `VentingEventsReporter`/`VentingEventReporter`
  재사용, `entrySource`는 원 진입 경로 유지).

## Capabilities

### venting-mode

- 대상 선택 후, 워치가 연동된 경우 사용자는 폰 룸과 워치 룸 중 하나를 선택해 분풀이를 시작할 수 있다.
- 워치로 시작하면 폰은 안내 화면만 보여주고 룸에 진입하지 않는다.
- 워치가 연동되지 않은 경우 워치 버튼은 노출되지 않으며 기존 폰 전용 흐름과 동일하다.

## Impact

- iOS: `VentingMode/Screens/VentingTargetSelectionScreen.swift`(버튼 파라미터·UI),
  `VentingMode/Screens/VentingFlowCoordinator.swift`(연동 감지·트리거·`watchHandoff` 상태·안내 화면).
- Android: `venting/ui/VentingTargetSelectionScreen.kt`(버튼 파라미터·UI),
  `venting/ui/VentingFlowCoordinator.kt`(연동 감지·트리거·`WatchHandoff` 상태),
  `venting/ui/VentingWatchHandoffScreen.kt`(신규 안내 화면).
- **워치(watchOS·Wear OS) 코드 변경 없음** — 기존 트리거 수신 핸들러 재사용.
- 백엔드 영향 없음. 분풀이 모드 전체가 DEBUG/피처 플래그 게이트 뒤에 있어 이 버튼도 동일 게이트를 상속.

## Non-Goals (후속)

- 워치 앱이 unreachable(꺼짐)일 때의 즉시 오픈 — 현재는 `transferUserInfo`/Data Layer 큐(10분 신선도)로
  큐잉되어 워치 앱이 켜질 때 열린다. 연동(installed)이면 버튼을 노출하고 안내 문구로 커버.
- 워치→폰 역방향 핸드오프.
- custom 이름 전송이 모델 주석("어디에도 전송하지 않는다")과 상충 → 사용자 결정(③)에 따라 폰↔자기
  워치로만 전달(서버 미전송). 초상권/저장 리스크 낮음. 필요 시 후속에서 정책 문구 갱신.
