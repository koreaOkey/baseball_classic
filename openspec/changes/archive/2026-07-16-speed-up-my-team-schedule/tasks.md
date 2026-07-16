## Backend

- [x] `/games?team=` 파라미터 + `_team_filter_labels` (코드→저장 라벨 확장, 미지원 400)
- [x] `_games_list_cache_ttl` — 오늘 미포함 범위 TTL 600초
- [x] 캐시 키에 team 반영, 테스트 2건 추가 (92 passed)

## Android

- [x] `fetchMyTeamGamesRangePayload` 단일 요청 + 월 청크 폴백
- [x] `peekMyTeamScheduleRangeCache` (TTL 무관 조회 + staleness)
- [x] HomeScreen 시트 SWR: 캐시 즉시 렌더 → 백그라운드 갱신, 스피너는 무캐시 시에만
- [x] compileDebugKotlin·testDebugUnitTest 통과

## iOS

- [x] `fetchTeamGamesRangePayload` 단일 요청 + 구서버 응답 감지 폴백
- [x] `peekMyTeamScheduleRangeCache` + 홈 카드/시즌 시트 캐시 scope 분리
- [x] HomeScreen SWR 적용, 스피너 조건 `loading && isEmpty`
- [x] xcodebuild BUILD SUCCEEDED

## 배포 검증

- [ ] 프로덕션 `/games?team=LG&from=&to=` 실측 (latency + 응답 팀 필터 확인)
- [ ] 과거 범위 재호출 시 10분 캐시 동작 확인
- [ ] 앱 릴리즈 빌드에 포함 (iOS/Android 다음 제출)
