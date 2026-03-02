-- Fix stats uniqueness to avoid collisions on duplicated player names.

alter table public.game_batter_stats
  drop constraint if exists uq_game_batter_stats_player;
alter table public.game_batter_stats
  drop constraint if exists game_batter_stats_game_id_team_side_player_name_key;

create unique index if not exists uq_game_batter_stats_player_id
  on public.game_batter_stats(game_id, team_side, player_id)
  where player_id is not null;

create unique index if not exists uq_game_batter_stats_player_name_fallback
  on public.game_batter_stats(game_id, team_side, player_name, coalesce(batting_order, 0))
  where player_id is null;

alter table public.game_pitcher_stats
  drop constraint if exists uq_game_pitcher_stats_player;
alter table public.game_pitcher_stats
  drop constraint if exists game_pitcher_stats_game_id_team_side_player_name_key;

create unique index if not exists uq_game_pitcher_stats_player_id
  on public.game_pitcher_stats(game_id, team_side, player_id)
  where player_id is not null;

create unique index if not exists uq_game_pitcher_stats_player_name_fallback
  on public.game_pitcher_stats(game_id, team_side, player_name, coalesce(appearance_order, 0))
  where player_id is null;
