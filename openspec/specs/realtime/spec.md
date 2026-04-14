# Realtime Specification

## Purpose
WebSocket과 이벤트 버스를 통한 실시간 경기 데이터 브로드캐스트 시스템.

## Requirements

### Requirement: WebSocket 경기 스트림
시스템은 경기별 WebSocket 채널을 제공해야 한다(MUST).

#### Scenario: 클라이언트 연결
- GIVEN 클라이언트가 경기를 구독하려 할 때
- WHEN WS /ws/games/{gameId} 에 연결하면
- THEN 실시간 state/events 업데이트를 수신한다

#### Scenario: 연결 해제 후 복구
- GIVEN WebSocket 연결이 끊어졌을 때
- WHEN 클라이언트가 재연결하면
- THEN lastCursor 기준으로 누락 이벤트를 보정받고 state 스냅샷을 동기화한다

### Requirement: 하이브리드 Push + Pull 아키텍처
시스템은 평소 Push, 끊김 시 Pull 복구 방식을 사용해야 한다(MUST).

#### Scenario: 정상 상태 (Push)
- GIVEN WebSocket 연결이 정상일 때
- WHEN 새 이벤트가 발생하면
- THEN 즉시 Push로 전달한다

#### Scenario: 재연결 복구 (Pull)
- GIVEN 연결이 끊겼다 복구되었을 때
- WHEN 재연결 직후
- THEN /games/{gameId}/events?after={lastCursor} + /games/{gameId}/state 1회 Pull로 보정한다

### Requirement: 데이터 우선순위 분리
최우선 데이터는 즉시, 부가 데이터는 지연 허용으로 전달해야 한다(MUST).

#### Scenario: 우선순위 전달
- GIVEN 이벤트가 발생했을 때
- WHEN 클라이언트에 전달하면
- THEN score, inning, BSO, bases는 즉시 반영하고, event_type, 설명, 메타데이터는 지연을 허용한다

### Requirement: Heartbeat 및 재연결 정책
시스템은 ping/pong heartbeat와 지수 백오프 재연결을 지원해야 한다(MUST).

#### Scenario: 지수 백오프 재연결
- GIVEN 연결이 끊어졌을 때
- WHEN 클라이언트가 재연결을 시도하면
- THEN 1s → 2s → 5s → 10s 간격으로 백오프하며 상한 10s를 유지한다

### Requirement: Redis Pub/Sub 이벤트 팬아웃
멀티 인스턴스 환경에서 Redis Pub/Sub를 통해 이벤트를 팬아웃해야 한다(SHOULD).

#### Scenario: 멀티 인스턴스 브로드캐스트
- GIVEN 백엔드가 복수 인스턴스로 실행될 때
- WHEN 인제스트 완료 후 Redis에 publish하면
- THEN 모든 인스턴스가 subscribe하여 로컬 event_bus로 브로드캐스트한다

### Requirement: WebSocket 전송 직렬화
동일 WebSocket에 대한 `send_json` 호출은 항상 직렬화되어야 한다(MUST). Starlette WebSocket은 concurrent send에 대해 안전하지 않으므로, 초기 메시지 전송과 브로드캐스트가 겹치거나 두 개의 브로드캐스트가 겹치는 경우에도 프레이밍 충돌이나 드롭이 발생하지 않아야 한다.

#### Scenario: 초기 송신과 브로드캐스트 중첩
- GIVEN 클라이언트가 WS 핸드셰이크 직후 `register`되고 서버가 초기 state/events를 전송 중일 때
- WHEN 같은 세션을 대상으로 브로드캐스트가 발생하면
- THEN per-connection send lock에 의해 초기 전송이 끝날 때까지 브로드캐스트 송신이 대기한 뒤, 순차적으로 동일 WebSocket에 안전하게 전달된다 (메시지 순서: 초기 state → 초기 events → broadcast update)

#### Scenario: 동시 브로드캐스트
- GIVEN 동일 게임에 두 개의 broadcast가 거의 동시에 트리거될 때
- WHEN 둘 다 같은 WebSocket으로 송신되면
- THEN per-connection send lock에 의해 두 송신이 순차 실행되어 메시지 손실/프레임 깨짐 없이 모두 전달된다
