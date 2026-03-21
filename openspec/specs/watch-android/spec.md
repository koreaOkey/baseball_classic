# Watch Android (Wear OS) Specification

## Purpose
Wear OS 스마트워치에서 실시간 야구 경기를 햅틱 피드백으로 전달하는 워치 앱.

## Requirements

### Requirement: 동기화 동의 플로우
워치는 모바일의 동기화 요청에 대해 사용자 동의를 받아야 한다(MUST).

#### Scenario: 동기화 프롬프트 수신
- GIVEN Android 모바일에서 Data Layer로 동기화 프롬프트가 전송되었을 때
- WHEN 워치가 /watch/prompt/current 경로의 프롬프트를 수신하면
- THEN "관람하겠습니까?" 팝업을 표시한다

#### Scenario: 동기화 수락
- GIVEN 동기화 팝업이 표시되었을 때
- WHEN 사용자가 "예"를 선택하면
- THEN /watch/sync-response/{timestamp}로 수락 응답을 전송하고 라이브 경기 화면으로 전환한다

#### Scenario: 동기화 거부
- GIVEN 동기화 팝업이 표시되었을 때
- WHEN 사용자가 "아니오"를 선택하면
- THEN 모바일에 거부 응답을 전송하고 현재 화면을 유지한다

### Requirement: 라이브 경기 화면
워치는 실시간 스코어보드와 이벤트를 표시해야 한다(MUST).

#### Scenario: 스코어보드 표시
- GIVEN 동기화된 경기가 진행 중일 때
- WHEN 라이브 경기 화면이 활성화되면
- THEN 스코어, 이닝, BSO 카운트, 주자 상황을 표시한다

### Requirement: 햅틱 피드백
워치는 경기 이벤트 발생 시 햅틱 피드백을 전달해야 한다(MUST).

#### Scenario: 이벤트별 햅틱 실행
- GIVEN 라이브 경기 중 이벤트가 발생했을 때
- WHEN 이벤트에 haptic_pattern이 포함되어 있으면
- THEN Vibrator API를 통해 해당 패턴에 맞는 진동을 실행한다

#### Scenario: 화면 깨우기
- GIVEN 워치 화면이 꺼져 있을 때
- WHEN 햅틱 이벤트가 발생하면
- THEN WakeLock을 사용하여 화면을 자동으로 켠다

### Requirement: 포그라운드 서비스
워치는 Doze 모드에서도 햅틱 이벤트를 계속 수신해야 한다(MUST).

#### Scenario: 백그라운드 유지
- GIVEN 경기 관람 중 워치가 Doze 모드로 전환될 때
- WHEN 포그라운드 서비스가 실행 중이면
- THEN 햅틱 이벤트 수신과 전달을 중단하지 않는다

### Requirement: 반응형 UI
워치는 다양한 Wear OS 화면 크기와 형태에 대응해야 한다(MUST).

#### Scenario: 화면 크기별 레이아웃 적용
- GIVEN 다양한 Wear OS 디바이스에서 앱이 실행될 때
- WHEN 화면 크기가 감지되면
- THEN small(≤192dp), medium(≤225dp), large(>225dp) 프로파일에 맞게 폰트/패딩/위젯 크기를 조정한다

#### Scenario: 원형/사각형 화면 대응
- GIVEN 원형 또는 사각형 화면의 워치에서 실행될 때
- WHEN 화면 형태가 감지되면
- THEN 해당 형태에 최적화된 레이아웃을 적용한다

### Requirement: 홈런 애니메이션
홈런 이벤트 발생 시 Media3 ExoPlayer로 영상을 재생해야 한다(MUST).

#### Scenario: 홈런 영상 재생
- GIVEN 홈런 이벤트가 감지되었을 때
- WHEN 라이브 경기 화면에서 이벤트를 수신하면
- THEN 최적화된 영상(450x450, 20fps, 263KB)을 4초간 재생한다
