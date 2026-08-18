## MODIFIED Requirements

### Requirement: 잠금화면 Live Activity push 는 업데이트 budget 을 고려해 발송한다

백엔드는 iOS 의 Live Activity 업데이트 budget(priority 10 push 초과분은 기기에서 조용히 드롭됨)을 전제로 발송해야 한다(MUST):

- 직전 발송과 동일한 content-state 는 재발송하지 않는다(MUST NOT). 단, heartbeat 간격(60s) 경과 시 stale-date 갱신을 위한 재전송은 허용한다(MAY).
- 스코어·주자·아웃·이닝·투수·경기상태 변화, 주요 이벤트 타입(홈런·득점·안타·출루·주루·아웃·병살·투수교체), `event=end` 는 즉시 발송한다(MUST).
- 볼카운트·타자만 변한 일상 갱신은 최소 간격(20s) 코얼레싱 후 발송한다(MUST).
- 발송 priority 는 10 을 사용한다(MUST). priority 5 는 잠금 상태에서 전달이 지연/유실됨이 실기기에서 확인되어(2026-08-18) 일상 갱신에도 사용하지 않는다(MUST NOT).
- 모든 liveactivity push 에 `stale-date`(발송 시각 +180s)를 포함한다(MUST).

클라이언트는 다음을 만족해야 한다(MUST):

- 위젯 확장은 `NSSupportsLiveActivitiesFrequentUpdates` 를 선언해 고빈도 갱신 budget 을 확보한다.
- 로컬 업데이트의 staleDate 는 백엔드 stale-date 와 동일한 180s 를 사용한다.
- `context.isStale` 이면 잠금화면 카드에 "동기화 지연"을 표시해 낡은 데이터임을 드러낸다.

#### Scenario: 잠금 상태 장시간 관람에도 주요 순간 계속 갱신

- **GIVEN** 사용자가 잠금화면 라이브 스코어를 켜고 폰을 잠근 채 1시간 이상 방치한 상태에서
- **WHEN** 득점·아웃·이닝 전환 등 주요 변화가 발생하면
- **THEN** 즉시 push 로 카드가 갱신된다. 볼카운트 변화는 20s 코얼레싱 간격으로 준실시간 반영된다.

#### Scenario: 동일 상태 중복 발송 금지 + heartbeat

- **GIVEN** 이닝 교대 등으로 경기 상태가 변하지 않는 구간에서
- **WHEN** 크롤러 ingest 가 동일 상태로 반복 도착하면
- **THEN** heartbeat 간격 내에는 push 를 보내지 않고, 간격 경과 시에만 재전송해 stale-date 를 갱신한다.

#### Scenario: 갱신 두절 시 stale 가시화

- **GIVEN** push 전달이 어떤 이유로든 중단된 상태에서
- **WHEN** 마지막 업데이트로부터 180초가 경과하면
- **THEN** 잠금화면 카드는 이벤트 라벨 대신 "동기화 지연"을 표시한다. 앱 진입 시 WebSocket 경로로 즉시 복구된다.
