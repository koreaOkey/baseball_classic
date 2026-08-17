# crawling — 수비 실책 감지 델타

## ADDED Requirements

### Requirement: 수비 실책 감지 metadata
크롤러는 중계 텍스트의 수비 실책을 감지하여 이벤트 metadata에 실책 여부·포지션을 MUST 부착하고, 이벤트 타입(`type`)은 MUST 변경하지 않는다.

부연: 햅틱·푸시·클라이언트 렌더는 전부 이벤트 타입 기반이므로 실책 감지는 metadata만 추가하여 무영향이어야 한다.

#### Scenario: 실책 감지 및 포지션 판정
- GIVEN 중계 텍스트가 "투수 교체 후 유격수 송구 실책으로 출루"일 때
- WHEN 이벤트를 빌드하면
- THEN metadata.isError = true, metadata.errorPosition = "유격수", metadata.errorPositionCode = "SS"로 부착한다
- AND 이벤트 타입은 실책 감지로 인해 변경되지 않는다

#### Scenario: 무득점 실책도 수집
- GIVEN 실책이 실점(SCORE)이나 병살 등으로 분류되지 않는 무득점 상황일 때
- WHEN 이벤트를 빌드하면
- THEN 이벤트는 정상 전송되고 metadata에 실책 정보가 실린다
- AND 실책을 범한 팀은 기존 metadata.defenseTeam(수비팀)이 가리킨다

#### Scenario: 오탐 제외
- GIVEN 텍스트가 "실책성 안타"(안타로 기록), "무실책", "실책 없이" 등일 때
- WHEN 실책을 감지하면
- THEN isError를 부착하지 않는다(실책 아님)

#### Scenario: 포지션 미상
- GIVEN 텍스트에 "실책"은 있으나 수비 포지션 토큰이 없을 때
- WHEN 이벤트를 빌드하면
- THEN metadata.isError = true 만 부착하고 errorPosition은 생략한다
