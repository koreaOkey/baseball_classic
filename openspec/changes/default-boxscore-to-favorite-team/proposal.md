# default-boxscore-to-favorite-team — 박스스코어 응원팀 기본 노출 + "기록" 명칭

## Why

경기 상세 박스스코어 탭은 팀 토글 기본 선택이 항상 왼쪽(선공인 어웨이)이라,
응원팀이 홈인 사용자는 매번 토글을 눌러야 자기 팀 기록을 볼 수 있었다.
또 토글 라벨이 "OO팀 타자"였지만 실제로는 그 아래에 타자·투수 기록이 모두
보이므로 명칭이 내용과 맞지 않았다.

## What Changes

- **응원팀 기본 노출 (iOS·Android)**: 박스스코어 팀 토글의 기본 선택을
  어웨이 고정 → 응원팀 우선으로 변경. 응원팀이 홈이면 홈이 기본 선택되고,
  응원팀이 없거나(`Team.none`/`NONE`) 이 경기에 출전하지 않으면 기존대로
  선공인 어웨이가 기본. 수동 토글은 그대로 우선(수동 선택 전 상태만 nil/null로
  두고 렌더 시 기본값 계산).
- **토글 배치 유지**: 세그먼트 버튼 순서(어웨이 왼쪽·홈 오른쪽)는 라인스코어
  표기 순서와 맞추기 위해 유지 — 기본 "선택"만 응원팀으로 변경.
- **명칭 변경 (iOS·Android)**: 토글 라벨 "OO팀 타자" → "OO팀 기록".
- **타자 표 제목 추가 (iOS·Android)**: 기존 "투수 기록" 제목과 대칭이 되도록
  타자 표 위에 "타자 기록" 제목 추가(라벨에서 "타자"가 빠지며 생긴 공백 보완).

## Impact

- **클라이언트 전용 2파일**:
  - iOS `Screens/LiveGameScreen.swift` (`BoxscoreSection`, `BoxscoreTeamSegment`)
  - Android `ui/screens/LiveGameScreen.kt` (`boxscoreShowsHome` 상태, `BoxscoreSection`, `BoxscoreBatterTable`)
- **백엔드·DB 변경 없음**. 응원팀은 기존 저장값 재사용
  (iOS `selected_team` AppStorage, Android `LocalTeamTheme.current.team`).
- 빌드 검증: Android `:app:compileDebugKotlin` 통과, iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과.

## Non-Goals

- 세그먼트 버튼 순서 변경(응원팀을 왼쪽으로 재배치하지 않음).
- 워치(watchOS·Wear OS) 화면 — 박스스코어 탭이 폰 전용.
- 중계 탭·라인스코어 표기 순서 변경.

## Capabilities

### Added Capabilities
- `live-game-detail`: 경기 상세 박스스코어 탭의 팀 토글 기본 선택·라벨 규칙.
