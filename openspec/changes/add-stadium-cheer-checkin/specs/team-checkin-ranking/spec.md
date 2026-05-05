## ADDED Requirements

### Requirement: 체크인 이벤트 기록
사용자가 자동 체크인 알림 또는 앱 내 체크인 카드의 [확인] 버튼을 탭하면, 클라이언트는 백엔드에 체크인 이벤트를 1건 전송하고 백엔드는 이를 raw 형태로 기록해야 한다(SHALL). 좌표는 검증·디버깅 목적으로 90일간 보관 후 익명화한다.

#### Scenario: 체크인 성공
- **WHEN** 사용자가 잠실 반경 내에서 [확인] 버튼을 탭한다
- **THEN** 클라이언트는 인증 토큰과 함께 `team_code`, `stadium_code`, 좌표, 정확도, mock_location 플래그, 앱 버전을 백엔드에 POST한다
- **AND** 백엔드는 이벤트를 `pending` 상태로 저장한다

#### Scenario: 90일 익명화
- **WHEN** 체크인 이벤트가 90일 경과한다
- **THEN** 좌표 컬럼이 NULL로 익명화된다
- **AND** 집계 카운트는 영향받지 않는다

### Requirement: 체크인 검증 워커
백엔드는 비동기 워커로 체크인 이벤트를 사후 검증하여 `valid` / `invalid` / `suspicious` 상태로 판정해야 한다(SHALL).

#### Scenario: 좌표 재검증 통과
- **WHEN** 워커가 pending 이벤트를 처리한다
- **AND** 좌표가 등록된 stadium 반경 내이고 mock_location이 false이다
- **AND** 응원 시각 -60분 ~ +30분 윈도우 내 클릭이다
- **THEN** 상태를 `valid`로 변경한다

#### Scenario: mock_location 차단
- **WHEN** 이벤트의 mock_location 플래그가 true이다
- **THEN** 상태를 `invalid`로 변경하고 `invalidity_reason`에 사유를 기록한다

#### Scenario: 윈도우 외 클릭
- **WHEN** 클릭 시각이 응원 시각 -60분 ~ +30분 외이다
- **THEN** 상태를 `invalid`로 변경한다

#### Scenario: 의심 패턴
- **WHEN** 동일 디바이스가 1시간 내 2개 이상 다른 구장에서 체크인한다
- **THEN** 상태를 `suspicious`로 변경한다

### Requirement: 1일 1체크인 제약
시스템은 동일 사용자에 대해 같은 날(KST 기준)에 1건의 valid 체크인만 카운트해야 한다(SHALL).

#### Scenario: 중복 체크인
- **WHEN** 사용자가 같은 날 두 번째 체크인을 시도한다
- **THEN** 백엔드는 이벤트를 저장하되 두 번째 이벤트는 valid로 승격되지 않는다

### Requirement: 팀별 체크인 집계 캐시
시스템은 팀별 일/주/시즌 단위 valid 체크인 누적 카운트를 집계 캐시 테이블에 보관해야 한다(SHALL). 캐시는 워커에 의해 갱신된다. 집계는 **iOS와 Android 사용자 이벤트를 합산한 단일 카운트**여야 한다(SHALL). raw 이벤트 테이블에는 platform 컬럼이 보존되어 사후 분리 분석이 가능해야 한다.

#### Scenario: 일간 집계
- **WHEN** 워커가 valid 이벤트를 처리한다
- **THEN** 해당 `(team_code, date)` 카운트를 +1 증가시킨다
- **AND** 이벤트의 platform(`ios`/`android`)에 무관하게 동일한 카운트로 집계된다

#### Scenario: 시즌 집계
- **WHEN** 일간 집계가 갱신된다
- **THEN** 해당 팀의 시즌 누적 카운트도 +1 증가시킨다
- **AND** iOS·Android 이벤트는 단일 카운트로 합산된다

### Requirement: 랭킹 UI 합산 명시
랭킹 화면은 표시되는 카운트가 iOS·Android 합산임을 사용자에게 명시해야 한다(SHALL).

#### Scenario: 합산 라벨 노출
- **WHEN** 사용자가 응원 랭킹 화면을 본다
- **THEN** "iOS · Android 합산 집계" 류 라벨이 주간/시즌 토글 근처에 노출된다

### Requirement: 팀별 응원 랭킹 조회
클라이언트는 백엔드로부터 팀별 누적 체크인 랭킹을 시즌·주간 단위로 조회할 수 있어야 한다(SHALL).

#### Scenario: 시즌 랭킹 조회
- **WHEN** 클라이언트가 시즌 랭킹을 요청한다
- **THEN** 백엔드는 KBO 10개 팀의 시즌 누적 valid 카운트와 순위를 반환한다

#### Scenario: 주간 랭킹 조회
- **WHEN** 클라이언트가 주간(직전 7일) 랭킹을 요청한다
- **THEN** 백엔드는 KBO 10개 팀의 주간 valid 카운트와 순위를 반환한다

### Requirement: 본인 체크인 이력 조회
시스템은 사용자가 본인의 valid 체크인 이력을 기간 필터로 조회할 수 있는 엔드포인트를 제공해야 한다(SHALL). 응답에는 날짜·구장·팀·경기 식별자가 포함되어 달력 UI / 개인 통계 화면이 클라이언트 단에서 렌더링 가능해야 한다.

#### Scenario: 시즌 단위 본인 이력 조회
- **WHEN** 클라이언트가 인증된 사용자로 시즌 시작 ~ 현재 범위를 요청한다
- **THEN** valid 체크인 row 리스트가 client_ts 내림차순으로 반환된다
- **AND** 각 row는 client_ts, stadium_code, team_code, game_id, is_home_team, opponent_team_code를 포함한다

#### Scenario: 90일 후 좌표 익명화 후에도 조회 가능
- **WHEN** 90일 경과한 체크인 row의 좌표가 NULL로 익명화된 상태에서 본인 이력을 조회한다
- **THEN** 좌표는 NULL이지만 stadium_code·team_code·game_id 등 식별자는 보존되어 달력 UI 표시가 정상이다

### Requirement: 시즌 1위 팀 디지털 뱃지 시상
시즌 종료 시점에 valid 체크인 누적 1위 팀의 응원팀 사용자에게 디지털 뱃지가 자동 지급되어야 한다(SHALL).

#### Scenario: 시즌 종료 시상
- **WHEN** KBO 시즌이 종료된다
- **AND** 1위 팀이 확정된다
- **THEN** 해당 팀을 응원팀으로 설정한 사용자 전체에 디지털 뱃지가 지급된다
- **AND** 시상 후 부정 사용자가 발견되면 뱃지 회수 가능
