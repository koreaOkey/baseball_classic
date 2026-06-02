## ADDED Requirements

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
