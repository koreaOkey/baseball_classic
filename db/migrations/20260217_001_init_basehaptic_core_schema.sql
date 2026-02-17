create table public.games (
  id text primary key,
  home_team text not null,
  away_team text not null,
  status text not null default 'SCHEDULED' check (status in ('LIVE','SCHEDULED','FINISHED')),
  inning text not null default '-',
  home_score integer not null default 0 check (home_score >= 0),
  away_score integer not null default 0 check (away_score >= 0),
  ball_count smallint not null default 0 check (ball_count between 0 and 4),
  strike_count smallint not null default 0 check (strike_count between 0 and 3),
  out_count smallint not null default 0 check (out_count between 0 and 3),
  base_first boolean not null default false,
  base_second boolean not null default false,
  base_third boolean not null default false,
  pitcher text,
  batter text,
  observed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index idx_games_status_updated_at on public.games(status, updated_at desc);

create table public.game_events (
  cursor bigint generated always as identity primary key,
  game_id text not null references public.games(id) on delete cascade,
  source_event_id text not null,
  event_type text not null check (event_type in ('BALL','STRIKE','OUT','HIT','HOMERUN','SCORE','OTHER')),
  description text not null default '',
  event_time timestamptz not null,
  haptic_pattern text,
  payload_json jsonb,
  created_at timestamptz not null default now(),
  unique (game_id, source_event_id)
);

create index idx_game_events_game_cursor on public.game_events(game_id, cursor);
create index idx_game_events_game_event_time on public.game_events(game_id, event_time desc);

create table public.themes (
  id text primary key,
  name text not null,
  team_code text not null,
  version integer not null default 1 check (version > 0),
  price_krw integer not null default 0 check (price_krw >= 0),
  is_active boolean not null default true,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (team_code, version)
);

create table public.user_theme_purchases (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  theme_id text not null references public.themes(id) on delete restrict,
  order_id text not null unique,
  status text not null default 'PAID' check (status in ('PENDING','PAID','CANCELLED','REFUNDED')),
  purchased_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index idx_user_theme_purchases_user_purchased_at on public.user_theme_purchases(user_id, purchased_at desc);
create index idx_user_theme_purchases_user_theme on public.user_theme_purchases(user_id, theme_id);

create table public.user_theme_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  active_theme_id text references public.themes(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger trg_games_set_updated_at
before update on public.games
for each row execute function public.set_updated_at();

create trigger trg_themes_set_updated_at
before update on public.themes
for each row execute function public.set_updated_at();

create trigger trg_user_theme_purchases_set_updated_at
before update on public.user_theme_purchases
for each row execute function public.set_updated_at();

create trigger trg_user_theme_settings_set_updated_at
before update on public.user_theme_settings
for each row execute function public.set_updated_at();

alter table public.games enable row level security;
alter table public.game_events enable row level security;
alter table public.themes enable row level security;
alter table public.user_theme_purchases enable row level security;
alter table public.user_theme_settings enable row level security;

create policy "public_read_games"
on public.games
for select
to anon, authenticated
using (true);

create policy "public_read_game_events"
on public.game_events
for select
to anon, authenticated
using (true);

create policy "public_read_active_themes"
on public.themes
for select
to anon, authenticated
using (is_active = true);

create policy "user_read_own_theme_purchases"
on public.user_theme_purchases
for select
to authenticated
using (auth.uid() = user_id);

create policy "user_insert_own_theme_purchases"
on public.user_theme_purchases
for insert
to authenticated
with check (auth.uid() = user_id);

create policy "user_read_own_theme_settings"
on public.user_theme_settings
for select
to authenticated
using (auth.uid() = user_id);

create policy "user_insert_own_theme_settings"
on public.user_theme_settings
for insert
to authenticated
with check (auth.uid() = user_id);

create policy "user_update_own_theme_settings"
on public.user_theme_settings
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

insert into public.themes (id, name, team_code, version, price_krw, is_active, description)
values
  ('theme_doosan_base_v1', '두산 기본 테마', 'DOOSAN', 1, 0, true, '두산 기본 내장 테마'),
  ('theme_lg_base_v1', 'LG 기본 테마', 'LG', 1, 0, true, 'LG 기본 내장 테마'),
  ('theme_kiwoom_base_v1', '키움 기본 테마', 'KIWOOM', 1, 0, true, '키움 기본 내장 테마'),
  ('theme_samsung_base_v1', '삼성 기본 테마', 'SAMSUNG', 1, 0, true, '삼성 기본 내장 테마'),
  ('theme_lotte_base_v1', '롯데 기본 테마', 'LOTTE', 1, 0, true, '롯데 기본 내장 테마'),
  ('theme_ssg_base_v1', 'SSG 기본 테마', 'SSG', 1, 0, true, 'SSG 기본 내장 테마'),
  ('theme_kt_base_v1', 'KT 기본 테마', 'KT', 1, 0, true, 'KT 기본 내장 테마'),
  ('theme_hanwha_base_v1', '한화 기본 테마', 'HANWHA', 1, 0, true, '한화 기본 내장 테마'),
  ('theme_kia_base_v1', 'KIA 기본 테마', 'KIA', 1, 0, true, 'KIA 기본 내장 테마'),
  ('theme_nc_base_v1', 'NC 기본 테마', 'NC', 1, 0, true, 'NC 기본 내장 테마');
