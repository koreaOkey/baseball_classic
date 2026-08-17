# Lock Screen Live Score

## ADDED Requirements

### Requirement: Promoted style is the default on all supported devices

promoted 잠금화면 라이브 스코어 스타일의 기본값은 제조사와 무관하게 활성(ON)이어야 한다(SHALL). 삼성을 포함한 모든 API 36+ 기기에서 사용자가 설정을 조작하지 않은 경우 promoted 스타일이 선택되어야 한다(MUST). 승격 미지원 기기는 런타임 `canPostPromotedNotifications()` 확인을 통해 이전 리치 카드로 자동 폴백한다.

#### Scenario: 삼성 기기 미조작 사용자

- **WHEN** API 36+ 삼성 기기에서 사용자가 promoted 스타일 토글을 한 번도 조작하지 않은 상태로 라이브 스코어가 갱신될 때
- **THEN** 기본값이 promoted(ON)로 해석되어 promoted 경로로 진입한다
- **AND** 해당 기기가 승격을 지원하지 않으면 런타임에서 이전 리치 카드로 폴백한다(회귀 없음)

#### Scenario: 비삼성 기기 미조작 사용자

- **WHEN** API 36+ 비삼성 기기에서 사용자가 토글을 조작하지 않은 상태로 라이브 스코어가 갱신될 때
- **THEN** 기존과 동일하게 promoted 스타일이 노출된다

#### Scenario: 저장된 사용자 선택 유지

- **WHEN** 사용자가 설정에서 토글을 OFF로 저장한 뒤 앱을 재실행할 때
- **THEN** 저장된 값(OFF)이 유지되고 새 기본값(ON)으로 덮이지 않는다
