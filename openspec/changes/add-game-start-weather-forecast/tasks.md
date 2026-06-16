## 1. Backend Weather API

- [x] 1.1 Add backend settings and API schemas for optional game weather summary and hourly forecast rows.
- [x] 1.2 Implement stadium-to-forecast lookup, dome handling, forecast time selection, and safe external API fallback.
- [x] 1.3 Attach optional weather summary to `/games` responses without blocking game list failures.
- [x] 1.4 Add a game hourly weather endpoint for today forecast bottom sheet usage.
- [x] 1.5 Confirm no DB migration is required for weather data.

## 2. Android Home UI

- [x] 2.1 Extend Android game models and repository parsing for optional weather summary and hourly forecast data.
- [x] 2.2 Show home-team “(홈)” text in today and upcoming game cards.
- [x] 2.3 Add scheduled-game weather summary under today card team rows with copy format `경기 시작 예보 · 구장 · N시 기준 · 날씨 · 기온 · 강수확률`.
- [x] 2.4 Add upcoming-game weather summary without vertical divider styling.
- [x] 2.5 Add today-game weather bottom sheet with loading, error, empty, retry, and start-time highlight states.

## 3. Platform Impact

- [x] 3.1 Verify Android mobile impact and build.
- [x] 3.2 Verify iOS mobile compatibility with optional backend fields.
- [x] 3.3 Verify Wear OS and watchOS are unaffected by the new weather UI.

## 4. Validation

- [x] 4.1 Add or update backend tests for weather summary, dome fallback, missing API key fallback, and hourly endpoint behavior.
- [x] 4.2 Run targeted OpenSpec, backend, and Android verification commands.
