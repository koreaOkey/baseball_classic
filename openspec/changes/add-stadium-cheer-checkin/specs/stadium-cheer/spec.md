## ADDED Requirements

### Requirement: 9구장 정적 데이터 제공
시스템은 KBO 9개 정규 구장(잠실, 고척, 인천, 수원, 대전, 대구, 사직, 광주, 창원)의 좌표·반경·홈팀 코드·실내 여부를 포함한 정적 데이터(`stadiums.json`)를 클라이언트에 제공해야 한다(SHALL). 시즌 중 변경 시 원격에서 갱신 가능해야 한다.

#### Scenario: 클라이언트 시작 시 stadiums 로드
- **WHEN** 사용자가 앱을 실행한다
- **THEN** 클라이언트는 번들된 `stadiums.json` 또는 캐시된 원격 버전 중 더 최신을 사용한다
- **AND** 9개 구장 모두 좌표·반경·홈팀 코드를 보유한다

#### Scenario: 원격 갱신
- **WHEN** 백엔드 `stadiums.json`이 새 버전으로 갱신된다
- **THEN** 다음 클라이언트 실행 시 새 버전을 다운로드해 캐시한다
- **AND** 다운로드 실패 시 직전 캐시로 동작한다

### Requirement: 매일 응원 시그널 발행
백엔드는 KBO 일정 기반으로 매일 `cheer_signals.json`을 발행해야 한다(SHALL). 각 경기 entry는 홈팀과 원정팀 2개 signals를 포함하며, 각 signal은 `team_code`, `fire_at_iso`, `cheer_text`, `primary_color`, `haptic_pattern_id`를 가진다.

#### Scenario: 경기 시그널 발행
- **WHEN** 백엔드 일일 잡이 실행된다
- **THEN** 오늘과 내일 KBO 경기 각각에 대해 홈/원정 2개 signals 포함된 entry를 생성한다
- **AND** `fire_at_iso`는 KBO 발표 경기 시작 시각과 일치한다

#### Scenario: 더블헤더 처리
- **WHEN** 같은 구장에서 같은 날 두 경기가 열린다
- **THEN** 시그널 entry 2개를 별도로 발행한다
- **AND** 각 entry는 자체 `fire_at_iso`를 가진다

#### Scenario: 원정팬 응원 문구
- **WHEN** signals 배열에서 원정팀 signal을 조회한다
- **THEN** 해당 팀의 `cheer_text`는 홈경기일 때와 동일하다

### Requirement: 자동 체크인 알림
사용자가 KBO 9구장 반경 내에 진입하고, 본인 응원팀이 해당 구장에서 오늘 경기 중이며, 위치·알림 권한을 부여한 경우, 시스템은 OS 로컬 알림으로 자동 체크인 팝업을 노출해야 한다(SHALL).

#### Scenario: 잠실 진입 시 두산 팬 알림
- **WHEN** 두산 팬 사용자가 잠실야구장 반경 내에 진입한다
- **AND** 오늘 두산이 잠실에서 경기 중이다
- **THEN** "잠실 홈경기, 두산 팬들과 함께 응원해요" 류 로컬 알림이 발송된다

#### Scenario: 잠실 진입 시 LG 팬(원정) 알림
- **WHEN** LG 팬 사용자가 잠실야구장 반경 내에 진입한다
- **AND** 오늘 LG가 잠실 원정 중이다
- **THEN** "LG가 잠실 원정 중, 함께 응원해요" 류 로컬 알림이 발송된다

#### Scenario: 응원팀 무관 사용자 무시
- **WHEN** KIA 팬 사용자가 잠실야구장 반경 내에 진입한다
- **AND** 오늘 잠실에서 KIA가 출전하지 않는다
- **THEN** 알림은 발송되지 않는다

#### Scenario: 위치 권한 거부 사용자
- **WHEN** 사용자가 백그라운드 위치 권한을 거부한 상태이다
- **THEN** 자동 알림은 발송되지 않는다
- **AND** 사용자가 앱을 직접 열어 구장 반경 내일 경우 홈 화면에 체크인 카드가 노출된다

### Requirement: 응원 시각 자율 발화
응원 시각(`fire_at_iso`)에 사용자가 해당 구장 반경 내에 있고 응원팀이 출전 중이면, 클라이언트는 백엔드 트리거 없이 자율적으로 워치에 풀스크린 응원 발화를 송신해야 한다(SHALL). 폰 화면에는 변화가 없어야 한다(SHALL NOT).

#### Scenario: 정시 발화
- **WHEN** 응원 시각 도래
- **AND** 사용자가 구장 반경 내 + 응원팀 출전 중
- **THEN** 워치에 풀스크린 응원 페이로드 전달 (응원 문구 + 팀 컬러 + 햅틱 패턴)
- **AND** 폰 화면은 변화 없음

#### Scenario: 발화 차단 — 마스터 토글 OFF
- **WHEN** `live_haptic_enabled` 또는 `stadium_cheer_enabled`가 false
- **THEN** 발화하지 않는다

#### Scenario: 발화 차단 — 구장 외 위치
- **WHEN** 응원 시각 도래
- **AND** 사용자가 구장 반경 외부에 있다
- **THEN** 발화하지 않는다

### Requirement: 워치 풀스크린 응원 표시
워치는 응원 페이로드 수신 시 풀스크린 응원 문구·팀 컬러 배경·홈런 이벤트 수준의 강한 반복 햅틱 패턴을 5~7초간 표시해야 한다(SHALL).

#### Scenario: 풀스크린 표시
- **WHEN** 워치가 응원 페이로드를 수신한다
- **THEN** 팀 컬러 배경 + 응원 문구 대형 텍스트가 표시된다
- **AND** 홈런 이벤트처럼 강한 반복 햅틱이 동기 재생된다
- **AND** 5~7초 후 자동으로 dismiss된다

### Requirement: 워치 응원 화면 테스트
설정의 워치 테스트 화면은 실제 구장 진입이나 경기 시작 시각을 기다리지 않고 워치 풀스크린 응원 표시를 즉시 확인할 수 있는 테스트 동작을 제공해야 한다(SHALL).

#### Scenario: 응원 화면 테스트 전송
- **WHEN** 사용자가 설정의 워치 테스트 화면에서 응원 화면 테스트를 실행한다
- **THEN** 워치에 선택 응원팀 기준 응원 문구·팀 컬러·햅틱 페이로드가 즉시 전송된다
- **AND** 워치는 실제 응원 발화와 같은 풀스크린 응원 화면을 표시한다
- **AND** 홈런 이벤트 수준의 강한 반복 햅틱을 재생한다

### Requirement: 응원 테마 별도 영속화
응원 발화 풀스크린에 적용되는 테마는 워치 페이스 테마와 별개의 키(`active_cheer_theme_id`)로 영속화되어야 한다(SHALL). 한 쪽 변경이 다른 쪽에 영향을 주지 않아야 한다.

#### Scenario: 응원 테마만 변경
- **WHEN** 사용자가 상점 → 현장 응원 테마에서 새 테마를 적용한다
- **THEN** `active_cheer_theme_id`만 업데이트된다
- **AND** 워치 페이스 테마(`active_theme_id`)는 변경되지 않는다

#### Scenario: 워치 페이스 테마만 변경
- **WHEN** 사용자가 상점 → 베이직 테마에서 새 테마를 적용한다
- **THEN** `active_theme_id`만 업데이트된다
- **AND** `active_cheer_theme_id`는 변경되지 않는다

### Requirement: 마스터 토글 게이트
응원 발화는 기존 `live_haptic_enabled` 마스터 토글과 신규 `stadium_cheer_enabled` 서브토글 모두 활성 상태일 때만 동작해야 한다(SHALL).

#### Scenario: 사용자 옵트아웃
- **WHEN** 사용자가 설정에서 `stadium_cheer_enabled`를 OFF로 변경한다
- **THEN** 자동 체크인 알림과 응원 발화가 모두 차단된다
- **AND** 워치도 게이트 상태를 동기 받아 발화 무시
