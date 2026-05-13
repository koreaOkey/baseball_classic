## ADDED Requirements

### Requirement: 라이브 스코어 ongoing 노티 항상 노출
경기 LIVE 상태인 동안 폰·워치 양쪽에 라이브 스코어 ongoing 노티(또는 Live Activity)가 항상 노출되어야 한다(MUST). 이 영역은 사용자 설정 UI에 노출하지 않으며 토글로 제어하지 않는다.

#### Scenario: LIVE 전환 시 노티 게시
- **WHEN** 관심 경기가 LIVE로 전환된 시점에
- **THEN** 폰에는 라이브 스코어 표시 채널(iOS Live Activity 또는 Android ongoing notification)이 게시되고, 워치 앱이 페어링·활성 상태이면 워치에도 ongoing 노티가 게시된다

#### Scenario: 경기 종료 시 노티 종료
- **WHEN** 경기가 종료 상태로 전환되면
- **THEN** 폰·워치 양쪽의 라이브 스코어 노티/Live Activity가 자동으로 제거된다

### Requirement: 같은 식별자 in-place 갱신
이벤트(스코어 변경·이닝 전환·BSO 변화)가 발생할 때마다 라이브 스코어 노티는 동일한 식별자로 in-place 교체되어야 한다(MUST). 매 이벤트마다 새로운 노티 스택을 쌓아서는 안 된다.

#### Scenario: 스코어 변경 시 교체
- **WHEN** 경기 도중 스코어 또는 이닝이 변경되면
- **THEN** 기존 라이브 스코어 노티가 같은 식별자로 새 콘텐츠로 교체되며 알림 스택이 중복되지 않는다

### Requirement: 최근 이벤트 1개 노출
라이브 스코어 ongoing 노티는 현재 스코어 외에도 가장 최근에 발생한 이벤트 한 줄(예: "김현수 솔로 홈런 · 9회초")을 함께 노출해야 한다(MUST).

#### Scenario: 최근 이벤트 라인 갱신
- **WHEN** 새 이벤트가 발생하면
- **THEN** 노티의 최근 이벤트 라인이 해당 이벤트로 즉시 교체된다

### Requirement: 선택 이벤트 long-look expand
사용자가 선호 이벤트 타입(`preferred_event_types`)으로 선택한 이벤트가 발생할 때에만 라이브 스코어 노티가 일시적으로 long-look(또는 BigText) 스타일로 expand되며 햅틱이 함께 발화해야 한다(MUST). 선택되지 않은 이벤트는 노티 in-place 갱신만 수행하고 expand·햅틱을 발화하지 않는다.

#### Scenario: 선택 이벤트 expand
- **GIVEN** 사용자가 `preferred_event_types`에 HOMERUN을 포함한 상태에서
- **WHEN** HOMERUN 이벤트가 발생하면
- **THEN** 라이브 스코어 노티가 일시적으로 expand 스타일로 강조되고 햅틱이 발화되며 약 3초 후 ongoing 표시로 복귀한다

#### Scenario: 미선택 이벤트 조용한 갱신
- **GIVEN** 사용자가 `preferred_event_types`에 WALK를 포함하지 않은 상태에서
- **WHEN** WALK 이벤트가 발생하면
- **THEN** 라이브 스코어 노티의 스코어·최근 이벤트 라인만 in-place 갱신되며 expand·햅틱은 발생하지 않는다

### Requirement: 워치 우선 햅틱 정책
한 이벤트로 인한 햅틱은 워치·폰 중 한 곳에서만 발화되어야 한다(MUST). 워치가 페어링되어 있고 활성 상태(reachable 또는 동등 신호)이면 워치에서만 햅틱을 발화하고 폰은 햅틱·헤드업 알림을 발화하지 않는다. 다만 폰의 라이브 스코어 노티/Live Activity 갱신은 항상 동작한다.

#### Scenario: 워치 활성 시 폰 햅틱 suppress
- **GIVEN** 워치가 페어링되고 활성 상태일 때
- **WHEN** 선택 이벤트가 발생하면
- **THEN** 워치에서만 햅틱이 발화되고 폰 햅틱·헤드업은 발생하지 않으며 폰 Live Activity/ongoing 노티는 정상적으로 갱신된다

#### Scenario: 워치 비활성 시 폰 발화
- **GIVEN** 워치가 페어링되지 않았거나 비활성/도달 불가 상태일 때
- **WHEN** 선택 이벤트가 발생하면
- **THEN** 폰에서 햅틱과 헤드업 노티가 정상적으로 발화된다

### Requirement: 손목 미착용 보호 정책
워치가 비활성/도달 불가 상태로 판단되는 경우(시나리오 3·6) 폰도 햅틱·헤드업 노티를 발화하지 않아야 한다(MUST). 단, 폰 라이브 스코어 노티/Live Activity 갱신은 정상 동작한다.

#### Scenario: 워치 OFF 추정 상태에서 진동 안 함
- **GIVEN** 워치 페어링은 되어 있으나 비활성/도달 불가 상태로 추정될 때
- **WHEN** 이벤트가 발생하면
- **THEN** 폰은 햅틱·헤드업 노티를 발화하지 않고, 폰 라이브 스코어 노티/Live Activity만 갱신된다

### Requirement: 미수신 이벤트 큐잉 금지
폰·워치가 모두 OFF 상태일 때 발생한 이벤트는 큐잉하지 않으며 디바이스가 다시 깨어났을 때 누락된 이벤트들을 차례로 재발화해서는 안 된다(MUST). 깨어났을 때는 라이브 스코어 노티가 현재 시점의 최신 스코어와 가장 최근 이벤트 1개만 표시한다.

#### Scenario: 깨어난 직후 최신 스코어만 표시
- **GIVEN** 디바이스가 일정 시간 OFF 상태였고 그 사이에 다수의 이벤트가 발생한 뒤
- **WHEN** 디바이스가 다시 사용 가능 상태로 돌아오면
- **THEN** 라이브 스코어 노티가 현재 시점의 최신 스코어와 가장 최근 이벤트 1개만 표시하고 그 사이의 이벤트들은 개별 알림으로 재발화되지 않는다

### Requirement: 사용자 선호 이벤트 타입 저장
사용자별 `preferred_event_types`를 백엔드에 저장하고 폰·워치 클라이언트가 동기화하여 long-look expand 가드에 사용해야 한다(MUST).

#### Scenario: 설정 저장
- **WHEN** 사용자가 모바일 설정에서 이벤트 타입을 선택·해제하고 저장하면
- **THEN** 변경 사항이 백엔드에 영속 저장되고 동일 사용자의 폰·워치에 즉시 또는 다음 동기화 시점에 반영된다

### Requirement: 폰 foreground 시 노티 suppress
폰 앱이 foreground 상태일 때는 푸시 노티의 헤드업 표시를 suppress 해야 한다(MUST). 라이브 스코어 갱신은 인앱 UI로 처리한다.

#### Scenario: 폰 fg 시 헤드업 안 띄움
- **WHEN** 폰 앱이 foreground 상태에서 이벤트 푸시를 수신하면
- **THEN** 헤드업 노티는 표시하지 않고 인앱 라이브 화면이 데이터를 갱신한다
