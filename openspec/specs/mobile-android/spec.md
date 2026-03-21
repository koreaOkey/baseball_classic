# Mobile Android Specification

## Purpose
Android(Jetpack Compose) 스마트폰에서 실시간 야구 경기를 확인하고 Wear OS 워치와 연동하는 모바일 앱.

## Requirements

### Requirement: 당일 경기 목록
앱은 오늘의 경기 목록을 표시해야 한다(MUST).

#### Scenario: 경기 목록 로드
- GIVEN 사용자가 앱을 실행했을 때
- WHEN 홈 화면이 표시되면
- THEN 당일 KBO 경기 목록을 백엔드에서 조회하여 카드 형태로 표시한다

### Requirement: 팀 선택 및 관심 경기
사용자는 관심 팀을 선택하고 해당 팀 경기에 집중할 수 있어야 한다(MUST).

#### Scenario: 관심 팀 경기 LIVE 전환
- GIVEN 사용자가 관심 팀을 설정했을 때
- WHEN 해당 팀 경기가 LIVE 상태로 전환되면
- THEN Wear OS 워치에 동기화 프롬프트를 전송한다

### Requirement: Wear OS 워치 동기화 플로우
모바일은 Wear OS 워치와 Data Layer API를 통해 양방향 동기화해야 한다(MUST).

#### Scenario: 워치 동기화 요청
- GIVEN 관심 팀 경기가 LIVE가 되었을 때
- WHEN 모바일이 /watch/prompt/current 경로로 프롬프트를 전송하면
- THEN Wear OS 워치에 동기화 동의 팝업이 표시된다

#### Scenario: 워치 동기화 응답 수신
- GIVEN 워치에서 동기화 응답이 전송되었을 때
- WHEN /watch/sync-response/{timestamp} 경로로 응답을 수신하면
- THEN 수락 시 syncedGameId를 저장하고 실시간 동기화를 시작한다

### Requirement: 실시간 경기 데이터 수신
모바일은 선택된 경기의 실시간 데이터를 수신해야 한다(MUST).

#### Scenario: 경기 상태 폴링/WebSocket
- GIVEN 사용자가 경기를 선택했을 때
- WHEN 경기가 진행 중이면
- THEN WebSocket 또는 폴링으로 state/events를 실시간 수신하여 화면에 반영한다

### Requirement: Data Layer 서비스
모바일은 워치와의 통신을 위한 Data Layer 리스너 서비스를 운영해야 한다(MUST).

#### Scenario: 워치 응답 수신 서비스
- GIVEN 앱이 실행 중일 때
- WHEN 워치로부터 Data Layer 메시지가 도착하면
- THEN MobileDataLayerListenerService가 수신하여 WearWatchSyncBridge로 전달한다
