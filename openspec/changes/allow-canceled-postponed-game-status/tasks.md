# Tasks

## DB
- [x] `db/migrations/20260507_010_allow_canceled_postponed_game_status.sql` 작성
- [x] `db/README.md` 마이그레이션 목록 / 테이블 설명 / Migration Notes 갱신
- [ ] **운영 Supabase 에 적용** (사용자 수동 실행)
  - 옵션 A: Supabase Dashboard → SQL Editor 에 SQL 붙여넣기
  - 옵션 B: `psql "$BASEHAPTIC_DATABASE_URL" -f db/migrations/20260507_010_allow_canceled_postponed_game_status.sql`
- [ ] 적용 직후 점검:
  ```sql
  select conname, pg_get_constraintdef(oid)
  from pg_constraint
  where conrelid = 'public.games'::regclass and contype = 'c';

  select id, status, inning, observed_at, updated_at
  from public.games
  where game_date = '2026-05-07';
  ```
  → check 제약에 5개 값이 보이고, 5분 내 `20260507LTKT02026` 가 `status=CANCELED` 로 갱신되어야 함.

## Spec
- [x] `openspec/specs/game-state/spec.md` "경기 상태 전이" Requirement 확장 (delta 적용)

## Verification
- [ ] Railway 로그 grep: `games_status_check` 위반 로그가 적용 시점 이후 0건인지 확인
- [ ] iOS/Android 폰 앱에서 `20260507LTKT02026` 카드가 빨간 "경기 취소" 라벨로 보이는지 육안 확인

## Non-Goals (별도 change 로 분리)
- iOS watchOS `GameData` 모델 리팩터
- Wear OS `MainActivity` 종료 캐시 정리 확장
