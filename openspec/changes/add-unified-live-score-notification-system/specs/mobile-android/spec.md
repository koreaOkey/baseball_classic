## ADDED Requirements

### Requirement: 폰 라이브 스코어 ongoing 노티
Android 폰은 라이브 경기 동안 잠금화면·알림 드로어에서 확인 가능한 라이브 스코어 ongoing notification을 게시해야 한다(MUST). 동일 식별자를 사용해 스코어·이닝·BSO·최근 이벤트 1개를 in-place 갱신한다.

#### Scenario: LIVE 동안 노티 노출
- **GIVEN** 사용자 관심 경기가 LIVE 상태일 때
- **WHEN** 폰이 백그라운드 또는 잠금 상태이면
- **THEN** 라이브 스코어 ongoing notification이 잠금화면·알림 드로어에 노출되고 잠금 가시성(공개/비공개)은 사용자 OS 설정을 따른다

#### Scenario: 이벤트 시 in-place 갱신
- **WHEN** 새 이벤트가 발생하면
- **THEN** 동일 식별자의 노티가 새 스코어·최근 이벤트 라인으로 in-place 교체되며 별도 노티가 누적되지 않는다

### Requirement: 워치 우선 햅틱 suppress
워치(Wear OS) 노드가 페어링·연결된 상태에서는 폰 햅틱·헤드업 노티를 발화하지 않아야 한다(MUST). 단, 라이브 스코어 ongoing notification 갱신은 항상 동작한다.

#### Scenario: 워치 연결 시 폰 햅틱 안 함
- **GIVEN** 폰이 페어링된 워치 노드와 연결된 상태일 때
- **WHEN** 선택 이벤트가 발생하면
- **THEN** 폰은 햅틱·헤드업 노티를 발화하지 않고 라이브 스코어 ongoing notification만 갱신된다

### Requirement: 사용자 이벤트 선택 UI
설정 화면에 "선택한 이벤트만 알림" 항목을 노출하고 사용자가 SCORE·HOMERUN·HIT·WALK·STEAL 등 이벤트 타입을 멀티 셀렉트로 지정할 수 있어야 한다(MUST). ongoing 스코어 노티는 설정 UI에 노출하지 않는다.

#### Scenario: 이벤트 선택 저장·반영
- **WHEN** 사용자가 설정 화면에서 이벤트 타입을 선택·해제하고 저장하면
- **THEN** 변경 사항이 백엔드에 저장되고 폰·페어링된 워치에 즉시 반영되어 다음 이벤트부터 적용된다
