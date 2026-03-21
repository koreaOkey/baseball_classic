# Watch iOS (watchOS) Specification

## Purpose
Apple Watch(watchOS/SwiftUI)에서 실시간 야구 경기를 햅틱 피드백으로 전달하는 워치 앱.

## Requirements

### Requirement: 동기화 동의 플로우
워치는 iPhone의 동기화 요청에 대해 사용자 동의를 받아야 한다(MUST).

#### Scenario: 동기화 프롬프트 수신
- GIVEN iPhone에서 WCSession을 통해 동기화 프롬프트가 전송되었을 때
- WHEN Apple Watch가 프롬프트를 수신하면
- THEN "관람하겠습니까?" 팝업을 표시한다

#### Scenario: 동기화 수락
- GIVEN 동기화 팝업이 표시되었을 때
- WHEN 사용자가 "예"를 선택하면
- THEN WCSession으로 수락 응답을 전송하고 라이브 경기 화면으로 전환한다

#### Scenario: 동기화 거부
- GIVEN 동기화 팝업이 표시되었을 때
- WHEN 사용자가 "아니오"를 선택하면
- THEN iPhone에 거부 응답을 전송하고 현재 화면을 유지한다

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
- THEN WKInterfaceDevice haptic API를 통해 해당 패턴에 맞는 진동을 실행한다

#### Scenario: Digital Crown 연동
- GIVEN 라이브 경기 화면이 활성화되어 있을 때
- WHEN 사용자가 Digital Crown을 회전하면
- THEN 이벤트 타임라인을 스크롤할 수 있다

### Requirement: 백그라운드 세션 유지
워치는 백그라운드에서도 경기 데이터를 수신해야 한다(MUST).

#### Scenario: Extended Runtime Session
- GIVEN 경기 관람 중 화면이 꺼지거나 다른 앱으로 전환될 때
- WHEN Extended Runtime Session이 활성화되어 있으면
- THEN 햅틱 이벤트 수신과 전달을 유지한다

#### Scenario: 세션 시간 제한 대응
- GIVEN watchOS Extended Runtime Session에 시간 제한이 있을 때
- WHEN 세션이 만료되면
- THEN 사용자에게 알리고 앱 복귀 시 자동 재연결한다

### Requirement: 반응형 UI
워치는 다양한 Apple Watch 화면 크기에 대응해야 한다(MUST).

#### Scenario: 화면 크기별 레이아웃 적용
- GIVEN 다양한 Apple Watch 모델에서 앱이 실행될 때
- WHEN 화면 크기가 감지되면
- THEN 41mm, 45mm, 49mm(Ultra) 모델에 맞게 폰트/패딩/위젯 크기를 조정한다

### Requirement: 컴플리케이션
사용자는 워치 페이스에서 경기 상태를 바로 확인할 수 있어야 한다(SHOULD).

#### Scenario: 컴플리케이션 표시
- GIVEN 사용자가 BaseHaptic 컴플리케이션을 워치 페이스에 추가했을 때
- WHEN 관심 팀 경기가 진행 중이면
- THEN 현재 스코어와 이닝을 컴플리케이션에 표시한다

### Requirement: 홈런 애니메이션
홈런 이벤트 발생 시 애니메이션을 재생해야 한다(MUST).

#### Scenario: 홈런 애니메이션 재생
- GIVEN 홈런 이벤트가 감지되었을 때
- WHEN 라이브 경기 화면에서 이벤트를 수신하면
- THEN watchOS 최적화된 애니메이션을 재생한다
