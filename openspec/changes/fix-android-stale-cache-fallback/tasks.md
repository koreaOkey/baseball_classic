# Tasks

## 코드 변경
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/data/BackendGamesRepository.kt:162`: `fetchTodayGamesCached` fallback에 `cachedDate == today` 가드 추가
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/data/BackendGamesRepository.kt:209`: `fetchTodayTeamRecordCached` fallback에 `cachedDate == today && cachedTeam == selectedTeam.name` 가드 추가

## 검증
- [x] 사용자 보고: 앱 데이터 삭제 후 "오늘 경기 없음" 정상 표시 확인 (가설 검증)
- [x] `fetchTodayUpcomingGamesCached`(line 276)는 이미 `cacheMatches` 가드 있음 — 영향 없음 확인
- [ ] 빌드 후 실기기 검증: 휴식일 + 네트워크 실패 조합에서 "오늘 경기 없음" 정상 표시
- [ ] iOS 동등 함수에 동일 패턴 회귀 없는지 점검 (NSCache 메모리 기반이라 영향 없을 것으로 추정, 확인만)

## 후속
- [ ] Android 차기 릴리즈(versionCode bump)에 포함
- [ ] 동일 캐시 패턴(다른 도메인) 재사용 시 fallback 가드를 기본형으로 하도록 메모리화
