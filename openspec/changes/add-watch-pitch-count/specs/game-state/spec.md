# Game State Specification (delta)

## MODIFIED Requirements

### Requirement: 경기 상태 모델
시스템은 경기의 전체 상태를 하나의 스냅샷으로 표현해야 한다(MUST).

상태 응답에는 워치 라이브 화면이 별도 호출 없이 그릴 수 있도록 활성 투수의 누적 투구수(`pitcherPitchCount`)를 포함한다.

규칙:
- LIVE 상태이고 `game.pitcher` 이름과 일치하는 `GamePitcherStat` 행이 있을 때만 해당 행의 `pitchesThrown` 을 정수로 노출한다. `appearance_order desc nulls last` 로 가장 최근 등판을 활성 투수로 본다(동명이인/멀티 등판 가드).
- FINISHED / CANCELED / POSTPONED / SCHEDULED 상태이거나, 매칭되는 `GamePitcherStat` 행이 없으면 `null` 을 반환한다.
- 클라이언트는 이 값이 `null` 이거나 0 미만이면 투구수 영역을 표시하지 않는다.

#### Scenario: 상태 조회
- GIVEN 진행 중인 경기가 있을 때
- WHEN GET /games/{gameId}/state 를 호출하면
- THEN score, inning, inningHalf, ball, strike, out, bases, currentBatter, currentPitcher, pitcherPitchCount 를 포함한 상태를 반환한다

#### Scenario: 활성 투수의 투구수 노출
- GIVEN LIVE 경기에서 `game.pitcher = "Kim Starter"` 이고 `GamePitcherStat` 에 동일 이름·`pitchesThrown=88` 행이 있을 때
- WHEN GET /games/{gameId}/state 를 호출하면
- THEN 응답의 `pitcherPitchCount = 88` 으로 반환된다

#### Scenario: 투수 정보 부재 시 null
- GIVEN LIVE 경기에서 `game.pitcher` 가 없거나 매칭되는 `GamePitcherStat` 행이 없을 때
- WHEN GET /games/{gameId}/state 를 호출하면
- THEN `pitcherPitchCount` 는 `null` 로 반환된다

#### Scenario: 종료된 경기
- GIVEN 경기 status 가 FINISHED / CANCELED / POSTPONED 일 때
- WHEN GET /games/{gameId}/state 를 호출하면
- THEN `pitcherPitchCount` 는 `null` 로 반환된다(종료 화면에서 투구수 영역 미표시)
