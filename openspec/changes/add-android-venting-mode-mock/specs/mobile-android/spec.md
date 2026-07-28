# mobile-android — 분풀이 모드 홈 통합

## ADDED Requirements

### Requirement: 분풀이 홈카드 및 목업 경기 주입
Android 홈 화면은 분풀이 모드 활성 시 "오늘의 경기" 헤더 아래에 분풀이 카드 컨테이너를 렌더링해야 한다(SHALL). 오픈 조건 미충족 시 카드 영역은 아무것도 렌더링하지 않아야 한다(SHALL NOT). 분풀이 토글 ON + 마이팀 설정 + 완료된 마이팀 경기가 없으면 목업 마이팀 패배 완료 경기(3:7)를 오늘의 경기 목록 상단에 주입해야 한다(SHALL) — iOS HomeScreen과 동일 동작.

#### Scenario: 목업 패배 경기 주입
- **WHEN** 분풀이 토글이 ON이고 오늘 완료된 마이팀 경기가 없으면
- **THEN** "경기 종료" 상태의 마이팀 3:7 패배 카드가 목록 상단에 표시된다

### Requirement: 설정 DEBUG 토글
Android 설정 화면은 DEBUG 빌드에서만 "DEBUG" 섹션과 "분풀이 모드" 토글을 표시해야 한다(SHALL). 토글 상태는 SharedPreferences `venting_mode_enabled`에 영속화된다(SHALL).

#### Scenario: 토글로 활성화
- **WHEN** 사용자가 설정 > DEBUG > 분풀이 모드를 ON 하고 홈으로 이동하면
- **THEN** 분풀이 홈카드가 노출된다
