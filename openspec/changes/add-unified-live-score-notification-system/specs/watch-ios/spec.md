## ADDED Requirements

### Requirement: 워치 ongoing 라이브 스코어 노티
워치(iOS) 앱은 라이브 경기 동안 동일 식별자를 사용하는 ongoing 노티를 통해 최신 스코어·이닝·BSO와 최근 이벤트 1개를 노출해야 한다(MUST). 워치 앱이 foreground 상태일 때는 노티 표시를 suppress 한다.

#### Scenario: 백그라운드/홈에서 노티 노출
- **GIVEN** 워치 앱이 background 또는 워치 홈 상태일 때
- **WHEN** 라이브 경기 데이터가 갱신되면
- **THEN** 동일 식별자의 ongoing 노티가 최신 스코어·이닝·BSO·최근 이벤트로 in-place 교체되어 노티 센터에서 확인 가능하다

#### Scenario: foreground 시 노티 suppress
- **GIVEN** 워치 앱이 foreground 상태일 때
- **WHEN** 이벤트 푸시를 수신하면
- **THEN** 푸시 노티 표시 옵션은 빈 값으로 처리되어 화면에 배너가 노출되지 않는다

### Requirement: 선택 이벤트 long-look expand 가드
워치(iOS)는 사용자 `preferred_event_types`에 포함된 이벤트에만 long-look expand 표시와 햅틱을 발화해야 한다(MUST). 선택되지 않은 이벤트는 ongoing 노티 in-place 갱신만 수행한다.

#### Scenario: 선택 이벤트 expand
- **GIVEN** 사용자가 `preferred_event_types`에 HOMERUN을 포함한 상태일 때
- **WHEN** HOMERUN 이벤트가 워치에 도착하면
- **THEN** ongoing 노티가 long-look으로 expand되고 햅틱이 발화되며 약 3초 후 ongoing으로 복귀한다

#### Scenario: 미선택 이벤트 조용한 갱신
- **GIVEN** 사용자가 `preferred_event_types`에 WALK를 포함하지 않은 상태일 때
- **WHEN** WALK 이벤트가 워치에 도착하면
- **THEN** ongoing 노티의 스코어·최근 이벤트 라인만 in-place 갱신되며 expand·햅틱은 발생하지 않는다
