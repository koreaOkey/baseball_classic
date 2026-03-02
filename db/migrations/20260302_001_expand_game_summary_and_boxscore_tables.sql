-- Expand game summary and add normalized lineup/boxscore tables.

alter table public.games
  add column if not exists home_hits integer not null default 0,
  add column if not exists away_hits integer not null default 0,
  add column if not exists home_home_runs integer not null default 0,
  add column if not exists away_home_runs integer not null default 0,
  add column if not exists home_outs_total integer not null default 0,
  add column if not exists away_outs_total integer not null default 0,
  add column if not exists last_event_type text,
  add column if not exists last_event_desc text,
  add column if not exists last_event_at timestamptz;

create table if not exists public.game_lineup_slots (
  id bigint generated always as identity primary key,
  game_id text not null references public.games(id) on delete cascade,
  team_side text not null check (team_side in ('home', 'away')),
  batting_order smallint not null check (batting_order between 1 and 9),
  player_id text,
  player_name text not null,
  position_code text,
  position_name text,
  is_starter boolean not null default false,
  is_active boolean not null default true,
  entered_at_event_cursor bigint references public.game_events(cursor) on delete set null,
  exited_at_event_cursor bigint references public.game_events(cursor) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (game_id, team_side, batting_order)
);

create index if not exists idx_game_lineup_slots_game_side_order
  on public.game_lineup_slots(game_id, team_side, batting_order);

create index if not exists idx_game_lineup_slots_game_side_starter
  on public.game_lineup_slots(game_id, team_side, is_starter);

create table if not exists public.game_batter_stats (
  id bigint generated always as identity primary key,
  game_id text not null references public.games(id) on delete cascade,
  team_side text not null check (team_side in ('home', 'away')),
  player_id text,
  player_name text not null,
  batting_order smallint check (batting_order between 1 and 9),
  primary_position text,
  is_starter boolean not null default false,
  plate_appearances integer not null default 0 check (plate_appearances >= 0),
  at_bats integer not null default 0 check (at_bats >= 0),
  runs integer not null default 0 check (runs >= 0),
  hits integer not null default 0 check (hits >= 0),
  rbi integer not null default 0 check (rbi >= 0),
  doubles integer not null default 0 check (doubles >= 0),
  triples integer not null default 0 check (triples >= 0),
  home_runs integer not null default 0 check (home_runs >= 0),
  walks integer not null default 0 check (walks >= 0),
  strikeouts integer not null default 0 check (strikeouts >= 0),
  stolen_bases integer not null default 0 check (stolen_bases >= 0),
  caught_stealing integer not null default 0 check (caught_stealing >= 0),
  hit_by_pitch integer not null default 0 check (hit_by_pitch >= 0),
  sac_bunts integer not null default 0 check (sac_bunts >= 0),
  sac_flies integer not null default 0 check (sac_flies >= 0),
  left_on_base integer not null default 0 check (left_on_base >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (game_id, team_side, player_name),
  foreign key (game_id, team_side, batting_order)
    references public.game_lineup_slots (game_id, team_side, batting_order)
);

create index if not exists idx_game_batter_stats_game_side_order
  on public.game_batter_stats(game_id, team_side, batting_order);

create table if not exists public.game_pitcher_stats (
  id bigint generated always as identity primary key,
  game_id text not null references public.games(id) on delete cascade,
  team_side text not null check (team_side in ('home', 'away')),
  appearance_order smallint,
  player_id text,
  player_name text not null,
  is_starter boolean not null default false,
  outs_recorded integer not null default 0 check (outs_recorded >= 0),
  hits_allowed integer not null default 0 check (hits_allowed >= 0),
  runs_allowed integer not null default 0 check (runs_allowed >= 0),
  earned_runs integer not null default 0 check (earned_runs >= 0),
  walks_allowed integer not null default 0 check (walks_allowed >= 0),
  strikeouts integer not null default 0 check (strikeouts >= 0),
  home_runs_allowed integer not null default 0 check (home_runs_allowed >= 0),
  batters_faced integer not null default 0 check (batters_faced >= 0),
  at_bats_against integer not null default 0 check (at_bats_against >= 0),
  pitches_thrown integer not null default 0 check (pitches_thrown >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (game_id, team_side, player_name)
);

create index if not exists idx_game_pitcher_stats_game_side_appearance
  on public.game_pitcher_stats(game_id, team_side, appearance_order);

create table if not exists public.game_notes (
  id bigint generated always as identity primary key,
  game_id text not null references public.games(id) on delete cascade,
  team_side text check (team_side in ('home', 'away')),
  note_type text not null,
  note_title text not null default '',
  note_body text not null default '',
  inning text,
  event_cursor bigint references public.game_events(cursor) on delete set null,
  created_at timestamptz not null default now()
);

create index if not exists idx_game_notes_game_created
  on public.game_notes(game_id, created_at desc);

alter table public.game_lineup_slots enable row level security;
alter table public.game_batter_stats enable row level security;
alter table public.game_pitcher_stats enable row level security;
alter table public.game_notes enable row level security;

drop policy if exists public_read_game_lineup_slots on public.game_lineup_slots;
create policy public_read_game_lineup_slots
on public.game_lineup_slots
for select
to anon, authenticated
using (true);

drop policy if exists public_read_game_batter_stats on public.game_batter_stats;
create policy public_read_game_batter_stats
on public.game_batter_stats
for select
to anon, authenticated
using (true);

drop policy if exists public_read_game_pitcher_stats on public.game_pitcher_stats;
create policy public_read_game_pitcher_stats
on public.game_pitcher_stats
for select
to anon, authenticated
using (true);

drop policy if exists public_read_game_notes on public.game_notes;
create policy public_read_game_notes
on public.game_notes
for select
to anon, authenticated
using (true);

drop trigger if exists trg_game_lineup_slots_set_updated_at on public.game_lineup_slots;
create trigger trg_game_lineup_slots_set_updated_at
before update on public.game_lineup_slots
for each row execute function public.set_updated_at();

drop trigger if exists trg_game_batter_stats_set_updated_at on public.game_batter_stats;
create trigger trg_game_batter_stats_set_updated_at
before update on public.game_batter_stats
for each row execute function public.set_updated_at();

drop trigger if exists trg_game_pitcher_stats_set_updated_at on public.game_pitcher_stats;
create trigger trg_game_pitcher_stats_set_updated_at
before update on public.game_pitcher_stats
for each row execute function public.set_updated_at();
