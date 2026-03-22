## Why
5경기 동시 ingest + 다수 WebSocket 클라이언트 환경에서 실시간성을 확보하기 위한 최적화 및 한계 측정.

## What Changes
- insert_events IN 쿼리 chunking (100건 단위) + COUNT 기반 중복 early return
- WS on-connect DB 쿼리를 asyncio.to_thread로 비동기화
- event_bus broadcast를 asyncio.gather로 병렬화
- Redis 캐시로 WS on-connect DB 쿼리 제거 (game state + recent events)
- uvicorn 워커 1 → 3 증설
- Supabase idle_in_transaction_session_timeout = 60s 설정

## 부하 테스트 결과 (Railway 무료 플랜, 단일 인스턴스, 3 워커)

| 동시 접속 | 연결 | Broadcast 수신 | Ingest 시간 | 지연 avg | 지연 max | 판정 |
|----------|------|---------------|------------|---------|---------|------|
| 50 | 50/50 | 50/50 | ~1초 | ~1초 | ~1초 | 완벽 |
| 200 | 200/200 | 200/200 | ~1.2초 | ~1.2초 | ~1.3초 | 완벽 |
| 500 | 500/500 | 500/500 | ~1.6초 | ~1.6초 | ~1.8초 | 완벽 |
| 1000 | 1000/1000 | 976/1000 | ~2.3초 | ~2.8초 | ~3.1초 | 양호 (2.4% 누락) |
| 1200 | 1200/1200 | 1196/1200 | ~2.4초 | ~2.7초 | ~3.2초 | 양호 (한계선) |
| 1400 | 1312/1400 | 1307/1312 | ~2.8초 | ~3.2초 | ~3.9초 | 연결 실패 시작 |
| 1500 | 1091/1500 | 1083/1091 | ~3.5초 | ~4.4초 | ~5.2초 | 불안정 |
| 2000 | 2/2000 | - | - | - | - | 불가 |

## 결론

### 부하 없이 사용 가능: ~500명
- 연결 100%, broadcast 100%, 지연 2초 이내
- KBO 5경기 기준 경기당 ~100명

### 부하 발생 시작: ~1000명
- 연결 100%이나 broadcast 2~3% 누락, 지연 3초 전후
- 실시간 중계 앱으로서 수용 가능한 마지노선

### 서비스 불안정: ~1400명 이상
- WS handshake timeout 발생, 연결 실패율 증가
- 지연 3초 이상, broadcast 누락 증가

## 1000명 이상에서 지연이 발생하는 원인

1. **broadcast I/O 병목**: asyncio.gather로 병렬화해도 1000개 WS에 send_json하는 네트워크 I/O 자체가 시간 소모
2. **단일 인스턴스 이벤트루프 한계**: 3 워커지만 각 워커가 담당하는 WS 수가 300+개로 이벤트루프 포화
3. **WS handshake 처리량**: 동시 다량 연결 시 handshake 자체가 CPU/메모리 경합

## 향후 개선 방향 (1000명+ 실시간 지원 시)

### 1. Railway replica 스케일아웃 (가장 효과적)
- Hobby 플랜($5/월) 이상 필요
- 2~3 replica로 WS 클라이언트를 분산
- Redis pub/sub이 이미 구현되어 있어 코드 변경 불필요
- 500명/인스턴스 유지 → 지연 1.6초 수준

### 2. uvicorn 워커 수 증가
- 현재 3 → 5~6으로 늘리면 동시 처리 capacity 증가
- 단, 메모리 제한(무료 플랜 512MB)으로 OOM 위험
- Hobby 플랜에서 메모리 여유가 있으면 시도 가능

### 3. broadcast batching
- 1000개 개별 send_json 대신 일정 수(50~100)씩 묶어서 전송
- 네트워크 I/O 왕복 횟수 감소
- 코드 복잡도 증가 대비 효과는 제한적

### 4. WebSocket 서버 분리
- backend API와 WebSocket 서버를 별도 서비스로 분리
- WS 서버는 Redis에서만 읽고 DB 접근 없음
- 각각 독립적으로 스케일 가능
- 구조 변경이 크므로 장기 과제

## 테스트 스크립트
- `scripts/test_concurrent_ingest.py` — 5경기 동시 ingest 테스트
- `scripts/test_ws_load.py` — 다중 WebSocket + 동시 ingest 부하 테스트

## Impact
- backend/api/app/services.py — insert_events chunking + early return
- backend/api/app/main.py — WS async화 + Redis 캐시 on-connect
- backend/api/app/event_bus.py — broadcast 병렬화
- backend/api/app/redis_bus.py — set_cache/get_cache 추가
- Railway — NIXPACKS_START_CMD (--workers 3)
- Supabase — idle_in_transaction_session_timeout = 60s
