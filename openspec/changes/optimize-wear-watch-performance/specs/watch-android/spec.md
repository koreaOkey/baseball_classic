## ADDED Requirements

### Requirement: 포그라운드 서비스 수명 관리
라이브 관전 FGS는 이미 실행 중이면 재시작 요청을 무시해야 하고(SHALL), 일정 시간 경기 데이터가 없으면 스스로 종료해야 한다(SHALL).

#### Scenario: 실행 중 재시작 요청 무시
- **GIVEN** 라이브 FGS가 실행 중일 때
- **WHEN** 새 game_data 수신으로 시작 요청이 다시 발생하면
- **THEN** 서비스는 재시작·알림 재게시 없이 그대로 유지된다

#### Scenario: 데이터 두절 시 자동 종료와 복귀
- **GIVEN** 마지막 경기 데이터 수신 후 30분이 경과했을 때
- **WHEN** 워치독이 이를 감지하면
- **THEN** FGS는 스스로 종료한다
- **AND** 이후 새 game_data가 도착하면 FGS가 다시 시작된다

### Requirement: ongoing 알림 변경 시에만 재게시
ongoing 라이브 스코어 알림은 OngoingActivity 메타데이터가 포함된 알림을 게시해야 하며(SHALL), 표시 내용이 변경된 경우에만 재게시해야 한다(SHALL).

#### Scenario: 내용이 같은 업데이트
- **GIVEN** ongoing 알림이 게시되어 있을 때
- **WHEN** 표시 내용(제목·본문)이 동일한 갱신 요청이 오면
- **THEN** 알림을 다시 게시하지 않는다

#### Scenario: 워치페이스 칩 표시
- **GIVEN** 라이브 경기 관전 중일 때
- **WHEN** ongoing 알림이 게시되면
- **THEN** OngoingActivity 메타데이터가 포함되어 워치페이스에 칩이 표시된다

### Requirement: 앰비언트 중 폴링 완화
워치 독립 폴링은 앰비언트 모드에서 간격을 완화해야 하고(SHALL), 앰비언트 해제 시 즉시 1회 폴링으로 재개해야 한다(SHALL). 대상 경기를 특정한 후에는 단일 경기 조회를 사용한다.

#### Scenario: 손목 내림 중 폴링 간격 완화
- **GIVEN** 경기 시작 전 폴링이 동작 중일 때
- **WHEN** 앰비언트 모드로 진입하면
- **THEN** 폴링 간격이 3분으로 완화된다

#### Scenario: 손목 올림 시 즉시 재개
- **GIVEN** 앰비언트 상태로 폴링이 완화되어 있을 때
- **WHEN** 앰비언트가 해제되면
- **THEN** 즉시 1회 폴링 후 30초 간격으로 복귀한다
