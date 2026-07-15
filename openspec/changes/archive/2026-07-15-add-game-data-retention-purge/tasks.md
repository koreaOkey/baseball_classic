## Backend

- [x] `_purge_expired_game_rows()` 구현 — 7일 보존, 10k 배치 + 커밋 + 0.5s 대기, notes 우선 삭제, games 행 보존
- [x] `game_date` NULL 폴백 (`created_at` 기준)
- [x] 일일 퍼지 루프 통합 + KST 04:00 정렬 (`_seconds_until_next_kst_hour`)
- [x] 테스트: 오래된 경기 상세 삭제 + games/최근 경기 보존 + 배치 루프 경로, no-op 케이스 (90 passed)

## 배포 후 (선택)

- [ ] 첫 드레인 후 Supabase SQL 에디터에서 `VACUUM (VERBOSE) game_events;` 1회 실행해 공간 재사용 확정 (무잠금, 선택 사항)
- [ ] `SELECT pg_size_pretty(pg_total_relation_size('game_events'));` 로 전후 크기 확인
