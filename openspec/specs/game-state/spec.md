# Game State Specification

## Purpose
야구 경기의 실시간 상태(스코어, 이닝, BSO, 주자, 라인업)를 관리하는 핵심 도메인 모델.
## Requirements
### Requirement: 경기 상태 모델
시스템은 경기의 전체 상태를 하나의 스냅샷으로 표현해야 한다(MUST).

#### Scenario: 상태 조회
- GIVEN 진행 중인 경기가 있을 때
- WHEN GET /games/{gameId}/state 를 호출하면
- THEN score, inning, inningHalf, ball, strike, out, bases, currentBatter, currentPitcher 를 포함한 상태를 반환한다

### Requirement: 경기 목록 조회
시스템은 날짜 및 상태 기준으로 경기 목록을 조회할 수 있어야 한다(MUST).

#### Scenario: 당일 라이브 경기 조회
- GIVEN LIVE 상태의 경기가 존재할 때
- WHEN GET /games?status=LIVE&date=2026-03-21 을 호출하면
- THEN 해당 날짜의 LIVE 경기 목록을 반환한다

### Requirement: 이벤트 타임라인
시스템은 경기 이벤트를 커서 기반으로 페이징 조회할 수 있어야 한다(MUST).

#### Scenario: 커서 기반 이벤트 조회
- GIVEN 경기에 이벤트가 누적되어 있을 때
- WHEN GET /games/{gameId}/events?after={cursor}&limit=50 을 호출하면
- THEN 커서 이후의 이벤트를 시간순으로 최대 50건 반환한다

### Requirement: BSO 카운트 관리
시스템은 3아웃 시 ball/strike 카운트를 자동 리셋해야 한다(MUST).

#### Scenario: 3아웃 카운트 리셋
- GIVEN 아웃 카운트가 3이 되었을 때
- WHEN 이닝이 전환되면
- THEN ball, strike, out 카운트를 모두 0으로 리셋한다

### Requirement: 경기 상태 전이
경기 상태는 SCHEDULED → LIVE → FINISHED 순서로만 전이되어야 한다(MUST).

#### Scenario: 정상 전이
- GIVEN 경기 상태가 SCHEDULED일 때
- WHEN 첫 번째 릴레이 데이터가 수신되면
- THEN 상태를 LIVE로 전환한다

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

### Requirement: 이벤트 응답에 inning 메타데이터 포함
백엔드 `GameEventOut` 응답은 이벤트가 발생한 회 표기(`inning`) 를 옵셔널 필드로 포함해야 한다(MUST).

#### Scenario: inning 페이로드와 함께 INSERT
- **GIVEN** 크롤러가 `CrawlerEventIn.inning` 을 채워 보냈을 때
- **WHEN** 이벤트가 DB 에 저장되고 응답이 생성되면
- **THEN** `GameEventOut.inning` 에 그 값이 그대로 노출된다

#### Scenario: inning 누락 시 game.inning fallback
- **GIVEN** 크롤러 페이로드의 `inning` 이 없을 때
- **WHEN** 이벤트가 저장되면
- **THEN** 저장 시점의 `game.inning` 값으로 채워져 응답에 노출된다

#### Scenario: 마이그레이션 이전 이벤트
- **GIVEN** 마이그레이션 이전에 저장돼 inning 이 NULL 인 이벤트
- **WHEN** 응답이 생성되면
- **THEN** `inning` 필드는 `null` 로 전달된다

