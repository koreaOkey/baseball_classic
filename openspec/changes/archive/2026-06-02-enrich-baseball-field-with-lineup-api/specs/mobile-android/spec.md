## ADDED Requirements

### Requirement: 필드 카드 수비 라벨이 백엔드 라인업으로 채워짐
Android 모바일 앱의 라이브 경기 필드 카드는 백엔드 `GameStateOut.homeLineup` / `awayLineup` 을 수비팀 기준으로 매핑하여 8 개 수비 슬롯 라벨을 자동 표시해야 한다(MUST).

#### Scenario: 라인업 도착 시 자동 표시
- **GIVEN** 백엔드 응답에 활성 라인업이 포함되어 있고 이닝 표기가 "N회초" 또는 "N회말" 일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 수비팀(이닝 "초"=홈수비, "말"=어웨이수비) 의 라인업 슬롯이 한글 positionName 으로 매핑되어 LF/CF/RF/SS/2B/3B/1B/C 8 개 라벨에 자동 표시된다

#### Scenario: 라인업 미보유
- **GIVEN** 백엔드 응답에 라인업이 빈 배열일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 8 개 수비 슬롯은 표시되지 않으며 P 와 B 두 라벨만 노출된다

#### Scenario: 이닝 초/말 전환
- **GIVEN** 이닝이 "초" 에서 "말" 로 전환될 때
- **WHEN** 새 상태가 수신되면
- **THEN** 필드 카드 라벨이 새 수비팀의 라인업으로 교체된다

#### Scenario: positionName 매핑 미스
- **GIVEN** 라인업 슬롯의 한글 positionName 이 표준 9 개 포지션 명칭과 일치하지 않을 때
- **WHEN** 매핑 헬퍼가 동작하면
- **THEN** 해당 슬롯은 어느 라벨 위치에도 표시되지 않고 다른 슬롯 표시에 영향이 없다
