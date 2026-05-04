-- TODO(stadium-cheer): 활성화 시점에 적용. 다크 머지 단계에서는 적용 보류.
-- 활성화 절차: psql -f db/migrations/20260502_009_add_cheer_events_and_aggregates.sql

-- 1. cheer_events: 응원 확인 클릭 raw 이벤트 (검증·디버깅 목적, 좌표 90일 후 익명화)
create table if not exists public.cheer_events (
  id bigint generated always as identity primary key,
  user_id uuid not null,
  team_code text not null,
  stadium_code text not null,
  game_id text,
  client_ts timestamptz not null,
  server_ts timestamptz not null default now(),
  lat double precision,
  lng double precision,
  accuracy_m double precision,
  mock_location boolean not null default false,
  app_version text,
  device_id_hash text,
  ip_hash text,
  platform text not null default 'unknown',
  validity_status text not null default 'pending',
  invalidity_reason text,
  is_home_team boolean,
  opponent_team_code text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint cheer_events_validity_status_chk
    check (validity_status in ('pending', 'valid', 'invalid', 'suspicious')),
  constraint cheer_events_platform_chk
    check (platform in ('ios', 'android', 'unknown'))
);

create index if not exists idx_cheer_events_user_date
  on public.cheer_events(user_id, (timezone('Asia/Seoul', client_ts)::date));
create index if not exists idx_cheer_events_team_status
  on public.cheer_events(team_code, validity_status);
create index if not exists idx_cheer_events_stadium_ts
  on public.cheer_events(stadium_code, client_ts desc);
create index if not exists idx_cheer_events_pending
  on public.cheer_events(validity_status, server_ts) where validity_status = 'pending';

-- 사용자 본인 시즌 체크인 목록 조회 (달력 UI / 개인 통계 화면 대비). user_id 기준 + client_ts DESC.
create index if not exists idx_cheer_events_user_clientts
  on public.cheer_events(user_id, client_ts desc);

-- 1일 1체크인: 같은 user_id + KST 날짜에는 valid 1건만 허용 (부분 unique index)
create unique index if not exists uniq_cheer_events_user_kstdate_valid
  on public.cheer_events(user_id, (timezone('Asia/Seoul', client_ts)::date))
  where validity_status = 'valid';

drop trigger if exists trg_cheer_events_set_updated_at on public.cheer_events;
create trigger trg_cheer_events_set_updated_at
  before update on public.cheer_events
  for each row execute function public.set_updated_at();

alter table public.cheer_events enable row level security;

drop policy if exists "user_read_own_cheer_events" on public.cheer_events;
create policy "user_read_own_cheer_events"
  on public.cheer_events
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists "user_insert_own_cheer_events" on public.cheer_events;
create policy "user_insert_own_cheer_events"
  on public.cheer_events
  for insert
  to authenticated
  with check ((select auth.uid()) = user_id);

-- 2. team_checkin_daily: 팀별 일간 valid 카운트 캐시
create table if not exists public.team_checkin_daily (
  team_code text not null,
  date date not null,
  count integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (team_code, date)
);

create index if not exists idx_team_checkin_daily_date
  on public.team_checkin_daily(date desc);

drop trigger if exists trg_team_checkin_daily_set_updated_at on public.team_checkin_daily;
create trigger trg_team_checkin_daily_set_updated_at
  before update on public.team_checkin_daily
  for each row execute function public.set_updated_at();

alter table public.team_checkin_daily enable row level security;

drop policy if exists "public_read_team_checkin_daily" on public.team_checkin_daily;
create policy "public_read_team_checkin_daily"
  on public.team_checkin_daily
  for select
  to anon, authenticated
  using (true);

-- 3. team_checkin_season: 팀별 시즌 누적 valid 카운트 캐시
create table if not exists public.team_checkin_season (
  team_code text not null,
  season text not null,
  count integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (team_code, season)
);

create index if not exists idx_team_checkin_season_season_count
  on public.team_checkin_season(season, count desc);

drop trigger if exists trg_team_checkin_season_set_updated_at on public.team_checkin_season;
create trigger trg_team_checkin_season_set_updated_at
  before update on public.team_checkin_season
  for each row execute function public.set_updated_at();

alter table public.team_checkin_season enable row level security;

drop policy if exists "public_read_team_checkin_season" on public.team_checkin_season;
create policy "public_read_team_checkin_season"
  on public.team_checkin_season
  for select
  to anon, authenticated
  using (true);
