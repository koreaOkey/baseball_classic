## Why
WebSocket 전용 아키텍처는 연결 끊김 시 데이터 유실 위험이 있음.
실환경에서 끊김을 0으로 가정하지 않고, 빠른 자동 복구를 기본으로 설계해야 함.

## What Changes
- 실시간 전달 아키텍처를 하이브리드(Push + 복구 Pull)로 확정
- Redis Pub/Sub 도입 방침 수립 (멀티 인스턴스 대응)
- 연결 안정화 정책 수립 (heartbeat, 지수 백오프, dedupe)

## Capabilities
### Modified Capabilities
- `realtime`: Push 기본 + Pull 복구 하이브리드 아키텍처로 전환
- `realtime`: Redis Pub/Sub 이벤트 팬아웃 도입 (멀티 인스턴스)

### New Capabilities
- `realtime`: 데이터 우선순위 분리 (최우선: score/inning/BSO/bases, 차선: event_type/메타데이터)

## Impact
- backend/api/app/event_bus.py — 이벤트 브로드캐스트 로직
- backend/api/app/redis_bus.py — Redis Pub/Sub 연동
- backend/api/app/main.py — WebSocket 핸들러 재연결 보정
- 클라이언트(모바일/워치) — cursor 기반 dedupe 및 재연결 로직
