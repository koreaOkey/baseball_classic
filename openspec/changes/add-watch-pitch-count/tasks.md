# Tasks

## Backend
- [x] `backend/api/app/schemas.py` — `GameStateOut.pitcherPitchCount: int | None` 추가
- [x] `backend/api/app/services.py:build_game_state` — LIVE 시 `GamePitcherStat` 에서 활성 투수(이름 매칭, `appearance_order desc nulls last`) 의 `pitches_thrown` 조회

## Tests
- [x] `backend/api/tests/test_api.py` — 기존 ingest+state 통합 테스트에 `state_body["pitcherPitchCount"] == 88` 어서션 추가
- [ ] `pytest tests/test_api.py` 전체 통과 (현재 환경에 pytest 미설치 — CI 또는 개발 머신에서 검증)

## iOS Phone
- [x] `ios/mobile/BaseHaptic/Data/BackendGamesRepository.swift` — `LiveGameState.pitcherPitchCount: Int?` 필드 + JSON 파서
- [x] `ios/mobile/BaseHaptic/WatchSync/WatchGameSyncManager.swift` — `sendGameData` 에 `pitcherPitchCount` 파라미터, `pitcher_pitch_count` 키로 페이로드 전송 (`nil` → `-1`)
- [x] `ios/mobile/BaseHaptic/AppDelegate.swift` — APNs background 경로에서 `pitcher_pitch_count` 파싱 후 전달
- [x] `ios/mobile/BaseHaptic/BaseHapticApp.swift` — initial state, `.state`, `.update` 3곳에 `pitcherPitchCount` 전달, signature 에도 포함
- [x] `ios/mobile/BaseHaptic/Screens/WatchTestScreen.swift` — `SimGameState.pitchCount`, runner 자동 카운트/리셋, 화면 표시 줄에 노출

## iOS Watch
- [x] `ios/watch/BaseHapticWatch/Models/GameData.swift` — `pitcherPitchCount: Int?` 필드 + mock 갱신
- [x] `ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift` — `pitcher_pitch_count` 파싱 (`< 0` → `nil`)
- [x] `ios/watch/BaseHapticWatch/Screens/WatchLiveGameScreen.swift` — player info 를 `HStack` 로 분해, `Text("\(count)").contentTransition(.numericText(value:)).id(gameData.pitcher)` 로 카운트업, FINISHED 시 숨김

## Android Phone
- [x] `apps/mobile/.../data/BackendGamesRepository.kt` — `LiveGameState.pitcherPitchCount: Int?` + JSON 파서 (`isNull` 가드)
- [x] `apps/mobile/.../wear/WearGameSyncManager.kt` — `sendGameData` 에 파라미터, `GameDataPayload` 에 필드, DataMap 키 `pitcher_pitch_count` 와 캐시 JSON 모두 갱신
- [x] `apps/mobile/.../service/GameSyncForegroundService.kt` — signature 에 포함, `pushStateToWatch` 호출에서 전달
- [x] `apps/mobile/.../ui/screens/WatchTestScreen.kt` — `SimGameState.pitchCount`, `PITCH_EVENT_TYPES`, runner 자동 카운트/리셋, 화면 표시 줄

## Android Watch
- [x] `apps/watch/.../DataLayerListenerService.kt` — `KEY_PITCHER_PITCH_COUNT` 상수, prefs 에 저장 (`-1` sentinel)
- [x] `apps/watch/.../MainActivity.kt:readGameDataFromPrefs` — prefs 에서 읽어 `< 0` → `null` 변환
- [x] `apps/watch/.../data/GameData.kt` — `pitcherPitchCount: Int?` 필드 + mock 갱신
- [x] `apps/watch/.../ui/components/LiveGameScreen.kt` — player info 2줄(`P {name} · {count}구` / `B {name}`), `AnimatedContent(slideInVertically + slideOutVertically + fade)` + `contentKey = gameData.pitcher` 로 투수 교체 시 애니메이션 스킵
- [x] `apps/watch/.../ui/theme/WatchUiProfile.kt` — `playerInfoOffsetYDp` 를 3 티어 모두 +6dp 상향(`20→26`, `28→34`, `35→41`)

## Verification (앱 빌드 후)
- [ ] iOS Watch: 라이브 경기에서 `P 김재윤 · 12구  B 김성욱` 한 줄 표시, 한 구마다 슬라이드/롤링 애니메이션
- [ ] Android Watch: `P 김재윤 · 12구` (위) `B 김성욱` (아래) 2줄 표시, 한 구마다 슬라이드 업
- [ ] 두 플랫폼 모두 투수 교체 직후 다운카운트 애니메이션 없이 새 투수+새 카운트 즉시 갱신
- [ ] 종료/취소 경기에서는 투구수 영역 미표시
- [ ] iOS/Android Watch Test 자동 시뮬레이션에서도 투구수가 함께 표시·증가

## Non-Goals (별도 change 로 분리)
- 폰 메인 LiveGameScreen 의 투구수 표시
- 투구수 리미트 햅틱 알림 (예: 100구)
- 투수별 상세 통계 카드
