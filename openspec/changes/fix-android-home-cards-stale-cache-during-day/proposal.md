## Why

2026-05-10 사용자 제보: 안드로이드 홈 화면의 오늘 경기 카드에 점수가 표시되지 않고, LIVE 카드를 탭해도 워치 동기화 다이얼로그("워치 동기화")가 뜨지 않음.

원인 분석:

- 단말 캐시(`backend_games_cache.xml > today_games_payload`)에 새벽(2026-05-09 22:50 KST 등) 시점의 SCHEDULED + score=0 스냅샷이 박혀 있었음.
- `BackendGamesRepository.fetchTodayGamesCached()` (line 132–168, 수정 전): `cachedDate == today` 면 네트워크 호출 없이 캐시를 그대로 반환 → 게임이 시작되어 백엔드는 LIVE/점수를 내려주는데, 화면은 새벽의 SCHEDULED를 그대로 보여줌.
- `MainActivity` LaunchedEffect (line 469–499, 수정 전): `if (todayGamesLoadedDate == today) return@LaunchedEffect` 로 같은 날 안에서는 한 번만 로드. ON_RESUME 핸들러도 `todayGamesLoadedDate != today` 일 때만 reloadToken 을 올려서 같은 날 재진입 시 새로 fetch 하지 않음.
- 결과: 점수가 0–0 SCHEDULED 로 굳어 "-" 로 표시되고, `onSelectGame` 의 `if (game.status == GameStatus.LIVE && syncedGameId != game.id)` 조건이 false 가 되어 워치 동기화 프롬프트도 트리거되지 않음.

백엔드(`/games?date=YYYY-MM-DD`)는 정상 LIVE/점수 응답 확인. 단말 사이드 단일 회귀.

동일 파일 `fetchTodayUpcomingGamesCached`(다가오는 경기) 는 영속 상태가 아니라 무관. `fetchTodayTeamRecordCached` 도 별 영향 없음(전적은 빈도 낮음).

## What Changes

- `BackendGamesRepository.fetchTodayGamesCached()` 를 **네트워크 우선, 캐시 폴백** 패턴으로 재구성. 캐시 빠른 페인트는 호출부의 `peekTodayGamesCache()` 가 이미 담당.
- `MainActivity` 의 ON_RESUME 핸들러가 매번 `todayGamesReloadToken += 1` 하도록 변경 — 같은 날 안에서도 SCHEDULED→LIVE→FINISHED 전이를 반영.
- `MainActivity` 의 today-games LaunchedEffect 에서 `if (todayGamesLoadedDate == today) return@LaunchedEffect` 조기 반환 제거. reload 트리거 시 캐시는 빈 스냅샷일 때만 페인트(깜빡임 방지)하고 항상 네트워크에서 최신 갱신.

## Capabilities

### Modified Capabilities

- `android-mobile-home-today-games`: 홈 화면 오늘 경기 카드가 같은 날 안에서도 백엔드 상태(SCHEDULED→LIVE→FINISHED, 점수)를 반영. 외부 행동: 앱 포그라운드 진입 시점의 백엔드 상태를 보장.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| ON_RESUME 마다 네트워크 호출 → 트래픽/배터리 부담 | 5팀 × 게임 수만 응답하는 가벼운 GET, 사용자 인지 가능한 진입 빈도라 무시 가능. 폴링은 도입 안 함 |
| 네트워크 실패 시 점수 갱신 지연 | 캐시 폴백은 그대로 유지 — 마지막 성공 응답이 화면에 남음. 기존보다 나빠지지 않음 |
| 첫 진입 시 캐시 SCHEDULED → 네트워크 LIVE 로 깜빡임 | `peekTodayGamesCache()` 는 첫 페인트만, 그 후 네트워크 결과로 매끄럽게 덮어씀. 한 사이클이라 시각적으로 자연스러움 |

## Status

- [x] 구현 완료
  - `apps/mobile/app/src/main/java/com/basehaptic/mobile/data/BackendGamesRepository.kt:132-163` 네트워크 우선으로 재구성
  - `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt:454-498` ON_RESUME 무조건 reload + 조기 반환 제거
- [x] 빌드 성공 (`./gradlew :mobile:assembleDebug`)
- [x] 실기기 설치 후 cache xml 검증: SCHEDULED→LIVE 갱신 확인 (`backend_games_cache.xml` 에 `status:"LIVE"`, 정상 점수 기록됨)
- [ ] 사용자 검증: 홈 카드 점수 표시 + LIVE 카드 탭 시 워치 동기화 프롬프트 노출
- [ ] 다음 Android 릴리즈(versionCode bump) 포함
