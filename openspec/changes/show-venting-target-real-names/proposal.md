# show-venting-target-real-names — 분풀이 대상 선택 화면 실명 표기 "역할(실명)"

## Why

분풀이 대상 선택 화면에서 서버 TOP5 경로는 실명을 "9번 타자 구자욱"(공백 병기)으로
표시했지만, 라이브 진입 경로(`LiveRegretProvider`)는 실명을 의도적으로 제거해 역할
레이블("9번 타자")만 보여줬다. 사용자는 선택 화면에서 누구를 고르는지 바로 알 수
있도록 **"9번 타자(구자욱)"** 형식의 실명 표기를 요청했다.

## What Changes

- **표기 형식 통일 (iOS·Android)**: 선택 화면 `TargetRow` 제목을
  `"역할 실명"` → `"역할(실명)"`으로 변경 — 예: "9번 타자(구자욱)", "선발 투수(문동주)".
  실명이 없으면 기존처럼 역할 레이블만 표시.
- **라이브 진입 경로 실명 채움 (iOS·Android)**: `LiveRegretProvider`가 중계 이벤트의
  `batter`/`pitcher` 이름을 `RegretCandidate.playerName`에 실어 선택 화면에서도 실명이
  보이게 한다(공백 정리 후 비어 있으면 null).
- **중복 제거 키 개선**: 같은 역할 레이블 기준 → **실명 우선**(없으면 역할 레이블) 기준.
  "구원 투수"가 서로 다른 두 명이어도 각각 후보로 남는다.

## Impact

- **클라이언트 전용 4파일**:
  - iOS `VentingMode/Screens/VentingTargetSelectionScreen.swift`, `VentingMode/Data/LiveRegretProvider.swift`
  - Android `venting/ui/VentingTargetSelectionScreen.kt`, `venting/LiveRegretProvider.kt`
- **백엔드·DB 변경 없음** — 서버 regret-top5 응답·박스스코어 조인(`resolvePlayerName`) 그대로.
- **익명 방화벽 유지**: 실명 노출은 선택 화면 `TargetRow` 한정. 룸·완파·워치 화면은
  `VentingTarget.roleLabel`/`eventDescription`만 읽으므로 여전히 익명.
- 빌드 검증: Android `:mobile:compileDebugKotlin` 통과, iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과.

## Non-Goals

- 홈카드 TOP5 미리보기 표기 변경(익명 유지).
- 룸·완파·워치 화면 실명 노출(초상권·방화벽 원칙 유지).
- 직접 입력 행 동작 변경.

## Capabilities

### Modified Capabilities
- `venting-regret-selection`: 선택 화면 실명 표기를 "역할(실명)" 형식으로 통일하고,
  라이브 진입 후보에도 실명을 싣는다(룸·완파 익명 유지).
