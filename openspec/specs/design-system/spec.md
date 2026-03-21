# Design System Specification

## Purpose
BaseHaptic 서비스의 크로스 플랫폼(워치/모바일/웹) 디자인 기반 시스템. 컬러, 타이포그래피, 반응형 규칙을 정의한다.

## Requirements

### Requirement: 컬러 팔레트
모든 플랫폼은 공통 컬러 팔레트를 기반으로 해야 한다(MUST).

#### Scenario: 다크 테마 기본
- GIVEN BaseHaptic 앱이 실행될 때
- WHEN 기본 테마가 적용되면
- THEN Gray 계열(950~300) 다크 팔레트를 기본으로 사용하고, 팀 컬러는 액센트로 오버레이한다

#### Scenario: 웹 다크 모드
- GIVEN 웹 앱에서 다크 모드가 활성화될 때
- WHEN --dark 클래스가 적용되면
- THEN OKLCH 컬러 스페이스 기반의 반전된 색상 스케일을 적용한다

### Requirement: 타이포그래피
각 플랫폼은 가독성 기준에 맞는 타이포그래피를 적용해야 한다(MUST).

#### Scenario: 워치 타이포그래피
- GIVEN 워치 앱에서 텍스트를 표시할 때
- WHEN 타이포그래피가 적용되면
- THEN body1(16sp), title1(24sp bold), caption1(12sp)을 기본으로 사용하고 화면 크기에 따라 조정한다

### Requirement: 워치 반응형 프로파일
워치 UI는 3단계 크기 프로파일과 화면 형태를 기준으로 반응형 레이아웃을 적용해야 한다(MUST).

#### Scenario: 프로파일 기반 레이아웃
- GIVEN 워치 화면 크기가 감지되었을 때
- WHEN UI가 렌더링되면
- THEN 40개 이상의 디멘션 파라미터(패딩, 폰트, 위젯 크기)를 프로파일에 맞게 자동 조정한다

### Requirement: 접근성 기반 컴포넌트 (웹)
웹 UI 컴포넌트는 접근성 표준을 준수해야 한다(MUST).

#### Scenario: Radix UI 프리미티브 사용
- GIVEN 웹 앱에서 인터랙티브 컴포넌트를 구현할 때
- WHEN 컴포넌트를 생성하면
- THEN Radix UI 프리미티브를 기반으로 키보드 내비게이션, 스크린 리더 호환성을 보장한다

### Requirement: 반경(Radius) 시스템
모든 플랫폼은 일관된 모서리 반경 스케일을 사용해야 한다(SHOULD).

#### Scenario: 반경 토큰 적용
- GIVEN UI 요소에 모서리 반경을 적용할 때
- WHEN 디자인 토큰을 참조하면
- THEN sm, md, lg, xl 스케일 중 적절한 값을 적용한다
