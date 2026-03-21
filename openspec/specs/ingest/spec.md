# Ingest Specification

## Purpose
크롤러로부터 수신한 경기 스냅샷을 검증하고 데이터베이스에 안전하게 저장하는 내부 API.

## Requirements

### Requirement: 스냅샷 수집 API
시스템은 인증된 크롤러의 스냅샷 요청만 수락해야 한다(MUST).

#### Scenario: 정상 인제스트
- GIVEN 유효한 X-API-Key 헤더를 포함한 요청이 들어올 때
- WHEN POST /internal/crawler/games/{gameId}/snapshot 을 호출하면
- THEN 경기 상태, 이벤트, 라인업, 박스스코어를 upsert 한다

#### Scenario: 인증 실패
- GIVEN 잘못된 API Key가 전달될 때
- WHEN 인제스트 API를 호출하면
- THEN 401 Unauthorized를 반환한다

### Requirement: 중복 이벤트 방지
시스템은 동일 이벤트의 중복 저장을 방지해야 한다(MUST).

#### Scenario: 중복 이벤트 수신
- GIVEN 이미 저장된 이벤트와 동일한 (game_id, source_event_id) 조합이 들어올 때
- WHEN 인제스트를 시도하면
- THEN 기존 레코드를 유지하고 중복을 무시한다

### Requirement: 상태 회귀 방지
시스템은 경기 상태가 역방향으로 변경되는 것을 차단해야 한다(MUST).

#### Scenario: LIVE → SCHEDULED 회귀 시도
- GIVEN 경기 상태가 LIVE일 때
- WHEN SCHEDULED 상태의 스냅샷이 들어오면
- THEN 상태 변경을 거부하고 기존 LIVE 상태를 유지한다

#### Scenario: FINISHED → LIVE 회귀 시도
- GIVEN 경기 상태가 FINISHED일 때
- WHEN LIVE 상태의 스냅샷이 들어오면
- THEN 상태 변경을 거부한다

### Requirement: 동시 접근 제어
시스템은 동일 경기에 대한 동시 인제스트 요청을 안전하게 처리해야 한다(MUST).

#### Scenario: 락 타임아웃
- GIVEN 다른 프로세스가 동일 경기를 처리 중일 때
- WHEN 인제스트 요청이 들어오면
- THEN 락 타임아웃까지 대기 후 재시도한다
