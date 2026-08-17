# Lock Screen Live Score

## ADDED Requirements

### Requirement: Guide user to enable Live Updates when promoted is blocked by system setting

기기가 승격을 지원하고(API36+) 사용자가 promoted를 켰지만 시스템 "실시간 업데이트" appop이 꺼져 `canPostPromotedNotifications()`가 false인 경우, 앱은 사용자에게 이를 켜도록 안내해야 한다(SHALL). 안내는 설정 딥링크(`ACTION_APP_NOTIFICATION_SETTINGS`)를 제공해야 한다(MUST). 앱이 시스템 appop을 강제로 변경해서는 안 된다(MUST NOT).

#### Scenario: 설정 배너 노출 및 해제

- **WHEN** 설정의 "잠금화면 라이브 스코어" 섹션에서 promoted가 켜졌고 실시간 업데이트가 꺼져 있을 때
- **THEN** 스위치 아래 안내 배너("잠금화면 고정이 꺼져 있어요" + "켜기")가 노출된다
- **AND** 배너를 탭하면 앱 알림 설정으로 딥링크된다
- **AND** 사용자가 실시간 업데이트를 켜고 화면으로 돌아오면(ON_RESUME) 배너가 사라진다

#### Scenario: 라이브 진입 1회성 프롬프트

- **WHEN** blocked 상태에서 처음 라이브 화면에 진입할 때
- **THEN** 안내 다이얼로그("설정 열기"/"나중에")가 1회 노출된다
- **AND** 어느 버튼이든 누르면 dismiss가 기록되어 이후 재진입에서 다시 뜨지 않는다

#### Scenario: 승격 허용 상태에서는 안내 미노출

- **WHEN** `canPostPromotedNotifications()`가 true이거나 사용자가 promoted를 끈 경우
- **THEN** 설정 배너와 라이브 진입 프롬프트 모두 노출되지 않는다
