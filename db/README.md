# DB

Supabase(PostgreSQL) 스키마/마이그레이션을 관리하는 영역입니다.

## 현재 적용 상태
- 대상 Supabase 프로젝트: `snrafqoqpmtoannnnwdq` (ap-northeast-2)
- MCP로 적용 완료된 마이그레이션:
1. `init_basehaptic_core_schema`
2. `harden_rls_and_indexes`
3. `create_game_lineups_table`
4. `expand_game_summary_and_boxscore_tables`
5. `drop_legacy_game_lineups`

## 폴더 구조
- `migrations/20260217_001_init_basehaptic_core_schema.sql`
- `migrations/20260217_002_harden_rls_and_indexes.sql`
- `migrations/20260218_003_expand_game_events_event_type_check.sql`
- `migrations/20260218_004_add_walk_event_type_check.sql`
- `migrations/20260302_001_expand_game_summary_and_boxscore_tables.sql`
- `migrations/20260302_002_drop_legacy_game_lineups.sql`
- `migrations/20260302_003_fix_stats_uniqueness_and_outs.sql`
- `migrations/20260308_005_add_pitcher_change_event_type_check.sql`
- `migrations/20260308_006_add_half_inning_change_event_type_check.sql`

## 테이블
### 경기 데이터
- `public.games`: 경기 스냅샷 상태
  - 점수, 이닝, B/S/O, 주자 상태, 타자/투수, 상태(LIVE/SCHEDULED/FINISHED)
  - 요약 컬럼: `home_hits`, `away_hits`, `home_home_runs`, `away_home_runs`, `home_outs_total`, `away_outs_total`
  - 최근 이벤트: `last_event_type`, `last_event_desc`, `last_event_at`
- `public.game_events`: 이벤트 타임라인
  - `cursor`(증분 조회 커서), `source_event_id`, `event_type`, `event_time`
  - 중복 방지: `unique(game_id, source_event_id)`
- `public.game_lineup_slots`: 타순 단위 정규화 라인업
  - `unique(game_id, team_side, batting_order)` 로 팀별 1~9번 타순 슬롯 관리
- `public.game_batter_stats`: 선수별 타자 박스스코어
  - 타수/안타/타점/홈런/볼넷/삼진/도루 등 누적 지표
- `public.game_pitcher_stats`: 선수별 투수 박스스코어
  - 아웃카운트, 피안타, 실점/자책, 사사구, 삼진, 투구수 등
- `public.game_notes`: 경기 메모/특이사항
  - 결승타, 폭투, 심판 정보 같은 텍스트성 요약

### 테마 데이터
- `public.themes`: 테마 마스터(카탈로그)
  - `id`, `team_code`, `version`, `price_krw`, `is_active`
  - 앱 내장 리소스와 `theme_id` 매핑용 기준 테이블
- `public.user_theme_purchases`: 사용자별 구매 이력
  - `user_id (auth.users FK)`, `theme_id`, `order_id`, `status`, `purchased_at`
- `public.user_theme_settings`: 사용자별 현재 적용 테마
  - `user_id` PK, `active_theme_id`

## RLS 정책
- `games`, `game_events`, `themes`, `game_lineup_slots`, `game_batter_stats`, `game_pitcher_stats`, `game_notes`: 읽기 허용 정책 포함
- `user_theme_purchases`, `user_theme_settings`: `auth.uid()` 기준 본인 행만 조회/수정
- 성능 경고 대응: `(select auth.uid())` 형태로 정책 보정

## 인덱스
- `games(status, updated_at desc)`
- `game_events(game_id, cursor)`
- `game_events(game_id, event_time desc)`
- `game_lineup_slots(game_id, team_side, batting_order)`
- `game_batter_stats(game_id, team_side, batting_order)`
- `game_pitcher_stats(game_id, team_side, appearance_order)`
- `game_notes(game_id, created_at desc)`
- `user_theme_purchases(user_id, purchased_at desc)`
- `user_theme_purchases(user_id, theme_id)`
- `user_theme_purchases(theme_id)`
- `user_theme_settings(active_theme_id)`

## 기본 시드
`themes`에 KBO 10개 팀 기본 테마(`*_base_v1`)가 삽입되어 있습니다.

## 백엔드 연결 시 참고
`backend/api`는 코드 기본값이 SQLite이지만, 환경변수 `BASEHAPTIC_DATABASE_URL`을 Supabase Postgres 연결 문자열로 설정하면 동일 스키마로 동작합니다.

주의:
- SQLAlchemy 드라이버는 `postgresql+psycopg://...` 형식을 사용해야 합니다.
- 로컬 네트워크/OS에 따라 Direct host(`db.<project-ref>.supabase.co`)보다 Session Pooler가 더 안정적일 수 있습니다.

예시 형식:
- Session Pooler: `postgresql+psycopg://postgres.snrafqoqpmtoannnnwdq:<password>@aws-1-ap-northeast-2.pooler.supabase.com:5432/postgres`
- Direct host: `postgresql+psycopg://postgres:<password>@db.snrafqoqpmtoannnnwdq.supabase.co:5432/postgres`

## Event Type (Current)

`public.game_events.event_type` currently supports:

- `BALL`
- `STRIKE`
- `WALK`
- `OUT`
- `HIT`
- `HOMERUN`
- `SCORE`
- `SAC_FLY_SCORE`
- `TAG_UP_ADVANCE`
- `STEAL`
- `HALF_INNING_CHANGE`
- `PITCHER_CHANGE`
- `OTHER`

Classification notes (crawler -> backend):

- `HIT`: only hit results that imply batter advancement (single/double/triple/infield hit/bunt hit text)
- `pitchResult=H` (e.g. `N구 타격`) is treated as `OTHER` because it is contact-in-play, not final result
- failed steal (`도루실패` + out) is classified as `OUT`
- successful steal is classified as `STEAL`
- `볼넷`/`고의사구` is classified as `WALK`

## Migration Notes (2026-02-18)

Added migrations:

- `migrations/20260218_003_expand_game_events_event_type_check.sql`
- `migrations/20260218_004_add_walk_event_type_check.sql`

These extend `game_events_event_type_check` to allow newly introduced event types.

## Migration Notes (2026-03-02)

Added migration:

- `migrations/20260302_001_expand_game_summary_and_boxscore_tables.sql`

This migration adds:

- `games` summary columns for team-level progress (`*_hits`, `*_home_runs`, `*_outs_total`) and latest event (`last_event_*`)
- normalized lineup and boxscore tables:
  - `game_lineup_slots`
  - `game_batter_stats`
  - `game_pitcher_stats`
  - `game_notes`

## Migration Notes (2026-03-02, follow-up)

Added migration:

- `migrations/20260302_002_drop_legacy_game_lineups.sql`

This migration removes legacy JSON snapshot table:

- dropped `public.game_lineups`
- project now uses `game_lineup_slots` as the canonical lineup table

## Migration Notes (2026-03-02, constraints fix)

Added migration:

- `migrations/20260302_003_fix_stats_uniqueness_and_outs.sql`

This migration updates uniqueness strategy for stats tables:

- removes name-only unique constraints from `game_batter_stats`, `game_pitcher_stats`
- adds partial unique indexes:
  - `(game_id, team_side, player_id)` when `player_id is not null`
  - fallback unique key with order when `player_id is null`

## Migration Notes (2026-03-08, event type fix)

Added migration:

- `migrations/20260308_005_add_pitcher_change_event_type_check.sql`

This migration updates `game_events_event_type_check` to include:

- `PITCHER_CHANGE`

## Migration Notes (2026-03-08, half-inning event type)

Added migration:

- `migrations/20260308_006_add_half_inning_change_event_type_check.sql`

This migration updates `game_events_event_type_check` to include:

- `HALF_INNING_CHANGE`

## Recent Changes (2026-03-07)

- Game-level start time is now part of ingest/output flow:
  - logical column: `games.start_time` (`HH:MM`)
- Event-level pitcher/batter context is now stored:
  - logical columns: `game_events.pitcher`, `game_events.batter`
- Backend startup includes schema guards for existing DBs to ensure these columns exist.
- Event type set now includes `PITCHER_CHANGE` across crawler/backend mapping.
