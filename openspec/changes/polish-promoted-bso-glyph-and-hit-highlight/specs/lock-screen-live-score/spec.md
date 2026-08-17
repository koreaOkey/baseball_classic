# Lock Screen Live Score

## ADDED Requirements

### Requirement: Promoted BSO empty slots use emoji circle

promoted 카드 BSO 줄의 빈 슬롯은 채운 슬롯과 동일한 이모지 원 `⚪`(U+26AA)로 렌더링되어야 한다(SHALL). 텍스트 글리프 `○`(U+25CB)를 사용하지 않아야 한다(MUST NOT) — 크기·기준선 불일치를 피하기 위함이다.

#### Scenario: 볼 카운트 부분 채움

- **WHEN** promoted 카드가 볼 1개인 상태를 표시할 때
- **THEN** 볼 슬롯은 `🟢⚪⚪`로 렌더링된다(채운 원 + 이모지 빈 원)

### Requirement: Hit events are highlighted like scores and homeruns

테스트 자동 시뮬레이션의 하이라이트 판정은 득점·홈런과 함께 안타(HIT)도 강조 대상으로 포함해야 한다(SHALL). 이는 프로덕션 LOCK_SCREEN 이벤트 필터 기본값(score/homerun/hit = ON)과 일치해야 한다(MUST).

#### Scenario: 시뮬레이션 안타 이벤트

- **WHEN** 자동 시뮬레이션이 안타(HIT) 이벤트를 게시할 때
- **THEN** 해당 게시는 강조(heads-up + 진동 + 이벤트 문구)로 표시된다

#### Scenario: 사용자 이벤트 필터와 무관한 테스트 강조

- **WHEN** 사용자가 "안타" 잠금화면 이벤트 필터를 OFF한 상태에서 테스트 자동 시뮬레이션의 HIT 이벤트가 게시될 때
- **THEN** 테스트 미리보기는 `bypassEventFilter`로 필터를 무시하고 강조(alerts 채널)로 게시한다
- **AND** 프로덕션 게시 경로(bypass=false)는 기존대로 사용자 필터를 준수한다
