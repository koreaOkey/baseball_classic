## MODIFIED Requirements

### Requirement: 포그라운드 서비스
워치는 Doze 모드에서도 햅틱 이벤트를 계속 수신해야 하며(MUST), 라이브 경기 동안 게시되는 ongoing 노티에 현재 스코어·이닝·BSO와 가장 최근 이벤트 1개를 동적으로 갱신해야 한다(MUST). 정적인 "경기 관람 중" 텍스트만 노출해서는 안 된다.

#### Scenario: 백그라운드 유지
- **GIVEN** 경기 관람 중 워치가 Doze 모드로 전환될 때
- **WHEN** 포그라운드 서비스가 실행 중이면
- **THEN** 햅틱 이벤트 수신과 전달을 중단하지 않는다

#### Scenario: ongoing 노티 동적 갱신
- **WHEN** 워치가 라이브 경기 데이터(스코어·이닝·BSO 변화 또는 새 이벤트)를 수신하면
- **THEN** ongoing 노티의 콘텐츠가 최신 스코어·이닝·BSO와 가장 최근 이벤트 1개로 in-place 교체된다

## ADDED Requirements

### Requirement: Watch 채널 선택 이벤트 가드
워치는 사용자가 Watch 채널에서 선택한 이벤트에만 long-look expand 표시와 햅틱·앱 자동 진입을 수행해야 한다(MUST). 선택되지 않은 이벤트는 ongoing 노티 in-place 갱신만 수행한다.

#### Scenario: 선택 이벤트 expand
- **GIVEN** 사용자가 Watch 채널에서 HOMERUN을 켠 상태일 때
- **WHEN** HOMERUN 이벤트가 워치에 도착하면
- **THEN** ongoing 노티가 long-look으로 expand되고 햅틱이 발화되며 약 3초 후 ongoing으로 복귀한다

#### Scenario: 미선택 이벤트 조용한 갱신
- **GIVEN** 사용자가 Watch 채널에서 WALK를 끈 상태일 때
- **WHEN** WALK 이벤트가 워치에 도착하면
- **THEN** ongoing 노티의 스코어·최근 이벤트 라인만 in-place 갱신되며 expand·햅틱·앱 자동 진입은 발생하지 않는다

## REMOVED Requirements

### Requirement: Wear OS 타일 (GameTileService)
**Reason**: 라이브 스코어 ongoing 노티가 같은 정보(스코어·이닝·BSO·최근 이벤트)를 더 풍부하게 제공하고 양 플랫폼 동일 UX를 유지한다. 타일은 별도 ProtoLayout 코드를 유지해야 하는 부담이 있어 deprecate한다.
**Migration**: 사용자는 워치 홈에서 위로 스와이프해 노티 센터의 ongoing 라이브 스코어 노티를 통해 동일 정보를 확인한다. 타일 구독 UI 안내 문구 별도 노출 없이 앱 업데이트 시 자동 제거한다.
