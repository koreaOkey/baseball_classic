## 1. 아키텍처 설계
- [x] 1.1 하이브리드 Push + Pull 복구 방식 설계 및 확정
- [x] 1.2 데이터 우선순위 분리 정책 수립

## 2. 연결 안정화
- [x] 2.1 heartbeat(ping/pong) 메커니즘 구현
- [x] 2.2 지수 백오프 재연결 (1s → 2s → 5s → 10s) 적용
- [x] 2.3 재연결 직후 state 스냅샷 + 누락 이벤트 보정

## 3. 중복/순서 보장
- [x] 3.1 cursor 기반 이벤트 dedupe 구현
- [x] 3.2 클라이언트 측 정렬 후 렌더링

## 4. Redis 도입
- [x] 4.1 redis_bus.py 구현
- [x] 4.2 Railway Redis 서비스 연동
- [x] 4.3 backend ingest 후 Redis publish → subscribe → local event_bus 전달
