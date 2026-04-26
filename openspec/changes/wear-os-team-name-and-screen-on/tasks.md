## 1. 팀명 한글 마스코트명 표시

- [x] 1.1 LiveGameScreen.kt의 ScoreSide에서 `team.uppercase()` → `displayTeamName(team)` 변경
- [x] 1.2 `displayTeamName` import 추가

## 2. 경기 중 화면 꺼짐 방지

- [x] 2.1 MainActivity.kt에 `WindowManager` import 추가
- [x] 2.2 WatchApp 컴포저블에 `LaunchedEffect(gameData?.isLive)`로 `FLAG_KEEP_SCREEN_ON` 조건부 설정/해제

## 3. 홈런 이벤트 우선순위

- [x] 3.1 iOS: HOMERUN 도착 시 하위 영상(HIT/DOUBLE_PLAY/SCORE) 중단 후 홈런 영상 교체
- [x] 3.2 Android: 동일 로직 적용

## 4. 중립 경기 영상 이벤트 활성화

- [x] 4.1 iOS: isNeutralGame 조건 추가 — 내 팀 미포함 시 양 팀 모든 이벤트에 영상 표시
- [x] 4.2 Android: 동일 로직 적용
