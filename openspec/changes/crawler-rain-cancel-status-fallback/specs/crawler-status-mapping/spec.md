## ADDED Requirements

### Requirement: statusInfo 폴백으로 경기 취소/연기 감지
statusCode가 알려진 취소/연기 코드에 매핑되지 않을 때, statusInfo 텍스트에서 취소/연기 키워드를 감지하여 경기 상태를 판별해야 한다. statusCode가 정상 매핑되는 경우 statusInfo는 무시한다.

#### Scenario: statusCode 미매핑 + statusInfo에 우천취소 포함
- **WHEN** statusCode가 알려진 매핑에 없고 statusInfo가 "우천취소"일 때
- **THEN** 경기 상태를 CANCELED로 판별한다

#### Scenario: statusCode 미매핑 + statusInfo에 경기연기 포함
- **WHEN** statusCode가 알려진 매핑에 없고 statusInfo가 "경기연기"일 때
- **THEN** 경기 상태를 POSTPONED로 판별한다

#### Scenario: statusCode가 정상 매핑되면 statusInfo 무시
- **WHEN** statusCode가 "LIVE"이고 statusInfo가 "우천취소"일 때
- **THEN** statusCode 매핑을 우선하여 LIVE로 판별한다

#### Scenario: statusCode와 statusInfo 모두 매핑 정보 없음
- **WHEN** statusCode가 알려진 매핑에 없고 statusInfo에도 취소/연기 키워드가 없을 때
- **THEN** 기본값 SCHEDULED로 판별한다
