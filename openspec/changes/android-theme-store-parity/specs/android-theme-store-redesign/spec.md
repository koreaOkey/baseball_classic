## ADDED Requirements

### Requirement: Watch/Phone 탭 구성
테마 상점은 "Watch Themes"와 "Phone App Themes" 두 탭으로 구성되며, Phone 탭은 "준비 중" 상태를 표시해야 한다.

#### Scenario: Watch 탭에서 테마 목록 표시
- **WHEN** 사용자가 테마 상점의 Watch Themes 탭을 선택
- **THEN** Free + Ad-Reward 카테고리 테마 12개가 2열 그리드로 표시된다

#### Scenario: Phone 탭에서 준비 중 표시
- **WHEN** 사용자가 Phone App Themes 탭을 선택
- **THEN** "준비 중" 안내 메시지가 표시된다

### Requirement: 원형 워치 스크린샷 프리뷰
각 테마 카드에는 실제 워치 화면 스크린샷이 원형으로 표시되어야 한다.

#### Scenario: 테마 카드 프리뷰 표시
- **WHEN** 테마 카드가 렌더링될 때
- **THEN** 해당 테마의 워치 스크린샷이 120dp 원형으로 표시된다

### Requirement: 테마 상태별 버튼 표시
테마의 잠금/해제/적용 상태에 따라 적절한 액션 버튼을 표시하며, 모든 상태에서 카드 크기가 동일해야 한다.

#### Scenario: 잠긴 테마
- **WHEN** Ad-Reward 테마가 잠긴 상태
- **THEN** "광고 보고 받기" 버튼과 잠금 아이콘이 표시된다

#### Scenario: 해제된 테마
- **WHEN** 테마가 잠금 해제되었으나 미적용 상태
- **THEN** "적용하기" 버튼이 표시된다

#### Scenario: 적용 중인 테마
- **WHEN** 테마가 현재 적용 중
- **THEN** "적용 중" 표시와 체크 배지가 표시된다

### Requirement: Watch 테마와 Phone 테마 분리
스토어 테마 적용은 워치 UI에만 영향을 미치며, 폰 앱 UI는 항상 응원팀 색상을 유지해야 한다.

#### Scenario: 스토어 테마 적용 후 폰 UI 색상
- **WHEN** 사용자가 스토어 테마를 적용
- **THEN** 폰 앱의 홈/라이브 게임 화면 색상은 응원팀 색상을 유지한다

### Requirement: 상점 탭 활성화
하단 네비게이션 바에 상점 탭이 표시되어야 한다.

#### Scenario: 상점 탭 접근
- **WHEN** 사용자가 하단 네비게이션 바의 상점 아이콘을 탭
- **THEN** 테마 상점 화면이 표시된다
