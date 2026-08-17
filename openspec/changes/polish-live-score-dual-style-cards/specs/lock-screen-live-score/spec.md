# Lock Screen Live Score

## ADDED Requirements

### Requirement: User explicitly selects between system and custom card styles

설정의 "잠금화면 라이브 스코어" 섹션은 시스템 카드(Promoted)와 커스텀 카드(Ongoing) 중 하나를 선택하는 2택 UI를 제공해야 하며(SHALL), 선택이 자동 게시 스타일을 지배해야 한다(MUST). 기본값은 시스템 카드이다.

#### Scenario: 커스텀 카드 선택

- **WHEN** 사용자가 설정에서 "커스텀 카드 (Ongoing)"를 선택할 때
- **THEN** 다음 라이브 스코어 게시부터 커스텀 리치 카드로 표시된다
- **AND** 테스트 도구의 미리보기 스타일 기본값도 커스텀 카드가 된다

### Requirement: Custom card collapsed row carries score, inning, and BSO

커스텀 카드의 접힘 상태는 한 줄에 팀 로고·구단명·스코어·이닝·BSO 카운트 점을 표시해야 한다(SHALL). 구단명은 사용자 표기 설정과 무관하게 짧은 형태를 사용해야 한다(MUST).

#### Scenario: 접힘 카드 표시

- **WHEN** 커스텀 카드가 접힌 상태로 표시될 때
- **THEN** "로고 KIA 3 : 4 LG 로고 … 9회초 · ●●○ ●○ ●○" 형태로 렌더링되고 BSO 점은 실제 카운트 색으로 채워진다

### Requirement: Highlight events are emphasized in both styles

득점·홈런·안타 강조 시 시스템 카드는 본문을 이벤트 문구 단독(+이벤트별 이모지)으로 교체해야 하고(SHALL), 커스텀 카드는 하이라이트 행으로 표시해야 한다(SHALL). 평상시 시스템 카드 본문은 이벤트 문구가 아닌 BSO 줄이어야 한다(MUST).

#### Scenario: 시스템 카드 홈런 강조

- **WHEN** 시스템 카드 스타일에서 홈런 강조 게시가 발생할 때
- **THEN** 본문이 "💥 <이벤트 문구>" 단독으로 교체되고 소리/진동이 발생한다

#### Scenario: 시스템 카드 평상시

- **WHEN** 강조가 아닌 일반 갱신일 때
- **THEN** 접힘 본문은 BSO 줄이고 펼침에는 "타자 X | 투수 Y n구" 줄이 추가된다
