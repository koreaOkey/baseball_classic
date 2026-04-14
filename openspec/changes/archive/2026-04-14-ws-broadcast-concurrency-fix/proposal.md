## Why
Railway Hobby 플랜 + uvicorn 워커 8개 구성으로 부하 테스트(2026-04-14)를 진행하며 WebSocket broadcast 드롭 문제를 추적했다. 초기엔 전달률이 **200명 91.5% / 500명 72% / 1000명 91%** 수준에서 **9~30% 구조적 드롭**이 관찰됐고, instrumentation을 붙여 원인을 끝까지 파고든 결과 실제 드롭 경로는 2단계 레이스와 1건의 테스트 아티팩트였다.

### 실제 원인 (instrumentation counter로 확증)
1. **WS 핸들러 내부 concurrent send race** — `event_bus.connect()`가 connections set에 바로 추가한 뒤 `_load_game_initial_data_cached` 중 비동기 대기가 발생. 같은 시점에 ingest 브로드캐스트가 동일 WebSocket에 `send_json`을 동시 실행. Starlette WebSocket은 concurrent send에 대해 안전하지 않아 프레이밍 충돌/드롭 발생.
2. **Broadcast 경로에서 global `GameEventBus._lock` 경쟁** — 초기 구현은 broadcast가 스냅샷용 `_lock`을 잡고, per-WS `safe_send` 안에서 lazy-init 시 같은 `_lock`을 재획득. 500+ 동시 register가 이 글로벌 락을 계속 점유해 broadcast fanout이 수십 초 지연, listen 윈도우 초과로 클라이언트가 놓침.
3. **테스트 스크립트의 이벤트 필드명 오인** — 백엔드가 `to_event_out()`에서 `source_event_id → id`로 리네이밍한 뒤 broadcast payload에 실었는데, 테스트 스크립트는 `sourceEventId` 키로만 `test_id`를 매칭 → 모든 브로드캐스트를 크롤러 메시지로 오판하고 집계에서 스킵. (이건 순전히 측정 버그이지 시스템 드롭 아님.)

## What Changes
- `backend/api/app/event_bus.py`
  - `GameEventBus.connect()`를 `accept()`만 수행하도록 단순화. set 등록은 `register()`로 분리.
  - `register(game_id, websocket)` — connections set 추가 및 per-ws `asyncio.Lock` 생성을 `_lock` 내부에서 원자적으로 수행.
  - `safe_send(websocket, message)` — per-ws lock 기반 직렬화. lock이 없을 때 lazy-init은 **global `_lock` 없이** 즉석 생성 (asyncio 단일 스레드 모델 상 안전).
  - `broadcast()`의 타겟 스냅샷도 global `_lock` 없이 수행 → broadcast fanout이 register/disconnect 버스트와 경쟁하지 않음.
  - stale 클라이언트 정리 시에만 `_lock` 획득.
  - 디버깅용 stats 카운터 추가 (`register`, `disconnect`, `broadcast_calls`, `broadcast_targets`, `send_ok`, `send_fail`, `broadcast_no_targets`).
- `backend/api/app/redis_bus.py`
  - subscribe loop의 subscribe 직후 `self.subscribed_at` 기록, `SUBSCRIBED` WARNING 로그.
  - 디버깅용 stats 카운터 (`publish_ok`, `publish_fail`, `sub_received`, `sub_skipped_own`, `sub_forwarded`, `sub_errors`, `sub_reconnects`).
- `backend/api/app/main.py`
  - `websocket_game_stream`, `websocket_team_record_stream` 핸들러를 `connect → register → 초기 state/events(safe_send) → receive 루프(safe_send)` 순으로 재배치.
  - `/debug/relay-stats` 엔드포인트 신설 — 현재 워커의 pid/hostname/source_instance_id/stats 반환. 샘플링 기반으로 8 워커 전체 상태를 재구성하는 데 사용.
  - `logging.basicConfig(level=WARNING, force=True)` — INFO 레벨 stderr 포화가 broadcast 지연을 유발했음.
- `scripts/test_ws_load.py`
  - broadcast 인식 로직: `type=update` 메시지의 `payload.state`/`payload.events`를 확인.
  - 테스트 아티팩트 격리: `events[].id` 또는 `events[].sourceEventId`가 현재 실행의 `test_id` 접두사로 시작하는지 검사해 크롤러 브로드캐스트와 분리.
  - replay-on-reconnect 인식: 느린 클라이언트가 초기 events 로드 시 Redis cache에서 test events를 받는 경우도 성공으로 집계 (실제 앱 재연결 복구 경로와 동일).
- `scripts/sample_relay.py` (신설, `/tmp/sample_relay.py`로 배포) — `/debug/relay-stats`를 N회 병렬 호출해 pid별 최신 스냅샷을 aggregate. 테스트 전/후 호출 후 diff로 어느 워커에서 몇 건이 새는지 식별.
- `openspec/specs/realtime/spec.md` — WebSocket 전송 직렬화 요구사항과 등록 타이밍 요구사항 추가.
- Railway 환경변수 — `NIXPACKS_START_CMD` 교정(`$PORT` 누락 및 `--workers 8` 반영), Custom Start Command를 `uvicorn app.main:app --host 0.0.0.0 --port $PORT --workers 8`로 설정. `BASEHAPTIC_DB_POOL_SIZE=1`, `BASEHAPTIC_DB_MAX_OVERFLOW=1`로 Supabase Session Pooler MaxClientsInSessionMode 회피.

## Non-Goals
- **WebSocket 핸드셰이크 용량 확장** — 1000명 burst 시 ~200명 핸드셰이크 timeout. 단일 replica의 accept 큐 한계이며 replica scale-out은 별도 change.
- **Supabase Transaction Pooler 전환** — session pooler에 `pool_size=1`로 묶어 회피 중. 실사용에서 DB 대기가 관측되면 별도 change로 port 6543 + `prepare_threshold=None` 작업 예정.
- **시크릿 로테이션** — 이번 세션에서 평문 노출된 `DATABASE_URL`/`SUPABASE_SERVICE_ROLE_KEY`/`APNS_KEY_BASE64`/`CRAWLER_API_KEY`는 사용자 수동 작업으로 별도 진행.
- **broadcast ack / at-least-once 보장** — 현재 구조는 "정상 경로에서 드롭 없음" 수준. 네트워크 단절로 인한 유실은 기존 pull 복구(`lastCursor`)에 맡김.

## Verification
부하 테스트 (2026-04-14 기준, 최종 배포 후):
- 200 클라이언트: **200/200 (100%) 수신**, state/events 지연 avg 1.1s.
- 500 클라이언트: **500/500 (100%) 수신**, 지연 avg 2.3s.
- 1000 클라이언트: 핸드셰이크 성공 800/1000, **성공 클라이언트 기준 800/800 (100%) 수신**, 지연 avg 1.3s.

instrumentation 검증:
- `send_fail = 0` (모든 규모).
- `broadcast_targets`가 `reg`보다 적은 경우는 register 완료 전 broadcast fanout이 일어난 케이스로, 해당 클라이언트는 replay-on-reconnect(Redis cache 기반 초기 events)로 보정되어 최종 전달률 100%.
- 8 워커 전원이 Redis `SUBSCRIBED` 로그를 남기고 `sub_received` 카운터가 일치.

spec 검증: `openspec validate 2026-04-14-ws-broadcast-concurrency-fix`
