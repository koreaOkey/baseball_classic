## ADDED Requirements

### Requirement: 테마 잠금해제 이력 서버 저장
테마 잠금해제 시 user_theme_purchases 테이블에 이력을 저장하여 계정 기반으로 관리한다.

#### Scenario: 광고 시청 후 테마 잠금해제
- **WHEN** 유저가 광고를 시청하고 테마를 잠금해제한다
- **THEN** UserDefaults에 즉시 반영하고, Supabase user_theme_purchases에 해당 theme_id로 insert한다

#### Scenario: DB 저장 실패 시 로컬 유지
- **WHEN** 네트워크 오류로 Supabase insert가 실패한다
- **THEN** UserDefaults에는 이미 반영되어 있으므로 UX에 영향 없이 로컬 상태를 유지한다

### Requirement: 적용 테마 서버 저장
테마 적용 시 user_theme_settings 테이블에 현재 적용 중인 테마를 upsert한다.

#### Scenario: 테마 적용
- **WHEN** 유저가 테마를 적용한다
- **THEN** 워치에 동기화하고, Supabase user_theme_settings에 active_theme_id를 upsert한다

### Requirement: 로그인 시 서버 복원
로그인 시 서버에서 잠금해제 목록과 적용 테마를 가져와 로컬 상태를 복원한다.

#### Scenario: 다른 기기에서 로그인
- **WHEN** 유저가 새 기기에서 로그인한다
- **THEN** user_theme_purchases에서 PAID 상태인 theme_id를 fetch하여 로컬 unlockedThemeIds와 union한다
- **THEN** user_theme_settings에서 active_theme_id를 fetch하여 워치에 동기화한다

#### Scenario: 서버 복원 실패
- **WHEN** 로그인 시 서버 fetch가 실패한다
- **THEN** 로컬 UserDefaults 상태를 그대로 사용한다

### Requirement: RLS 기반 데이터 격리
각 유저는 본인의 테마 데이터만 읽고 쓸 수 있어야 한다.

#### Scenario: 타인 데이터 접근 차단
- **WHEN** 유저가 다른 user_id의 테마 구매 이력을 조회한다
- **THEN** RLS 정책에 의해 빈 결과를 반환한다
