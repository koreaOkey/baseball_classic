# DB

Supabase(PostgreSQL) 스키마/마이그레이션을 관리하는 영역입니다.

## 현재 적용 상태
- 대상 Supabase 프로젝트: `snrafqoqpmtoannnnwdq` (ap-northeast-2)
- MCP로 적용 완료된 마이그레이션:
1. `init_basehaptic_core_schema`
2. `harden_rls_and_indexes`

## 폴더 구조
- `migrations/20260217_001_init_basehaptic_core_schema.sql`
- `migrations/20260217_002_harden_rls_and_indexes.sql`

## 테이블
### 경기 데이터
- `public.games`: 경기 스냅샷 상태
  - 점수, 이닝, B/S/O, 주자 상태, 타자/투수, 상태(LIVE/SCHEDULED/FINISHED)
- `public.game_events`: 이벤트 타임라인
  - `cursor`(증분 조회 커서), `source_event_id`, `event_type`, `event_time`
  - 중복 방지: `unique(game_id, source_event_id)`

### 테마 데이터
- `public.themes`: 테마 마스터(카탈로그)
  - `id`, `team_code`, `version`, `price_krw`, `is_active`
  - 앱 내장 리소스와 `theme_id` 매핑용 기준 테이블
- `public.user_theme_purchases`: 사용자별 구매 이력
  - `user_id (auth.users FK)`, `theme_id`, `order_id`, `status`, `purchased_at`
- `public.user_theme_settings`: 사용자별 현재 적용 테마
  - `user_id` PK, `active_theme_id`

## RLS 정책
- `games`, `game_events`, `themes`: 읽기 허용 정책 포함
- `user_theme_purchases`, `user_theme_settings`: `auth.uid()` 기준 본인 행만 조회/수정
- 성능 경고 대응: `(select auth.uid())` 형태로 정책 보정

## 인덱스
- `games(status, updated_at desc)`
- `game_events(game_id, cursor)`
- `game_events(game_id, event_time desc)`
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

