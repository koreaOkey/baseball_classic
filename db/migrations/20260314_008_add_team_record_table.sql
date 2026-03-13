create table if not exists public.team_record (
  id bigint generated always as identity primary key,
  upper_category_id text,
  category_id text not null,
  season_code text not null,
  team_id text not null,
  team_name text not null,
  team_short_name text,
  ranking integer,
  order_no integer,
  game_type text,
  wra double precision,
  game_count integer,
  win_game_count integer,
  drawn_game_count integer,
  lose_game_count integer,
  game_behind double precision,
  continuous_game_result text,
  last_five_games text,
  offense_hra double precision,
  defense_era double precision,
  payload_json jsonb,
  observed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (category_id, season_code, team_id)
);

create index if not exists idx_team_record_category_season_rank
  on public.team_record(category_id, season_code, ranking);

drop trigger if exists trg_team_record_set_updated_at on public.team_record;
create trigger trg_team_record_set_updated_at
before update on public.team_record
for each row execute function public.set_updated_at();

alter table public.team_record enable row level security;

drop policy if exists "public_read_team_record" on public.team_record;
create policy "public_read_team_record"
on public.team_record
for select
to anon, authenticated
using (true);
