# Tasks

## 코드 변경
- [x] iOS Watch: `ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift`
  - `handleGameData()`의 `normalizedInning` 계산 분기 추가 (rawOut >= 3 && !isFinished → advanceInningBeforeNinth)
  - `advanceInningBeforeNinth(_ inning: String) -> String` 정적 헬퍼 추가
- [x] Android Wear: `apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt`
  - `handleGameData()`의 `normalizedInning` 계산 when 분기 추가
  - `advanceInningBeforeNinth(inning: String): String` private 함수 추가

## 검증
- [ ] iOS 시뮬레이터: 더미 메시지로 `out=3, inning="3회초"` → "3회말" 표기
- [ ] iOS 시뮬레이터: `out=3, inning="3회말"` → "4회초" 표기
- [ ] iOS 시뮬레이터: `out=3, inning="9회말"` → "9회말" 유지
- [ ] iOS 시뮬레이터: `out=3, inning="경기 종료"` 또는 isFinished=true → "경기 종료" 표기
- [ ] Android Wear OS 에뮬레이터: 동일 케이스 검증
- [ ] 1~8회 정상 진행(out<3) 시 inning 변경 없음 확인 (회귀 없음)

## 후속 (별도 change)
- [ ] 백엔드 `_normalize_state_for_three_outs()`에 점수·연장룰 기반 종료 판정 추가 (Plan B)
  - 9회초 3out + home_score > away_score → "경기 종료"
  - 9회말 3out + home_score != away_score → "경기 종료"
  - KBO 정규시즌 연장 12회 무승부 룰 등
  - 메모리 `project_backend_end_of_game_judgment_todo.md` 참고
