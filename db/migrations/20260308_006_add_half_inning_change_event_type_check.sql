-- Extend event type check to include HALF_INNING_CHANGE.
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
    'HALF_INNING_CHANGE',
    'HIT',
    'HOMERUN',
    'SCORE',
    'SAC_FLY_SCORE',
    'TAG_UP_ADVANCE',
    'STEAL',
    'PITCHER_CHANGE',
    'OTHER'
  )
);
