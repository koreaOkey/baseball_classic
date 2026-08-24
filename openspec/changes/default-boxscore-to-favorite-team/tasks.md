# Tasks — default-boxscore-to-favorite-team

## 1. 응원팀 기본 노출
- [x] 1.1 iOS `BoxscoreSection` — `selectedSide`를 옵셔널로 바꾸고 `selected_team` 기반 `defaultSide` 계산 (홈이면 .home, 아니면 .away)
- [x] 1.2 Android — `boxscoreShowsHome`을 `Boolean?`로 바꾸고 렌더 시 `LocalTeamTheme.current.team == state.homeTeamId`로 기본값 계산

## 2. 명칭 변경
- [x] 2.1 iOS·Android 토글 라벨 "OO팀 타자" → "OO팀 기록"
- [x] 2.2 iOS·Android 타자 표 위에 "타자 기록" 제목 추가 (기존 "투수 기록"과 대칭)

## 3. 검증
- [x] 3.1 Android `:app:compileDebugKotlin` 통과
- [x] 3.2 iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과
- [ ] 3.3 실기기: 응원팀=홈 경기에서 박스스코어 첫 진입 시 홈 기록 기본 노출, 응원팀 미출전 경기는 어웨이 기본, 라벨 "OO팀 기록" 확인
