# Tasks

## 1. 원인 진단
- [x] 1.1 알림 출처 특정: GameSyncForegroundService ongoing 알림 ("워치로 관람 중...")
- [x] 1.2 재현 경로 확인: pref `synced_game_id` 미정리 → 앱 실행 시 복원 → service 재시작 → 알림 반복
- [x] 1.3 iOS 동일 패턴 확인 (`savedGameId` 무검증 복원)

## 2. 구현
- [x] 2.1 Android `stopStreamingAndSelf()`: 자체 종료 시 `synced_game_id`/`active_live_score_game_id` pref 제거
- [x] 2.2 Android `loadSavedGameId`: YYYYMMDD prefix ≠ 오늘이면 폐기 + pref 정리
- [x] 2.3 iOS `savedGameId(forKey:)`: 동일 날짜 prefix 검증

## 3. 검증
- [x] 3.1 Android `:mobile:compileDebugKotlin` 통과
- [x] 3.2 iOS 시뮬레이터 빌드 성공
- [ ] 3.3 실기기 확인: 경기 종료 다음날 앱 실행 시 알림 미표시 + 당일 라이브 경기 워치 관람은 정상 복원
