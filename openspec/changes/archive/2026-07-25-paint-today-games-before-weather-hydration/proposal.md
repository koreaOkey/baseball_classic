# Paint Today Games Before Weather Hydration (Android)

## Why

안드로이드 홈 "오늘의 경기"가 간헐적으로 십수 초 늦게 표시됐다. 원인은 `MainActivity`의 오늘 경기 갱신 로직이 `/games` 응답(≈310ms)을 받은 뒤에도 날씨 하이드레이션이 끝날 때까지 스냅샷 반영을 미루는 구조:

- `/games`의 인라인 `weather`는 백엔드 KMA 프리워밍 캐시가 식으면(재배포·베이스타임 교체·KMA 실패 시 실패 캐시 TTL 60초) null로 내려온다.
- 이때 앱이 경기당 `/games/{id}/weather`를 **순차** 호출했고, KMA 지연 시 호출당 2~8초(앱 5초 타임아웃 → 499 포기 포함)로 누적 ~17초 확인 (2026-07-25 07:58 UTC 프로덕션 로그).
- 하루 첫 실행이면 로컬 캐시 날짜 불일치로 peek 페인트도 없어 그동안 빈 화면.

## What Changes

- `MainActivity` 오늘 경기 LaunchedEffect: fresh `/games` 결과를 **즉시** `todayGamesSnapshot`에 반영. 이전 스냅샷의 날씨는 신설 동기 헬퍼 `mergePreservedGameWeather`로 보존.
- `hydrateMissingGameWeather`를 페인트 이후 후속 단계로 분리하고, 누락 날씨 fetch를 `async`/`awaitAll`로 **병렬화** (순차 5회 → 동시 실행, 최악 지연 = 단일 호출 시간).
- 시그니처 변경: `hydrateMissingGameWeather(games, previousGames)` → 보존은 merge 헬퍼가 전담, 하이드레이션은 `(games)`만 받아 fetch만 수행.

## Capabilities

### home-today-games

- 오늘의 경기 카드(점수·상태 포함)는 날씨 API 상태와 무관하게 `/games` 응답 즉시 표시된다.
- 누락 날씨는 백그라운드 병렬 조회로 도착 시 갱신되며, 실패해도 경기 표시에 영향 없다.

## Impact

- `apps/mobile/.../MainActivity.kt` (imports + LaunchedEffect + 헬퍼 분리/병렬화)
- 백엔드·iOS·워치 변경 없음. `:app:compileDebugKotlin` 통과.
- 후속(별도 change): 백엔드 KMA 실패 시 last-known-good 예보 stale 서빙 → 하이드레이션 발동 자체 감소. iOS upcoming 하이드레이션 동일 패턴 점검.
