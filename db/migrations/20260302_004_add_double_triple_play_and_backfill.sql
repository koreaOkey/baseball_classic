-- Extend event type check to include DOUBLE_PLAY/TRIPLE_PLAY,
-- then backfill legacy OUT events by description.

alter table public.game_events
drop constraint if exists game_events_event_type_check;

alter table public.game_events
add constraint game_events_event_type_check
check (
  event_type in (
    'BALL',
    'STRIKE',
    'WALK',
    'OUT',
    'DOUBLE_PLAY',
    'TRIPLE_PLAY',
    'HIT',
    'HOMERUN',
    'SCORE',
    'SAC_FLY_SCORE',
    'TAG_UP_ADVANCE',
    'STEAL',
    'OTHER'
  )
);

with triple_updated as (
  update public.game_events
  set event_type = 'TRIPLE_PLAY'
  where event_type = 'OUT'
    and (
      description ilike '%삼중살%'
      or description ilike '%triple play%'
    )
  returning game_id
),
double_updated as (
  update public.game_events
  set event_type = 'DOUBLE_PLAY'
  where event_type = 'OUT'
    and (
      description ilike '%병살%'
      or description ilike '%double play%'
    )
    and not (
      description ilike '%삼중살%'
      or description ilike '%triple play%'
    )
  returning game_id
),
affected_games as (
  select distinct game_id from triple_updated
  union
  select distinct game_id from double_updated
)
update public.games g
set
  last_event_type = le.event_type,
  last_event_desc = le.description,
  last_event_at = le.event_time,
  updated_at = now()
from (
  select e.game_id, e.event_type, e.description, e.event_time
  from public.game_events e
  join (
    select game_id, max(cursor) as max_cursor
    from public.game_events
    group by game_id
  ) latest
    on latest.game_id = e.game_id
   and latest.max_cursor = e.cursor
) le
where g.id = le.game_id
  and g.id in (select game_id from affected_games);
