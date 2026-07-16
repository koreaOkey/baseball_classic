## 1. 백엔드

- [x] 1.1 `_prewarm_weather_forecasts()` — 오늘~+3일 SCHEDULED 경기 조회 후 `build_weather_summary(allow_network=True)`로 KMA 캐시 워밍
- [x] 1.2 `_weather_prewarm_loop()` 20분 주기 백그라운드 태스크 + lifespan 등록/정리
- [x] 1.3 서비스 키 미설정·DB 백오프 시 프리워밍 스킵 가드
- [x] 1.4 `FORECAST_ROWS` 1000→1500 (+3일차 예보 잘림 방지)
- [x] 1.5 프리워밍 테스트 추가 (`test_weather_prewarm_warms_forecast_range_games_with_network`) — 전체 97 passed

## 2. iOS

- [x] 2.1 `gameStartWeatherSummary` / `gameWithWeather` private 해제 (모듈 공유)
- [x] 2.2 `HomeScreen.loadUpcomingGames()`에 `hydrateUpcomingGameWeather` 적용 (+3일 이내, weather 누락 시만)
- [x] 2.3 시뮬레이터 Debug 빌드 통과

## 3. Android

- [x] 3.1 `toGameStartWeatherSummary` internal 노출
- [x] 3.2 `HomeScreen` upcoming produceState에 `hydrateUpcomingGameWeather` 적용
- [x] 3.3 `:app:compileDebugKotlin` 통과

## 4. 검증

- [x] 4.1 프로덕션 백엔드 실측: `/games` 목록·단건·`/games/{id}/weather` 모두 날씨 정상 반환 확인
- [ ] 4.2 백엔드 배포 후 로그에서 `[weather-prewarm]` 워밍 확인 + 캐시 콜드 상태에서 목록 응답 weather 포함 확인
- [ ] 4.3 실기기에서 다가오는 경기 카드 날씨 표시 확인 (iOS/Android)
