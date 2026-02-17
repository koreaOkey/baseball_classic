-- Supabase result check queries for baseball backend

select count(*) as games_count from public.games;
select count(*) as events_count from public.game_events;

select
  id,
  home_team,
  away_team,
  home_score,
  away_score,
  status,
  inning,
  updated_at
from public.games
order by updated_at desc
limit 20;

select
  cursor,
  game_id,
  source_event_id,
  event_type,
  description,
  event_time
from public.game_events
order by cursor desc
limit 50;
