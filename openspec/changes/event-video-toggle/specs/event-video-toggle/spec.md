## ADDED Requirements

### Requirement: 모바일 설정에 이벤트 영상 알림 토글 제공
모바일 앱(iOS, Android) 설정 화면 "알림" 섹션에 사용자가 워치 이벤트 영상 재생을 켜고 끌 수 있는 토글이 제공되어야 한다(SHALL). 기본값은 ON이다.

#### Scenario: 토글 기본값
- **WHEN** 사용자가 처음 앱을 설치하고 설정 화면을 열면
- **THEN** "이벤트 영상 알림" 토글이 ON 상태로 표시된다

#### Scenario: 토글 상태 영속 저장
- **WHEN** 사용자가 토글을 OFF로 변경하면
- **THEN** 모바일 앱 로컬 저장소(iOS UserDefaults, Android SharedPreferences "basehaptic_user_prefs")의 키 `event_video_enabled`에 false가 저장된다
- **AND** 앱을 재실행해도 OFF 상태가 유지된다

### Requirement: 모바일에서 워치로 토글 값 즉시 동기화
사용자가 모바일에서 토글을 변경하면 즉시 워치로 새 값이 전달되어야 한다(SHALL).

#### Scenario: 토글 변경 시 워치 전달
- **WHEN** 사용자가 모바일에서 토글을 변경하면
- **THEN** iOS는 `WCSession.updateApplicationContext`로, Android는 Wearable `DataClient`의 `/settings/current` 경로로 새 값을 push한다
- **AND** 워치는 자체 저장소(iOS UserDefaults, Android SharedPreferences "watch_user_prefs")에 값을 저장한다

#### Scenario: 앱 진입 시 초기 동기화
- **WHEN** 모바일 앱이 진입(`onAppear` / `LaunchedEffect`)하면
- **THEN** 현재 저장된 토글 값을 워치로 한 번 push한다

### Requirement: 워치는 토글 OFF 시 이벤트 영상 재생 차단
워치는 수신한 토글 값이 false이면 video event(VICTORY, HOMERUN, HIT, DOUBLE_PLAY, SCORE) 영상 재생을 차단해야 한다(SHALL).

#### Scenario: 토글 OFF 상태에서 video event 수신
- **WHEN** 토글이 OFF인 상태에서 워치가 video event(예: HOMERUN)를 수신하면
- **THEN** 영상 transition이 시작되지 않는다 (video token 발급 차단 / showTransition early return)

#### Scenario: 토글 OFF 상태에서 햅틱 동작
- **WHEN** 토글이 OFF인 상태에서 워치가 video event를 수신하면
- **THEN** 햅틱 진동은 정상적으로 동작한다 (햅틱 경로는 토글의 영향을 받지 않는다)

#### Scenario: 토글 OFF 상태에서 경기 종료 자동 victory
- **WHEN** 토글이 OFF인 상태에서 내 팀이 승리해 경기가 종료되면
- **THEN** 자동 victory 영상이 재생되지 않는다

### Requirement: 토글이 햅틱 및 점수 표시에 영향 주지 않음
이벤트 영상 토글은 영상 재생만 제어하며, 햅틱/이벤트 오버레이/점수 동기화 등 다른 워치 기능에 영향을 주지 않아야 한다(SHALL).

#### Scenario: 비-video 이벤트는 영향 없음
- **WHEN** 토글이 OFF인 상태에서 워치가 STRIKE, BALL, OUT, WALK, STEAL 등 영상이 없는 이벤트를 수신하면
- **THEN** 햅틱 및 이벤트 오버레이는 평소처럼 동작한다
