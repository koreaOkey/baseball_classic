## Why

야구봄 워치 라이브 화면은 현재 투수/타자 이름만 한 줄(`P 김재윤  B 김성욱`)로 표시한다. 사용자는 같은 화면에서 현재 투수의 투구수까지 보고 싶어한다. 백엔드 DB(`GamePitcherStat.pitches_thrown`)에는 투수별 누적 투구수가 이미 저장돼 있으나, `GET /games/{gameId}/state` 응답에는 노출되지 않아 워치까지 도달하지 못하고 있다.

## What Changes

- **백엔드** (`backend/api/app/schemas.py`, `services.py`)
  - `GameStateOut` 에 `pitcherPitchCount: int | None` 추가.
  - `build_game_state()` 에서 LIVE 경기에 한해 `game.pitcher` 이름과 일치하는 `GamePitcherStat` 행을 `appearance_order desc nulls last` 로 조회해 활성 투수의 `pitches_thrown` 을 노출. FINISHED/CANCELED/POSTPONED 는 `null`.

- **iOS 폰** (`ios/mobile/BaseHaptic/Data/BackendGamesRepository.swift`, `WatchSync/WatchGameSyncManager.swift`, `BaseHapticApp.swift`, `AppDelegate.swift`, `Screens/WatchTestScreen.swift`)
  - `LiveGameState` 에 `pitcherPitchCount: Int?` 추가, JSON 디코더 갱신.
  - `WatchGameSyncManager.sendGameData` 에 `pitcherPitchCount: Int?` 파라미터 추가, payload `pitcher_pitch_count` 키로 전송 (`-1` = 미전송 sentinel).
  - 4개 호출처(initial state / `.state` / `.update` / APNs background) 와 watch test 시뮬레이터에서 값 전달. signature 에도 포함해 동일 BSO 라도 투구수 변화 시 재전송.

- **iOS 워치** (`ios/watch/BaseHapticWatch/Models/GameData.swift`, `WatchSync/WatchConnectivityManager.swift`, `Screens/WatchLiveGameScreen.swift`)
  - `GameData` 에 `pitcherPitchCount: Int?` 추가.
  - `WatchConnectivityManager.handleGameData` 에서 `pitcher_pitch_count` 파싱(`-1` → `nil`).
  - `WatchLiveGameScreen` 의 player info 줄을 `HStack` 으로 분해해 `Text("P {name}") + Text("\(count)").contentTransition(.numericText(value:)).id(pitcher)` 로 그린다. **id 변경 = 투수 교체 시 다운카운트 애니메이션 스킵**. live 가 아니면 투구수 영역 숨김.

- **Android 폰** (`apps/mobile/.../data/BackendGamesRepository.kt`, `wear/WearGameSyncManager.kt`, `service/GameSyncForegroundService.kt`, `ui/screens/WatchTestScreen.kt`)
  - `LiveGameState` 에 `pitcherPitchCount: Int?` 추가, JSON 파서 갱신.
  - `WearGameSyncManager.sendGameData` 에 파라미터 추가, DataMap `pitcher_pitch_count` 키(`-1` sentinel) 와 캐시 JSON 모두 갱신.
  - `GameSyncForegroundService` signature 와 호출에 포함, `WatchTestScreen` 시뮬레이터 전달.

- **Android 워치** (`apps/watch/.../DataLayerListenerService.kt`, `MainActivity.kt`, `data/GameData.kt`, `ui/components/LiveGameScreen.kt`, `ui/theme/WatchUiProfile.kt`)
  - `KEY_PITCHER_PITCH_COUNT` 상수 추가, prefs 에 저장/복원.
  - `GameData.pitcherPitchCount: Int?` 추가, `readGameDataFromPrefs` 에서 `-1` → `null`.
  - `LiveGameScreen` 의 player info 를 **2줄 (`P {name} · {count}구` / `B {name}`)** 로 재배치. 투구수만 `AnimatedContent` (slide up + fade) 로 카운트업, `contentKey = gameData.pitcher` 로 투수 교체 시 애니메이션 스킵.
  - `WatchUiProfile.playerInfoOffsetYDp` 를 3 티어 모두 +6dp(`20→26`, `28→34`, `35→41`) 상향해 2줄 레이아웃이 위쪽 BSO 와 겹치지 않게 한다.

- **워치 자동 시뮬레이션** (iOS `WatchTestScreen.swift`, Android `WatchTestScreen.kt`)
  - `SimGameState.pitchCount` 추가. 시뮬레이션 runner 가 이벤트 타입 기반으로 자동 누적/리셋:
    - 한 구로 카운트되는 이벤트: `BALL, STRIKE, HIT, HOMERUN, WALK, OUT, DOUBLE_PLAY, TRIPLE_PLAY` → `+1`.
    - 그 외(`SCORE, STEAL, TAG_UP_ADVANCE, MOUND_VISIT, PITCHER_CHANGE` 등): 변화 없음.
    - 이벤트 클로저가 투수 이름을 바꾼 경우 → `pitchCount=0` 으로 리셋.
  - `sendCurrentState()` 가 워치로 함께 전달하므로 시뮬레이션 중에도 워치에 투구수가 갱신된다.

## Capabilities

### Modified Capabilities

- `game-state`
  - `GameStateOut` 응답에 활성 투수의 누적 투구수(`pitcherPitchCount`)를 함께 노출하여 클라이언트가 별도 호출 없이 워치 UI 까지 표시할 수 있게 확장.

## Impact

- **백엔드**: 응답에 nullable 정수 필드 1개 추가(기존 클라이언트 비호환 없음). 1회 라이트 쿼리 추가(`GamePitcherStat` 인덱스 `idx_game_pitcher_stats_game_side_appearance` 사용). DB 스키마 변경 없음, 마이그레이션 없음.
- **iOS/Android**: 페이로드에 `pitcher_pitch_count` 키 추가. 구버전 워치 앱은 이 키를 무시한다 (default `-1`/`nil` 처리). 레이아웃은 라이브 경기 + 값 존재 시에만 표시되므로 종료 화면에 영향 없음.
- **워치 시뮬레이션**: 기존 시나리오 클로저는 그대로, runner 가 `pitchCount` 만 자동 관리. 회귀 영향 없음.
- **DB 스키마**: 변경 없음.

### Non-Goals (별건으로 분리)

- 투수별 카드/모달 (이번 변경은 라이브 화면 한 줄만).
- 스트라이크/볼 비율, 볼 카운트 분석 등 투구 분석 통계.
- 투구수 리미트 알림(예: 100구 도달 햅틱).
- 폰 메인 라이브 화면(현 LiveGameScreen)의 투구수 표시. 워치 한정 변경.

## Rollout

1. 백엔드 PR 머지 → Railway 자동 배포.
2. 새 응답을 폰 앱이 무시해도 무방. iOS/Android 클라이언트 빌드 배포 후 자동 활성화.
3. 검증:
   - 라이브 경기에서 워치 라이브 화면에 `P {투수} · {N}구` / `B {타자}` (Android) 또는 `P {투수} · {N}구  B {타자}` (iOS) 표시 확인.
   - BALL/STRIKE/HIT 등 한 구짜리 이벤트마다 카운트가 +1 증가하며 슬라이드 업/numericText 애니메이션이 보이는지.
   - 투수 교체 직후 다운카운트 애니메이션 없이 새 투수 이름 + 새 카운트가 즉시 갱신되는지.
   - 워치 자동 시뮬레이션(iOS/Android Watch Test 화면)에서도 투구수가 함께 표시·증가하는지.

### Rollback

- 백엔드: `git revert` 한 번. DB 변경 없음.
- 클라이언트: 구버전 앱은 새 키를 무시하므로 자동 fallback. 별도 작업 불필요.
