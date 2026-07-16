## ADDED Requirements

### Requirement: Extended Runtime Session 연장 상한
watchOS 앱은 라이브 경기 중 WKExtendedRuntimeSession을 무제한 재시작하지 않아야 한다(SHALL NOT). 백그라운드 이벤트 햅틱은 APNs 직접 수신을 1차 경로로 사용한다.

#### Scenario: 세션 만료 시 무조건 재시작하지 않음
- **GIVEN** 라이브 경기 중 Extended Session이 만료에 도달했을 때
- **WHEN** `extendedRuntimeSessionWillExpire`가 호출되면
- **THEN** 워치는 재시작 정책(제거 또는 누적 상한/최근 사용 조건)에 따라서만 새 세션을 시작한다
- **AND** 재시도 카운트를 리셋해 상한을 우회하지 않는다

#### Scenario: 세션 없이도 백그라운드 이벤트 햅틱 유지
- **GIVEN** Extended Session이 없는 백그라운드 상태에서
- **WHEN** 라이브 이벤트 APNs push가 도착하면
- **THEN** 워치는 해당 이벤트의 햅틱을 재생한다
