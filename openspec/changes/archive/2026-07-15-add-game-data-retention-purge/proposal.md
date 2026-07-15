## Why

`game_events` 를 비롯한 경기 상세 테이블(라인업/타자·투수 스탯/노트)이 매 경기 누적되어 DB 크기가 계속 증가한다. 이벤트를 읽는 경로는 `/games/{id}/events`(라이브 상세)와 최근 20건 조회뿐으로, 모두 경기 진행 중·직후에만 사용된다 — 종료 후 오래된 상세 데이터는 죽은 데이터다.

## What Changes

- 일일 퍼지 태스크(기존 `_push_data_purge_loop`)에 경기 상세 데이터 보존기간 퍼지를 추가한다.
  - **보존 기간 7일** (`GAME_DATA_RETENTION_DAYS`): `game_date` 가 KST 기준 7일보다 오래된 경기의 `game_notes` → `game_events` → `game_lineup_slots` → `game_batter_stats` → `game_pitcher_stats` 행을 삭제 (notes 를 events 보다 먼저 지워 FK SET NULL 부하 방지).
  - **`games` 행은 보존** — 과거 스코어/결과 표시는 유지된다.
  - **배치 삭제**: 배치당 최대 10,000행, 배치마다 커밋 + 0.5초 대기 — 트랜잭션을 짧게 유지해 운영 영향 최소화. 라이브 경기와 game_id 가 겹치지 않아 행 잠금 경합 없음.
  - `game_date` 가 NULL 인 행은 `created_at` 기준으로 판정.
- 퍼지 루프 실행 시각을 **매일 KST 04:00** 로 정렬 (경기·자정 크롤러 임포트와 겹치지 않는 시간대). 부팅 120초 후 1회 즉시 실행은 유지 — 배포 직후 밀린 백로그가 배치 단위로 자동 드레인된다(별도 수동 대량 삭제 불필요).

## Capabilities

### Modified Capabilities

- `backend-api`: 경기 상세 데이터는 보존 기간(7일) 이후 자동 삭제되어 DB 크기가 하루 데이터량 × 보존일 수준으로 상한 고정되어야 한다.

## Impact

- Backend: `app/main.py` — `_purge_expired_game_rows()` 신설, `_push_data_purge_loop` 에 통합, `_seconds_until_next_kst_hour()` 로 04:00 KST 스케줄링. 테스트 2건 추가(90 passed).
- DB Migration: 불필요.
- 운영 참고: 삭제된 공간은 autovacuum 이 재사용하므로 크기 증가는 멈춘다. 이미 커진 파일 크기 자체를 회수하려면 최초 드레인 후 Supabase SQL 에디터에서 일반 `VACUUM` 1회 실행(선택, 무잠금). `VACUUM FULL` 은 테이블 잠금이 걸리므로 사용하지 않는다.
