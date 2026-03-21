# Themes Specification

## Purpose
KBO 10개 구단별 테마 시스템 및 테마 스토어를 통한 수익화 기능.

## Requirements

### Requirement: 팀별 컬러 프리셋
시스템은 KBO 10개 구단의 고유 컬러 프리셋을 제공해야 한다(MUST).

#### Scenario: 팀 테마 적용
- GIVEN 사용자가 관심 팀을 선택했을 때
- WHEN 앱 테마가 적용되면
- THEN 해당 팀의 primary, primaryDark, secondary, accent, gradient 색상이 적용된다

#### Scenario: 지원 구단 목록
- GIVEN 테마 시스템이 초기화될 때
- WHEN 구단 목록을 로드하면
- THEN 두산, LG, 키움, 삼성, 롯데, SSG, KT, 한화, KIA, NC 10개 구단을 지원한다

### Requirement: 테마 스토어
사용자는 프리미엄 팀 테마 패키지를 포인트로 구매할 수 있어야 한다(MUST).

#### Scenario: 테마 구매
- GIVEN 사용자가 테마 스토어에서 테마를 선택했을 때
- WHEN 포인트 잔액이 충분하면
- THEN 구매를 완료하고 user_theme_purchases에 기록한다

#### Scenario: 테마 활성화
- GIVEN 구매한 테마가 있을 때
- WHEN 사용자가 테마를 활성화하면
- THEN user_theme_settings에 저장하고 즉시 앱에 반영한다

### Requirement: 햅틱 패턴 정의
각 이벤트 타입은 고유한 햅틱 패턴을 가져야 한다(MUST).

#### Scenario: 이벤트별 햅틱 패턴
- GIVEN 경기 이벤트가 발생했을 때
- WHEN 햅틱 패턴이 정의되어 있으면
- THEN 이벤트 타입(BALL, STRIKE, OUT, HOMERUN, SCORE 등)에 맞는 진동 패턴을 실행한다

### Requirement: 홈런 애니메이션
홈런 이벤트 발생 시 워치에서 특별 애니메이션을 재생해야 한다(MUST).

#### Scenario: 홈런 영상 재생
- GIVEN 홈런 이벤트가 감지되었을 때
- WHEN 워치 라이브 경기 화면에서 이벤트를 수신하면
- THEN 최적화된 홈런 애니메이션(450x450, 20fps)을 4초간 재생한다

### Requirement: 테마 데이터 모델
테마는 구단별 버전 관리와 가격 책정을 지원해야 한다(MUST).

#### Scenario: 테마 버전 관리
- GIVEN 동일 구단에 여러 버전의 테마가 있을 때
- WHEN 테마를 조회하면
- THEN (team_code, version) 조합으로 유니크하게 관리된다
