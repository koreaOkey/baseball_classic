## ADDED Requirements

### Requirement: 경기 상태 응답에 라인업 포함
백엔드 `GameStateOut` 응답은 해당 경기의 홈·어웨이 라인업 슬롯을 포함해야 한다(MUST).

#### Scenario: 라인업 보유 경기 응답
- **GIVEN** 어떤 경기의 `GameLineupSlot` 테이블에 활성 슬롯이 저장되어 있을 때
- **WHEN** 클라이언트가 `/games/<id>/state` 를 호출하면
- **THEN** 응답 본문에 `homeLineup` 과 `awayLineup` 배열이 포함되며 각 슬롯에 `battingOrder`, `playerName`, `positionCode`, `positionName`, `isStarter`, `isActive` 가 들어있다

#### Scenario: 라인업 미보유 경기 응답
- **GIVEN** 어떤 경기의 라인업 슬롯이 DB 에 저장된 적이 없을 때
- **WHEN** 클라이언트가 `/games/<id>/state` 를 호출하면
- **THEN** `homeLineup` / `awayLineup` 은 빈 배열(`[]`) 로 응답된다

#### Scenario: 비활성 슬롯 제외
- **GIVEN** 일부 슬롯의 `is_active = false` 일 때
- **WHEN** 응답이 생성되면
- **THEN** 비활성 슬롯은 `homeLineup` / `awayLineup` 응답에서 제외되며 현재 시점 수비만 노출된다
