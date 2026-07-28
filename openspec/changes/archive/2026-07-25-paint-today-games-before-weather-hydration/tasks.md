# Tasks

- [x] 원인 확인: Railway HTTP 로그에서 순차 weather 호출(499 포함) ~17초 지연 확인
- [x] `mergePreservedGameWeather` 신설 — 이전 스냅샷 날씨 동기 보존 후 즉시 페인트
- [x] `hydrateMissingGameWeather` 병렬화 (`async`/`awaitAll`) + 페인트 후속 단계로 이동
- [x] `:app:compileDebugKotlin` 통과
- [ ] 실기기 확인: 콜드 스타트(하루 첫 실행)에 경기 카드 즉시 표시 → 날씨 후속 갱신
- [ ] 후속: 백엔드 KMA last-known-good stale 서빙 (별도 change)
- [ ] 후속: iOS upcoming/오늘 경기 하이드레이션 동일 패턴 점검
