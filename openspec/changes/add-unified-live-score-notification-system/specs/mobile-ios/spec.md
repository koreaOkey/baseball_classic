## ADDED Requirements

### Requirement: 워치 우선 햅틱 suppress
iOS 폰은 페어링된 Apple Watch가 활성·도달 가능 상태일 때 햅틱·헤드업 노티를 발화하지 않아야 한다(MUST). 단, 경기 카드의 "잠금화면 보기"가 켜져 있으면 Live Activity 갱신은 정상 동작한다.

#### Scenario: 워치 활성 시 폰 햅틱 suppress
- **GIVEN** 폰이 페어링된 Apple Watch와 연결되어 활성 상태일 때
- **WHEN** 선택 이벤트가 발생하면
- **THEN** 폰은 햅틱·헤드업 노티를 발화하지 않고 "잠금화면 보기"가 켜진 경우 Live Activity만 갱신된다

#### Scenario: 워치 비활성 시 폰 발화
- **GIVEN** 폰이 워치와 페어링되지 않았거나 워치가 비활성 상태일 때
- **WHEN** 이벤트가 발생하면
- **THEN** 폰은 헤드업 노티와 햅틱을 정상 발화한다

### Requirement: 사용자 이벤트 선택 UI
오늘의 경기 카드 하단에 "잠금화면" 토글과 "Watch" 토글을 노출해야 한다(MUST). 설정 화면에는 "알림 이벤트" 섹션을 노출하고 Watch/잠금화면 채널별 이벤트 선택을 제공해야 한다(MUST). "잠금화면"은 Live Activity 표시 여부를 제어하고, "Watch"는 워치 동기화 여부를 제어한다. 이벤트 선택은 SCORE·HOMERUN·HIT·WALK·STEAL·OUT·BALL·STRIKE·PITCHER_CHANGE 등 강한 이벤트 알림 타입을 채널별로 지정한다.

#### Scenario: 오늘의 경기 카드에서 토글 제어
- **GIVEN** 오늘의 경기 카드가 표시된 상태에서
- **WHEN** 사용자가 카드 하단의 "잠금화면" 또는 "Watch" 토글을 누르면
- **THEN** 해당 경기의 Live Activity 또는 워치 동기화 상태가 즉시 ON/OFF로 전환되고 카드 하단 토글 상태에 반영된다

#### Scenario: 잠금화면 보기 OFF
- **GIVEN** Live Activity가 표시된 상태에서
- **WHEN** 사용자가 경기 카드에서 "잠금화면"을 끄면
- **THEN** 기존 Live Activity가 즉시 종료되고 이후 같은 경기의 상태 갱신으로 다시 시작되지 않는다

#### Scenario: 이벤트 선택 저장·반영
- **WHEN** 사용자가 설정 화면에서 Watch 또는 잠금화면 이벤트 타입을 선택·해제하고 저장하면
- **THEN** 변경 사항이 백엔드에 저장되고 폰·페어링된 Apple Watch에 즉시 반영되어 다음 이벤트부터 해당 채널에 적용된다

## MODIFIED Requirements

### Requirement: 푸시 알림
LIVE 전환 등 주요 이벤트를 푸시 알림으로 전달해야 한다(MUST). 단, 폰 앱이 foreground 상태이거나 페어링된 Apple Watch가 활성 상태인 경우 헤드업 노티는 suppress 한다.

#### Scenario: LIVE 전환 알림
- **GIVEN** 관심 팀 경기가 LIVE로 전환되었을 때
- **WHEN** 앱이 백그라운드 또는 종료 상태이고 페어링된 워치가 비활성 또는 미페어링이면
- **THEN** APNs 푸시 알림을 통해 사용자에게 알린다

#### Scenario: 폰 foreground 또는 워치 활성 시 헤드업 suppress
- **GIVEN** 폰 앱이 foreground 상태이거나 페어링된 워치가 활성 상태일 때
- **WHEN** 이벤트 푸시를 수신하면
- **THEN** 헤드업 노티 표시를 suppress 하고 Live Activity·인앱 UI 갱신으로 대체한다
