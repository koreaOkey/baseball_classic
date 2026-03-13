-- Add denormalized team/game context columns to lineup and boxscore tables.

alter table public.game_lineup_slots
  add column if not exists player_team text,
  add column if not exists game_date text,
  add column if not exists home_team text,
  add column if not exists away_team text;

alter table public.game_batter_stats
  add column if not exists player_team text,
  add column if not exists game_date text,
  add column if not exists home_team text,
  add column if not exists away_team text;

alter table public.game_pitcher_stats
  add column if not exists player_team text,
  add column if not exists game_date text,
  add column if not exists home_team text,
  add column if not exists away_team text;

update public.game_lineup_slots ls
set
  game_date = g.game_date,
  home_team = g.home_team,
  away_team = g.away_team,
  player_team = case
    when ls.team_side = 'home' then g.home_team
    when ls.team_side = 'away' then g.away_team
    else null
  end
from public.games g
where g.id = ls.game_id
  and (
    ls.game_date is distinct from g.game_date
    or ls.home_team is distinct from g.home_team
    or ls.away_team is distinct from g.away_team
    or ls.player_team is null
  );

update public.game_batter_stats bs
set
  game_date = g.game_date,
  home_team = g.home_team,
  away_team = g.away_team,
  player_team = case
    when bs.team_side = 'home' then g.home_team
    when bs.team_side = 'away' then g.away_team
    else null
  end
from public.games g
where g.id = bs.game_id
  and (
    bs.game_date is distinct from g.game_date
    or bs.home_team is distinct from g.home_team
    or bs.away_team is distinct from g.away_team
    or bs.player_team is null
  );

update public.game_pitcher_stats ps
set
  game_date = g.game_date,
  home_team = g.home_team,
  away_team = g.away_team,
  player_team = case
    when ps.team_side = 'home' then g.home_team
    when ps.team_side = 'away' then g.away_team
    else null
  end
from public.games g
where g.id = ps.game_id
  and (
    ps.game_date is distinct from g.game_date
    or ps.home_team is distinct from g.home_team
    or ps.away_team is distinct from g.away_team
    or ps.player_team is null
  );
