-- 분풀이(venting) 모드 백엔드 Phase 2 — regret 캐시 / 감독 / 지표 / 팀 랭킹 집계.
-- TODO(venting): 활성화 시점에 적용. 다크 배포 단계에서는 적용 보류.
--   백엔드 startup(create_all)이 기본 테이블은 자동 생성하지만, 이 파일은 RLS·트리거·
--   부분 인덱스를 포함한 캐노니컬 정의다. BASEHAPTIC_VENTING_BACKEND_ENABLED 를 켜기 전
--   활성화 절차에서 적용한다:
--     psql -f db/migrations/20260810_001_add_venting_backend_phase2.sql
-- 전부 CREATE TABLE IF NOT EXISTS — 기존 테이블 무변경(additive only).

-- 1. team_manager: 팀별 감독 디렉터리(시즌 단위 수동 관리, 경질 시 새 행 + 이전 행 effective_to)
create table if not exists public.team_manager (
  id bigint generated always as identity primary key,
  team_code text not null,
  manager_name text not null,
  season text not null,
  effective_from timestamptz,
  effective_to timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_team_manager_team_season
  on public.team_manager(team_code, season);
-- 팀별 '현재 감독' 1명 보장(effective_to IS NULL 행은 팀당 최대 1건)
create unique index if not exists uniq_team_manager_current
  on public.team_manager(team_code) where effective_to is null;

drop trigger if exists trg_team_manager_set_updated_at on public.team_manager;
create trigger trg_team_manager_set_updated_at
  before update on public.team_manager
  for each row execute function public.set_updated_at();

alter table public.team_manager enable row level security;

drop policy if exists "public_read_team_manager" on public.team_manager;
create policy "public_read_team_manager"
  on public.team_manager
  for select
  to anon, authenticated
  using (true);

-- 2. venting_regret_cache: 경기 종료 후 산정한 패배팀 관점 regret-top5 (실명 미저장)
create table if not exists public.venting_regret_cache (
  id bigint generated always as identity primary key,
  game_id text not null,
  team_code text not null,
  items jsonb not null,
  manager_name text,
  source text not null default 'rule_fallback',
  computed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint venting_regret_cache_source_chk
    check (source in ('llm', 'rule_fallback')),
  constraint uq_venting_regret_cache_game_team unique (game_id, team_code)
);

drop trigger if exists trg_venting_regret_cache_set_updated_at on public.venting_regret_cache;
create trigger trg_venting_regret_cache_set_updated_at
  before update on public.venting_regret_cache
  for each row execute function public.set_updated_at();

alter table public.venting_regret_cache enable row level security;

drop policy if exists "public_read_venting_regret_cache" on public.venting_regret_cache;
create policy "public_read_venting_regret_cache"
  on public.venting_regret_cache
  for select
  to anon, authenticated
  using (true);

-- 3. venting_event: 분풀이 지표 raw 이벤트 (team 필수 — 팀 랭킹 집계 기반)
create table if not exists public.venting_event (
  id bigint generated always as identity primary key,
  event_type text not null,
  entry_source text,
  team text not null,
  game_id text,
  user_id uuid,
  client_ts timestamptz,
  platform text not null default 'unknown',
  created_at timestamptz not null default now(),
  constraint venting_event_type_chk
    check (event_type in ('room_enter', 'destroy_complete', 'retry_prompt_shown', 'retry_ad_start')),
  constraint venting_event_platform_chk
    check (platform in ('ios', 'android', 'unknown'))
);

create index if not exists idx_venting_event_team_created
  on public.venting_event(team, created_at desc);
create index if not exists idx_venting_event_type
  on public.venting_event(event_type);

alter table public.venting_event enable row level security;

-- 본인 이벤트만 insert (인증 유저). 조회 정책 없음(집계 테이블로만 노출).
drop policy if exists "user_insert_own_venting_event" on public.venting_event;
create policy "user_insert_own_venting_event"
  on public.venting_event
  for insert
  to authenticated
  with check (user_id is null or (select auth.uid()) = user_id);

-- 4. venting_team_daily: 팀별 일간 분풀이 방 실행 카운트 캐시
create table if not exists public.venting_team_daily (
  team text not null,
  date date not null,
  count integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (team, date)
);

create index if not exists idx_venting_team_daily_date
  on public.venting_team_daily(date desc);

drop trigger if exists trg_venting_team_daily_set_updated_at on public.venting_team_daily;
create trigger trg_venting_team_daily_set_updated_at
  before update on public.venting_team_daily
  for each row execute function public.set_updated_at();

alter table public.venting_team_daily enable row level security;

drop policy if exists "public_read_venting_team_daily" on public.venting_team_daily;
create policy "public_read_venting_team_daily"
  on public.venting_team_daily
  for select
  to anon, authenticated
  using (true);

-- 5. venting_team_season: 팀별 시즌 누적 분풀이 방 실행 카운트 캐시
create table if not exists public.venting_team_season (
  team text not null,
  season text not null,
  count integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (team, season)
);

create index if not exists idx_venting_team_season_count
  on public.venting_team_season(season, count desc);

drop trigger if exists trg_venting_team_season_set_updated_at on public.venting_team_season;
create trigger trg_venting_team_season_set_updated_at
  before update on public.venting_team_season
  for each row execute function public.set_updated_at();

alter table public.venting_team_season enable row level security;

drop policy if exists "public_read_venting_team_season" on public.venting_team_season;
create policy "public_read_venting_team_season"
  on public.venting_team_season
  for select
  to anon, authenticated
  using (true);
