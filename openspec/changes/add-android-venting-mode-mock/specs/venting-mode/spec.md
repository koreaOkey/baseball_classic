# venting-mode — 분풀이 모드 (Android 목업 동등성)

## MODIFIED Requirements

### Requirement: 플랫폼 지원 범위
분풀이 모드 Phase 1 목업 플로우(오픈 조건, TOP5+감독 선택, 룸 파괴 메커니즘, 도구 트레이 타격 연출, 완파·재도전)는 iOS와 Android 양쪽에서 동일하게 동작해야 한다(SHALL). 두 플랫폼 모두 DEBUG 빌드 + `venting_mode_enabled` 로컬 토글의 이중 게이트 뒤에서만 활성화되며(SHALL), 릴리즈 사용자에게는 노출되지 않아야 한다(SHALL NOT). 파괴 밸런스 상수(임계값 0.33/0.66/1.0, 탭당 1/40)와 목업 후보 데이터는 양 플랫폼이 동일 값을 사용해야 한다(SHALL).

#### Scenario: Android 홈카드 노출
- **WHEN** Android DEBUG 빌드에서 설정 > DEBUG > 분풀이 모드 토글을 켜고 오픈 조건이 충족되면
- **THEN** 홈 "오늘의 경기" 헤더 아래에 "오늘의 아쉬운 순간" 카드가 노출된다

#### Scenario: Android 파괴 플로우 동등성
- **WHEN** Android에서 후보를 선택하고 인형을 40회 탭하면
- **THEN** iOS와 동일하게 33%/66%에서 균열·터짐 스프라이트로 전환되고 100%에서 완파 화면으로 이동한다

#### Scenario: 릴리즈 빌드 미노출
- **WHEN** Android 릴리즈 빌드이면
- **THEN** 설정 DEBUG 섹션과 분풀이 카드가 렌더링되지 않는다
