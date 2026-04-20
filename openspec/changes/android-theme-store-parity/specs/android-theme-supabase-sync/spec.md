## ADDED Requirements

### Requirement: 로그인 시 서버에서 테마/설정 복원
로그인 상태가 되면 Supabase에서 잠금 해제된 테마 ID 목록, 활성 테마, 응원팀을 가져와 로컬 상태에 반영해야 한다.

#### Scenario: 로그인 후 서버 데이터 복원
- **WHEN** 사용자가 카카오 로그인에 성공
- **THEN** 서버에 저장된 잠금 해제 테마 목록, 활성 테마, 응원팀이 로컬에 반영된다

### Requirement: 테마 잠금 해제 시 서버에 저장
로그인 상태에서 테마를 잠금 해제하면 `user_theme_purchases` 테이블에 구매 이력을 저장해야 한다.

#### Scenario: 광고 시청 후 서버에 구매 이력 저장
- **WHEN** 로그인 상태에서 리워드 광고를 통해 테마를 잠금 해제
- **THEN** 해당 테마 ID가 `user_theme_purchases` 테이블에 `status=PAID`로 저장된다

### Requirement: 테마 적용/팀 변경 시 서버에 동기화
로그인 상태에서 활성 테마 변경 또는 응원팀 변경 시 `user_settings` 테이블에 저장해야 한다.

#### Scenario: 테마 적용 시 서버 동기화
- **WHEN** 로그인 상태에서 테마를 적용
- **THEN** `user_settings.active_theme_id`가 업데이트된다

#### Scenario: 응원팀 변경 시 서버 동기화
- **WHEN** 로그인 상태에서 응원팀을 변경
- **THEN** `user_settings.selected_team`이 업데이트된다

### Requirement: 비로그인 사용자 로컬 저장
비로그인 상태에서도 테마 잠금 해제와 적용이 가능하며, 로컬 저장소에만 저장한다.

#### Scenario: 비로그인 상태에서 테마 잠금 해제
- **WHEN** 비로그인 상태에서 광고를 통해 테마를 잠금 해제
- **THEN** 로컬 저장소에 해제 이력이 저장되고 테마가 적용된다
