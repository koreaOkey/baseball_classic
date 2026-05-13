## ADDED Requirements

### Requirement: 워치 우선 햅틱 suppress
iOS 폰은 페어링된 Apple Watch가 활성·도달 가능 상태일 때 햅틱·헤드업 노티를 발화하지 않아야 한다(MUST). 단, Live Activity 갱신은 항상 동작한다.

#### Scenario: 워치 활성 시 폰 햅틱 suppress
- **GIVEN** 폰이 페어링된 Apple Watch와 연결되어 활성 상태일 때
- **WHEN** 선택 이벤트가 발생하면
- **THEN** 폰은 햅틱·헤드업 노티를 발화하지 않고 Live Activity만 갱신된다

#### Scenario: 워치 비활성 시 폰 발화
- **GIVEN** 폰이 워치와 페어링되지 않았거나 워치가 비활성 상태일 때
- **WHEN** 이벤트가 발생하면
- **THEN** 폰은 헤드업 노티와 햅틱을 정상 발화한다

### Requirement: 사용자 이벤트 선택 UI
설정 화면에 "선택한 이벤트만 알림" 항목을 노출하고 사용자가 SCORE·HOMERUN·HIT·WALK·STEAL 등 이벤트 타입을 멀티 셀렉트로 지정할 수 있어야 한다(MUST). Live Activity·ongoing 스코어 노티는 설정 UI에 노출하지 않는다.

#### Scenario: 이벤트 선택 저장·반영
- **WHEN** 사용자가 설정 화면에서 이벤트 타입을 선택·해제하고 저장하면
- **THEN** 변경 사항이 백엔드에 저장되고 폰·페어링된 Apple Watch에 즉시 반영되어 다음 이벤트부터 적용된다

## MODIFIED Requirements

### Requirement: 푸시 알림
LIVE 전환 등 주요 이벤트를 푸시 알림으로 전달해야 한다(SHOULD). 단, 폰 앱이 foreground 상태이거나 페어링된 Apple Watch가 활성 상태인 경우 헤드업 노티는 suppress 한다.

#### Scenario: LIVE 전환 알림
- **GIVEN** 관심 팀 경기가 LIVE로 전환되었을 때
- **WHEN** 앱이 백그라운드 또는 종료 상태이고 페어링된 워치가 비활성 또는 미페어링이면
- **THEN** APNs 푸시 알림을 통해 사용자에게 알린다

#### Scenario: 폰 foreground 또는 워치 활성 시 헤드업 suppress
- **GIVEN** 폰 앱이 foreground 상태이거나 페어링된 워치가 활성 상태일 때
- **WHEN** 이벤트 푸시를 수신하면
- **THEN** 헤드업 노티 표시를 suppress 하고 Live Activity·인앱 UI 갱신으로 대체한다
