## ADDED Requirements

### Requirement: 폰 라이브 스코어 ongoing 노티
Android 폰은 라이브 경기 동안 잠금화면·알림 드로어에서 확인 가능한 라이브 스코어 ongoing notification을 게시해야 한다(MUST). 이 기능은 경기 카드의 "잠금화면" 토글로 제어되어야 한다(MUST). 동일 식별자를 사용해 스코어·이닝·BSO·최근 이벤트 1개를 in-place 갱신한다.

#### Scenario: LIVE 동안 노티 노출
- **GIVEN** 사용자 관심 경기가 LIVE 상태이고 경기 카드의 "잠금화면"이 켜져 있을 때
- **WHEN** 폰이 백그라운드 또는 잠금 상태이면
- **THEN** 라이브 스코어 ongoing notification이 잠금화면·알림 드로어에 노출되고 잠금 가시성(공개/비공개)은 사용자 OS 설정을 따른다

#### Scenario: 잠금화면 보기 OFF
- **GIVEN** 라이브 스코어 ongoing notification이 게시된 상태에서
- **WHEN** 사용자가 경기 카드에서 "잠금화면"을 끄면
- **THEN** 기존 ongoing notification이 즉시 제거되고 이후 같은 경기의 상태 갱신으로 다시 게시되지 않는다

#### Scenario: 이벤트 시 in-place 갱신
- **WHEN** 새 이벤트가 발생하면
- **THEN** 동일 식별자의 노티가 새 스코어·최근 이벤트 라인으로 in-place 교체되며 별도 노티가 누적되지 않는다

### Requirement: 워치 우선 햅틱 suppress
워치(Wear OS) 노드가 페어링·연결된 상태에서는 폰 햅틱·헤드업 노티를 발화하지 않아야 한다(MUST). 단, 경기 카드의 "잠금화면"이 켜져 있으면 라이브 스코어 ongoing notification 갱신은 정상 동작한다.

#### Scenario: 워치 연결 시 폰 햅틱 안 함
- **GIVEN** 폰이 페어링된 워치 노드와 연결된 상태일 때
- **WHEN** 선택 이벤트가 발생하면
- **THEN** 폰은 햅틱·헤드업 노티를 발화하지 않고 라이브 스코어 ongoing notification만 갱신된다

### Requirement: 사용자 이벤트 선택 UI
오늘의 경기 카드 하단에 "잠금화면" 토글과 "Watch" 토글을 노출해야 한다(MUST). 설정 화면에는 "알림 이벤트" 섹션을 노출하고 Watch/잠금화면 채널별 이벤트 선택을 제공해야 한다(MUST). "잠금화면"은 Android live_score ongoing notification 표시 여부를 제어하고, "Watch"는 워치 동기화 여부를 제어한다. 이벤트 선택은 SCORE·HOMERUN·HIT·WALK·STEAL·OUT·BALL·STRIKE·PITCHER_CHANGE 등 강한 이벤트 알림 타입을 채널별로 지정한다.

#### Scenario: 오늘의 경기 카드에서 토글 제어
- **GIVEN** 오늘의 경기 카드가 표시된 상태에서
- **WHEN** 사용자가 카드 하단의 "잠금화면" 또는 "Watch" 토글을 누르면
- **THEN** 해당 경기의 live_score ongoing notification 또는 워치 동기화 상태가 즉시 ON/OFF로 전환되고 카드 하단 토글 상태에 반영된다

#### Scenario: 잠금화면 토글 ON 광고 게이트
- **GIVEN** 사용자가 해당 경기의 live_score ongoing notification 광고를 아직 확인하지 않은 상태에서
- **WHEN** 오늘의 경기 카드 하단의 "잠금화면" 토글을 ON으로 전환하면
- **THEN** "잠금화면에서 보시겠습니까? / 광고 관람 후 경기 확인 가능합니다." AlertDialog 를 먼저 표시한다
- **AND** 사용자가 [확인] 을 탭하면 Android 잠금화면 경기 카드용 보상형 광고를 표시하고, 광고 확인 후 해당 경기의 live_score ongoing notification을 시작한다

#### Scenario: 잠금화면 광고 생략 — 같은 경기 재시청
- **GIVEN** 사용자가 해당 경기의 live_score ongoing notification 광고를 이미 확인한 상태에서
- **WHEN** 오늘의 경기 카드 하단의 "잠금화면" 토글을 ON으로 전환하고 확인 팝업에서 [확인] 을 탭하면
- **THEN** 광고는 재생되지 않고 즉시 해당 경기의 live_score ongoing notification을 시작한다

#### Scenario: 경기 시작 전 잠금화면 토글 차단
- **GIVEN** 경기 상태가 LIVE가 아닐 때
- **WHEN** 사용자가 오늘의 경기 카드 하단의 "잠금화면" 토글을 ON으로 전환하려 하면
- **THEN** live_score ongoing notification을 시작하지 않고 "경기 시작 전입니다." AlertDialog 를 표시한다

#### Scenario: 이벤트 선택 저장·반영
- **WHEN** 사용자가 설정 화면에서 Watch 또는 잠금화면 이벤트 타입을 선택·해제하고 저장하면
- **THEN** 변경 사항이 백엔드에 저장되고 폰·페어링된 워치에 즉시 반영되어 다음 이벤트부터 해당 채널에 적용된다
