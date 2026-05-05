## ADDED Requirements

### Requirement: Apple Watch 재연결 초기 이벤트 억제
iOS 모바일은 Apple Watch 동기화 스트림을 시작하거나 재연결할 때 수신한 과거 이벤트 스냅샷을 신규 워치 알림으로 전송하지 않아야 한다(MUST).

#### Scenario: 스트림 연결 직후 최근 이벤트 수신
- **GIVEN** 사용자가 이미 진행 중인 경기를 Apple Watch와 동기화한 상태
- **WHEN** iOS 모바일이 경기 스트림에 연결되어 최근 이벤트 목록을 수신하면
- **THEN** iOS 모바일은 해당 이벤트 목록을 기준 커서로만 반영하고 Apple Watch 햅틱 또는 영상 알림으로 전송하지 않는다

#### Scenario: 스트림 연결 이후 신규 이벤트 수신
- **GIVEN** iOS 모바일이 초기 이벤트 목록을 기준 커서로 반영한 상태
- **WHEN** 이후 신규 경기 이벤트가 수신되면
- **THEN** iOS 모바일은 사용자 설정에 따라 Apple Watch 햅틱 또는 영상 알림으로 전송한다
