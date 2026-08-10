# Add Venting Watch Swipe Entry (스와이프 진입 + 대상 선택)

## Why

워치 분풀이 룸의 진입점이 폰 테스트 도구뿐이었다. 작은 화면에 버튼을 두는 대신,
웨어러블 문법인 **가로 스와이프 페이지**로 실전 진입을 만든다 (2026-08-06 사용자 결정:
"스와이프 → 폰처럼 누가 문제인지 선택 → 탭하면 룸 진입").

## What Changes

### 진입 (Wear OS + watchOS 공통)

- 마이팀 경기 데이터가 있으면 라이브 화면이 **2페이지 가로 페이저**가 된다:
  페이지 1 = 기존 라이브 스코어, 페이지 2 = 분풀이 대상 선택.
  - Wear: `androidx.compose.foundation.pager.HorizontalPager` (WatchApp "game" 분기)
  - watchOS: `TabView(.page)` (WatchContentView 메인 분기)
- 선택 화면에서 대상 탭 → 즉시 룸 진입(기존 `WatchVentingScreen`/코디네이터 재사용).
  워치는 확인 단계 생략. 감독은 고정 마지막 항목.
- 폰 테스트 도구 트리거는 개발용으로 유지.

### 후보 데이터 — WatchRegretTracker (양 플랫폼 미러)

워치는 중계 기록(타순·박스스코어·실명)이 없으므로 **수신 이벤트 타입 + 그 시점 이닝**만으로
이닝 기반 역할 레이블을 자체 축적한다 (실명 미표기 원칙 자동 충족):

- 마이팀 공격 중: DOUBLE_PLAY→"N회 병살 타자"(가중 2), OUT→"N회 아웃 타자"(1), TRIPLE_PLAY(2)
- 마이팀 수비 중: SCORE/SAC_FLY_SCORE→"N회 실점 투수"(3), HOMERUN→"N회 피홈런 투수"(3)
- 공수 판정: 팀명 canonical 정규화(displayTeamName) + 이닝 초/말. 중립·테스트 경기는 양쪽 다 기록.
- 기록 지점: Wear `DataLayerListenerService`(game_data 인라인 이벤트 + /haptic), watchOS
  `WatchConnectivityManager`(game_data 인라인 + haptic_event + 직접 push). 이벤트 필터·햅틱
  토글과 무관하게 기록.
- 저장: 경기당 최대 12건(새 gameId 시 초기화), 표시 최대 5건 + 감독.

### 2모드 정렬 (사용자 요구 사양)

- **경기 중**: 최신순 — "그 시점까지 이슈가 있었던 사람"
- **패배 확정 후**(isFinished && 마이팀 스코어 열세): 심각도 가중 정렬 — "경기 전체 패배 지분"
  근사. Phase 2 백엔드 regret-top5 API로 교체 예정 자리.

## Impact

- Wear: `venting/WatchRegretTracker.kt`·`WatchVentingSelectionScreen.kt` 신설,
  `DataLayerListenerService.kt`(기록 훅 + 공수 판정), `MainActivity.kt`(페이저).
- watchOS: `WatchVentingScreen.swift`(트래커 + 선택 뷰 추가), `WatchConnectivityManager.swift`
  (기록 훅), `BaseHapticWatchApp.swift`(TabView 페이지).
- 폰·백엔드 영향 없음.

## Non-Goals (후속)

- 패배 알림 탭 → 바로 룸 진입 (승인된 컨셉, 별도 change).
- 백엔드 regret-top5 API 연동(Phase 2) — 폰·워치 후보 데이터 통일.
- 페이지 인디케이터/스와이프 힌트 연출.
