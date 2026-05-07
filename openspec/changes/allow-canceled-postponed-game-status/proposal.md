## Why

크롤러·백엔드·iOS/Android 폰 앱은 이미 `CANCELED`/`POSTPONED` 상태를 정식으로 매핑·렌더링하지만, 초기 스키마(`20260217_001_init_basehaptic_core_schema.sql`)의 `games.status` check 제약이 `'LIVE'/'SCHEDULED'/'FINISHED'` 만 허용해 `UPDATE games SET status='CANCELED'` 가 PostgreSQL constraint violation 으로 거부되어 왔다.

실제 사례: 2026-05-07 롯데–KT 우천 취소 경기(`20260507LTKT02026`)가 `observed_at=2026-05-07T08:48:19Z`(17:48 KST) 시점의 `SCHEDULED, "경기전"` 스냅샷에 박제되어, 5분마다 들어오는 `CANCELED` 스냅샷이 모두 거부되는 동안 사용자는 종일 "경기전 18:30" 으로 표시되는 경기를 보게 됐다.

## What Changes

- `db/migrations/20260507_010_allow_canceled_postponed_game_status.sql` 신설
  - `games_status_check` 제약을 drop 후 `'CANCELED'`, `'POSTPONED'` 를 포함한 5개 값으로 재생성
- `openspec/specs/game-state/spec.md` 의 "경기 상태 전이" Requirement 를 확장
  - SCHEDULED → CANCELED / POSTPONED 전이 시나리오 추가
  - SCHEDULED/LIVE 외부에서 들어오는 CANCELED/POSTPONED 는 terminal 로 진행도(progress=2)에 따라 처리됨을 명시
- `db/README.md` 마이그레이션 목록·테이블 설명·Migration Notes 업데이트

## Capabilities

### New Capabilities

(없음 — 이미 코드 레이어에 존재하던 능력을 DB 레이어에서 막혀 있던 부분만 풂)

### Modified Capabilities

- `game-state`: 상태 전이 가능 집합에 `CANCELED`, `POSTPONED` 추가 (terminal)

## Impact

- DB: `public.games.status` check 제약 확장. 기존 행 영향 없음(현재 DB 안에는 두 값이 존재하지 않으므로 데이터 마이그레이션 불필요).
- 백엔드 코드: 변경 없음 ([backend/api/app/schemas.py:23-24](backend/api/app/schemas.py#L23-L24), [backend/api/app/services.py:47-55](backend/api/app/services.py#L47-L55) 이미 두 상태를 인지).
- 폰 앱(iOS/Android): 변경 없음 (`GameStatus` enum과 UI 라벨 이미 갖춤).
- 크롤러: 변경 없음 ([crawler/live_wbc_dispatcher.py:383-405](crawler/live_wbc_dispatcher.py#L383-L405) 이미 `statusInfo` 폴백으로 매핑).

### Non-Goals (별건으로 분리)

- iOS watchOS `GameData.swift` 의 `isLive: Bool` → `GameStatus` enum 리팩터(현재는 CANCELED/POSTPONED 가 단순히 "라이브 아님"으로 떨어짐, 크래시 없음).
- Wear OS `MainActivity.kt:605` 의 종료 캐시 정리에 CANCELED/POSTPONED 추가(다음 동기화 시 자연 해소되므로 블로커 아님).
- `cheer_signals.py` 에서 CANCELED/POSTPONED 게임 응원 시그널 차단(현 흐름상 입력이 들어오지 않아 빈 결과로 안전하게 처리됨).

## Rollout

1. 마이그레이션 SQL 을 Supabase SQL Editor 또는 `psql -f` 로 운영 DB에 적용.
2. 적용 후 5분 이내 다음 dispatcher schedule refresh 사이클에서 `20260507LTKT02026` 행이 `status=CANCELED, inning="경기취소"` 로 자동 정정되는 것을 확인.
3. Railway 로그에서 `gameId=20260507LTKT02026` 관련 `games_status_check` 위반 로그가 더 이상 누적되지 않는 것을 확인.

### Rollback

`alter table public.games drop constraint games_status_check; alter table public.games add constraint games_status_check check (status in ('LIVE','SCHEDULED','FINISHED'));`
단, 롤백 시점에 이미 두 값으로 저장된 행이 존재하면 제약 추가가 거부되므로 사전에 `update games set status='SCHEDULED' where status in ('CANCELED','POSTPONED');` 로 되돌릴 것.
