## Why

홈 화면 "다가오는 경기" 카드에 날씨 예보가 보이지 않는 회귀가 보고됐다. 백엔드 기상청(KMA) 연동 자체는 정상이었지만 세 가지가 겹치면 카드에서 날씨가 사라진다.

1. `/games` 목록 응답은 지연 방지를 위해 KMA 네트워크 조회를 하지 않는다(`allow_weather_network=False`). 서버 인메모리 예보 캐시가 식으면(재배포, 30분 TTL 만료, 3시간마다 베이스타임 교체) 목록의 `weather`는 전부 `null`이 된다.
2. 오늘 경기 카드에는 개별 경기 하이드레이션(`/games/{id}/weather`)이 있지만 다가오는 경기 경로에는 없다.
3. 앱의 일정 캐시 TTL이 6시간이라, 날씨 없는 스냅샷이 한 번 저장되면 최대 6시간 동안 네트워크 없이 그대로 그려진다.

## What Changes

- 백엔드: 예보 지원 범위(오늘~+3일) SCHEDULED 경기의 구장 예보를 20분 주기로 미리 받아 캐시를 데우는 프리워밍 백그라운드 태스크를 추가한다. 이후 목록 응답에도 항상 날씨가 실리므로 iOS·Android 모두 앱 업데이트 없이도 개선된다.
- 백엔드: KMA 요청 `numOfRows`를 1000→1500으로 늘려 +3일차 저녁 슬롯 잘림을 방지한다.
- iOS: 다가오는 경기 카드 로딩 시 예보 범위(+3일) 내 경기의 누락 날씨를 시간별 예보로 직접 채운다(카드 최대 3장 → 요청 최대 3회). 오늘 경기용 하이드레이션 헬퍼(`gameStartWeatherSummary`, `gameWithWeather`)를 공유 가능하게 노출.
- Android: 동일한 다가오는 경기 하이드레이션을 적용한다(`toGameStartWeatherSummary` internal 노출).

## Capabilities

### Modified Capabilities
- `game-weather-forecast`: 목록 응답의 날씨 가용성 보장(프리워밍) + 다가오는 경기 카드 하이드레이션 요구사항 추가

## Impact

- Backend: `app/main.py`(프리워밍 루프 + lifespan 등록), `app/weather.py`(FORECAST_ROWS), `tests/test_api.py`(프리워밍 테스트)
- iOS: `BaseHapticApp.swift`(헬퍼 노출), `Screens/HomeScreen.swift`(`hydrateUpcomingGameWeather`)
- Android: `MainActivity.kt`(확장 함수 노출), `ui/screens/HomeScreen.kt`(`hydrateUpcomingGameWeather`)
- KMA 호출량: 프리워밍은 구장 수만큼만 호출(캐시 공유, 회당 최대 ~9콜 / 20분)
