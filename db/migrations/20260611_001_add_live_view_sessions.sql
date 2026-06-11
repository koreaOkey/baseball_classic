create table if not exists public.live_view_sessions (
  id bigserial primary key,
  game_id text not null,
  user_key text not null,
  surface text not null,
  token_key text,
  my_team text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint live_view_sessions_surface_chk
    check (surface in ('ios', 'android', 'watchos', 'wearos')),
  constraint uq_live_view_session_surface
    unique (game_id, user_key, surface)
);

create index if not exists idx_live_view_sessions_game_active
  on public.live_view_sessions (game_id, active);

create index if not exists idx_live_view_sessions_updated_at
  on public.live_view_sessions (updated_at);
