-- 활성화 절차: psql -f db/migrations/20260507_010_allow_canceled_postponed_game_status.sql
-- 또는 Supabase SQL Editor 에 그대로 붙여넣어 실행.
--
-- 배경:
--   초기 스키마(20260217_001)의 games.status check 제약이 'LIVE'/'SCHEDULED'/'FINISHED' 만 허용해
--   크롤러가 이미 매핑하고 있는 'CANCELED'/'POSTPONED' 스냅샷이 UPDATE 시 거부되어 왔다.
--   (예: 2026-05-07 롯데–KT 우천 취소가 17:48 KST 이후로 영영 갱신되지 않는 사례.)
--
--   백엔드(GameStatus enum, normalize_status, _resolve_next_game_status)와
--   iOS/Android 폰 앱은 이미 두 상태를 인지·렌더링하므로, 제약 1줄만 풀어주면 회귀 없이 정상화된다.

alter table public.games drop constraint if exists games_status_check;

alter table public.games
  add constraint games_status_check
    check (status in ('LIVE','SCHEDULED','FINISHED','CANCELED','POSTPONED'));
