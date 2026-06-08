-- Add schedule fields used by /games?date= filtering and schedule card ordering.
-- Existing production databases may already have these columns; keep this idempotent.

alter table public.games
  add column if not exists game_date text,
  add column if not exists start_time text;

update public.games
set game_date = concat(
  substring(id from 1 for 4),
  '-',
  substring(id from 5 for 2),
  '-',
  substring(id from 7 for 2)
)
where game_date is null
  and id ~ '^[0-9]{8}'
  and substring(id from 1 for 4)::integer between 2000 and 2100
  and substring(id from 5 for 2)::integer between 1 and 12
  and substring(id from 7 for 2)::integer between 1 and 31;

create index if not exists idx_games_game_date_start_time
  on public.games(game_date, start_time);
