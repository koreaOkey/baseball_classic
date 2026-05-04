## Why

2026-04-27 오늘 경기 없는 휴식일에, 안드로이드 phone 앱이 "오늘의 경기 없음" 대신 **어제 경기 결과**를 표시하는 회귀가 발견됨. 동일 백엔드를 쓰는 iOS는 정상적으로 "오늘 경기 없음" 표시. 사용자가 안드로이드 앱 데이터 삭제 후 재진입하면 정상 표시되는 것까지 검증되어 Android 측 캐시 fallback 로직 버그로 확정.

코드 리뷰 결과 `BackendGamesRepository.kt`의 두 fetch 함수에서 같은 패턴의 결함 확인:

1. **`fetchTodayGamesCached()` line 162**: fetch가 null을 반환할 때(네트워크 실패/타임아웃) `cachedDate == today` 체크 없이 `cachedPayload`를 그대로 반환 → 어제 캐시가 살아남아 "어제 경기"로 표시됨.
2. **`fetchTodayTeamRecordCached()` line 209**: 동일한 구조의 fallback. 어제 팀 전적이 그대로 노출될 수 있음.

대조군: 같은 파일의 `peekTodayGamesCache()`(line 127), `fetchTodayUpcomingGamesCached()` 의 fallback(line 276 `cacheMatches` 가드)은 정상적으로 날짜 체크함.

iOS와 동작이 다른 이유: iOS는 NSCache 메모리 기반이라 앱 재시작 시 자동 클리어. Android는 SharedPreferences 영구 저장이라 어제 캐시가 살아남음.

## What Changes

- `fetchTodayGamesCached()` line 162 fallback에 `cachedDate == today` 가드 추가.
- `fetchTodayTeamRecordCached()` line 209 fallback에 `cachedDate == today && cachedTeam == selectedTeam.name` 가드 추가 (메인 캐시 hit 분기와 동일한 조건).

## Capabilities

### Modified Capabilities
- `android-mobile-cache`: 캐시 fallback이 어제 데이터를 반환하지 않도록 수정. 외부 동작은 fetch 성공 시 동일.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 네트워크 실패 시 fallback이 사라져 "오늘 경기 없음" 표시가 더 자주 보임 | 정확성이 stale 데이터보다 우선 — 사용자가 어제 경기를 오늘 경기로 오인하는 게 더 큰 문제. 빈 응답은 자연스러운 휴식일 표시이므로 UX 측면에서도 정상 |
| Upcoming/UpcomingGames fallback도 같은 문제일 가능성 | line 276은 이미 `cacheMatches` 가드가 있음 — 영향 없음 (검증 완료) |

## Status

- [x] 구현 완료 (`BackendGamesRepository.kt` 두 함수 fallback 가드 추가)
- [x] 사용자 가설 검증 완료 (앱 데이터 삭제 후 정상 표시)
- [ ] 빌드 후 실기기 검증 (휴식일/네트워크 실패 시나리오)
- [ ] Android 차기 릴리즈에 포함
