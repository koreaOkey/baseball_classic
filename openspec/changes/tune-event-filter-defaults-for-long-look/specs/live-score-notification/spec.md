## MODIFIED Requirements

### Requirement: 선택 이벤트 long-look expand
사용자가 선호 이벤트 타입(`preferred_event_types` / `event_filter_*_enabled`)으로 선택한 이벤트가 발생할 때에만 라이브 스코어 노티가 일시적으로 long-look(또는 BigText) 스타일로 expand되며 햅틱이 함께 발화해야 한다(MUST). 선택되지 않은 이벤트에 대해 클라이언트는 일체의 노티 발화/갱신을 시도하지 않으며 햅틱도 발화하지 않는다.

**기본값(default)**: 신규 설치 또는 사용자가 명시적으로 토글하지 않은 상태에서 다음 이벤트만 ON 이다(MUST).
- ON: HOMERUN, SCORE(SAC_FLY_SCORE 포함), HIT
- OFF: STEAL(TAG_UP_ADVANCE 포함), WALK, OUT, DOUBLE_PLAY(TRIPLE_PLAY 포함), PITCHER_CHANGE

폰·워치 양 클라이언트는 동일한 기본값 매핑을 보유해야 하며 한쪽만 변경되어 drift 가 발생해서는 안 된다(MUST).

#### Scenario: 선택 이벤트 expand
- **GIVEN** 사용자가 이벤트 필터에 HOMERUN 을 ON 으로 둔 상태에서
- **WHEN** HOMERUN 이벤트가 발생하면
- **THEN** 라이브 스코어 노티가 long-look expand 로 강조되고 햅틱이 발화된다

#### Scenario: 미선택 이벤트 무발화
- **GIVEN** 사용자가 이벤트 필터에 WALK 를 OFF 로 둔 상태에서
- **WHEN** WALK 이벤트가 발생하면
- **THEN** 라이브 스코어 노티는 갱신되지 않으며(post 자체 skip) 햅틱도 발화되지 않는다. 본문의 "최근 이벤트" 라벨은 마지막으로 허용된 이벤트를 그대로 유지한다.

#### Scenario: 신규 설치 default
- **GIVEN** 사용자가 앱을 신규 설치하고 설정 진입 전 상태에서
- **WHEN** 도루·볼넷·아웃·병살·투수교체 이벤트가 발생하면
- **THEN** 폰·워치 모두 long-look 노티·햅틱이 발화되지 않는다. 홈런·득점·안타 이벤트는 정상 발화된다.

#### Scenario: 사용자 토글 후 발화
- **GIVEN** 사용자가 설정에서 아웃 토글을 ON 으로 변경한 뒤
- **WHEN** 아웃 이벤트가 발생하면
- **THEN** 폰·워치에서 모두 정상적으로 long-look 노티·햅틱이 발화된다.

### Requirement: 같은 식별자 in-place 갱신
이벤트가 발생할 때마다 라이브 스코어 노티는 동일한 식별자로 in-place 교체되어야 한다(MUST). 매 이벤트마다 새로운 노티 스택을 쌓아서는 안 된다.

다만 iOS 한정 부작용으로, 같은 identifier 의 silent replace 가 누적되면 다음 허용 이벤트의 wrist-raise wake 가 시스템에 의해 억제된다. 이를 막기 위해 클라이언트는 차단된 이벤트가 동반된 푸시에서는 noti post 호출 자체를 생략해야 한다(MUST). state-only 푸시(이벤트 미동반)는 점수 동기 유지를 위해 항상 post 한다.

#### Scenario: 허용 이벤트 in-place 갱신
- **WHEN** 경기 도중 허용 이벤트로 인해 스코어/이닝/BSO 가 변경되면
- **THEN** 기존 라이브 스코어 노티가 같은 식별자로 새 콘텐츠로 교체되며 알림 스택이 중복되지 않는다

#### Scenario: 차단 이벤트 동반 푸시 skip
- **WHEN** 차단된 이벤트(예: 사용자가 OFF 로 둔 OUT) 가 동반된 푸시가 도착하면
- **THEN** 클라이언트는 ongoing 노티 post 를 호출하지 않으며 기존 노티 본문은 변경되지 않은 채 남는다

#### Scenario: state-only 푸시는 항상 post
- **WHEN** 이벤트 타입이 비어 있는 상태 동기 푸시가 도착하면
- **THEN** 차단 여부와 무관하게 ongoing 노티가 갱신되어 점수·이닝·BSO 가 최신 상태로 유지된다
