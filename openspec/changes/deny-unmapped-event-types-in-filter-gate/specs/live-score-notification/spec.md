## MODIFIED Requirements

### Requirement: 이벤트 필터 게이트의 미매핑 타입 처리 (fail-closed)

이벤트 필터 게이트(폰 잠금화면 강조·폰 알림음·워치 햅틱을 결정하는 `EventFilterGate` / `isEventTypeAllowedByFilter`)는 필터 옵션에 매핑된 이벤트 타입만 발화 대상으로 삼아야 한다(MUST). 필터 옵션에 매핑되지 않은 타입(OTHER, HALF_INNING_CHANGE, MOUND_VISIT 및 향후 추가되는 신규 타입)은 사용자 설정과 무관하게 알림음·진동·강조·햅틱을 발화하지 않는다(MUST NOT).

예외(MUST):
- **타입 없음(null/blank)**: `event_type` 키가 없는 페이로드(경기 시작, 분풀이 유도 등 비이벤트성 visible 푸시)는 이 게이트의 대상이 아니며 통과시킨다.
- **VICTORY**: 필터 설정 항목이 아닌 승리 순간 햅틱으로 항상 허용한다. (프로덕션 폰 발화 경로는 게이트를 경유하지 않지만, 워치 수신단과 테스트 도구는 게이트를 경유한다.)

이 규칙은 4개 클라이언트 사본(Android 폰·iOS 폰·Wear OS·watchOS) 전부에 동일하게 적용되어 drift 가 발생해서는 안 된다(MUST). 신규 이벤트 타입을 발화 대상으로 추가하려면 4개 게이트의 타입 매핑과 필터 옵션 UI 에 명시적으로 등록하는 것이 유일한 정식 경로다.

차단은 발화(알림음·진동·강조·햅틱)에만 적용된다. 잠금화면 ongoing 카드 본문의 경기 상태(스코어·이닝·BSO·타자/투수)는 게이트와 무관하게 계속 갱신된다(MUST).

#### Scenario: 타자 교체(OTHER) 이벤트 무발화

- **GIVEN** 사용자가 이벤트 필터를 득점·홈런·안타만 ON 으로 둔 상태에서 (다른 어떤 설정이어도 동일)
- **WHEN** 타자 교체 등 크롤러가 OTHER 로 분류한 이벤트가 스트림/푸시로 도착하면
- **THEN** 잠금화면 강조·알림음·진동·워치 햅틱이 발화되지 않는다. 카드 본문의 경기 상태 갱신은 유지된다.

#### Scenario: 공수교대·마운드 방문 무발화

- **GIVEN** 임의의 필터 설정 상태에서
- **WHEN** HALF_INNING_CHANGE 또는 MOUND_VISIT 이벤트가 도착하면
- **THEN** 어떤 클라이언트에서도 알림음·진동·강조·햅틱이 발화되지 않는다.

#### Scenario: 승리 햅틱 유지

- **GIVEN** 사용자가 워치로 경기를 관람 중이고 응원팀이 승리한 상태에서
- **WHEN** 폰이 VICTORY 햅틱 이벤트를 워치로 전송하면
- **THEN** 워치 수신단 게이트는 VICTORY 를 통과시켜 승리 햅틱이 정상 발화된다.

#### Scenario: 비이벤트성 visible 푸시 유지

- **GIVEN** 경기 시작(`game_start`) 또는 분풀이(`venting_loss`) visible 푸시가 도착한 상태에서 (페이로드에 `event_type` 없음)
- **WHEN** 클라이언트가 게이트를 평가하면
- **THEN** 타입 없음 분기로 통과되어 푸시가 정상 표시된다.
