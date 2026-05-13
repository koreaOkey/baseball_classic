## ADDED Requirements

### Requirement: 이벤트 push payload에 이벤트 타입 포함
실시간 이벤트 push payload에는 클라이언트가 사용자 `preferred_event_types`와 비교할 수 있도록 이벤트 타입 식별자(예: SCORE, HOMERUN, HIT, WALK, STEAL 등)가 포함되어야 한다(MUST).

#### Scenario: payload에 타입 필드
- **WHEN** 백엔드가 클라이언트(폰·워치)에 이벤트 push payload를 발행하면
- **THEN** payload에 표준화된 이벤트 타입 식별자 필드가 포함되어 있어 클라이언트가 long-look expand 가드에 활용할 수 있다

### Requirement: 미수신 이벤트 큐잉 금지
백엔드는 디바이스(폰·워치)가 일시적으로 도달 불가일 때 이벤트를 큐잉했다가 재발화해서는 안 된다(MUST). 도달 가능 시점의 최신 경기 상태만 클라이언트가 가져갈 수 있도록 한다.

#### Scenario: 도달 불가 디바이스에 큐잉 안 함
- **GIVEN** 디바이스가 일정 시간 동안 push 도달 불가 상태였을 때
- **WHEN** 디바이스가 다시 사용 가능 상태로 돌아오면
- **THEN** 백엔드는 그 사이의 누락된 이벤트들을 개별 push로 재발행하지 않으며 클라이언트는 최신 경기 상태를 별도 동기화 경로(웹소켓 재연결 또는 풀)로만 복원한다
