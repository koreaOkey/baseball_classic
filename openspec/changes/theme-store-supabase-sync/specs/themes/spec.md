## ADDED Requirements

### Requirement: 야구가 좋아 워치 테마
"야구가 좋아" 테마를 워치 배경 이미지 테마로 제공한다. 카테고리는 베이직(광고 시청).

#### Scenario: 테마 상점에서 표시
- **WHEN** 유저가 테마 상점을 연다
- **THEN** "베이직 테마" 섹션에 "야구가 좋아" 테마가 기본형 바로 다음에 표시된다

### Requirement: themes 테이블 platform 구분
themes 테이블에 platform 컬럼(watch/phone/both)을 추가하여 워치/폰 테마를 구분한다.

#### Scenario: 워치 전용 테마 조회
- **WHEN** platform = 'watch'인 테마를 조회한다
- **THEN** 현재 등록된 베이직 12개 테마가 반환된다

## MODIFIED Requirements

### Requirement: 테마 상점 섹션 구조
테마 상점은 "베이직 테마"와 "프리미엄" 두 섹션으로 구성한다. 기존 "기본"과 "무료 (광고 시청)" 섹션을 "베이직 테마"로 통합하고, 프리미엄 섹션은 숨김 처리한다.

#### Scenario: 테마 상점 진입
- **WHEN** 유저가 테마 상점을 연다
- **THEN** "베이직 테마" 섹션만 표시되고, 프리미엄 섹션은 숨겨진다

### Requirement: 테마 적용 범위
테마 적용 시 워치에만 반영하고 폰 앱 색상은 변경하지 않는다.

#### Scenario: 테마 적용
- **WHEN** 유저가 테마를 적용한다
- **THEN** 워치에만 테마가 동기화되고 폰 앱의 HomeScreen/LiveGameScreen 색상은 팀 테마를 유지한다

## REMOVED Requirements

### Requirement: 레거시 팀별 테마 DB 레코드
**Reason**: theme_doosan_base_v1 등 10개 팀별 테마 레코드가 앱에서 사용되지 않음
**Migration**: themes 테이블에서 해당 10개 행 삭제 완료
