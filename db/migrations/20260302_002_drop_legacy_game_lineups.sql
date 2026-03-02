-- Drop legacy JSON snapshot lineup table. The project now uses normalized lineup_slots.

drop table if exists public.game_lineups;
