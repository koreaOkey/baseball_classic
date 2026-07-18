## Why

안드로이드에서 앱을 켤 때마다 "야구봄 — {팀} vs {팀} 경기 워치로 관람 중..." 알림이 반복 표시됐다. 워치 관람을 켜면 `synced_game_id` 가 SharedPreferences 에 저장되는데(프로세스 킬 대비 복원용), **경기가 끝나도 이 값을 지우는 곳이 없다**. GameSyncForegroundService 는 경기 종료 시 스스로 꺼지지만 pref 는 남아, 다음 앱 실행 때 MainActivity 가 지난 경기 ID 를 복원 → service 재시작 → 관람 중 알림 표시 → 비-LIVE 3회 관측 후 자체 종료 → pref 는 여전히 잔존 → 매 실행마다 반복. iOS 도 `savedGameId(forKey:)` 가 검증 없이 복원하는 동일 패턴(종료된 경기 스트리밍 재개)이었다.

## What Changes

### A. Android — 서비스 자체 종료 시 관람 상태 정리 (`GameSyncForegroundService.kt`)
- `stopStreamingAndSelf()` 에서 `synced_game_id` + `active_live_score_game_id` pref 제거. 경기 종료·최대 가동 시간 초과 등 어떤 자체 종료 경로에서도 다음 실행에 관람 상태가 되살아나지 않는다.

### B. Android — 복원 시 날짜 검증 (`MainActivity.kt` `loadSavedGameId`)
- 게임 ID 의 `YYYYMMDD` prefix 가 오늘이 아니면 pref 를 지우고 null 반환. 네트워크 없이 즉시 판정되는 안전망 — 이미 stale 값이 저장돼 있는 기존 사용자도 업데이트 후 첫 실행부터 해결.

### C. iOS — 동일 날짜 검증 (`BaseHapticApp.swift` `savedGameId(forKey:)`)
- 같은 prefix 검증으로 지난 경기 `synced_game_id` / `active_live_activity_game_id` 복원 차단.

## Capabilities

### Modified Capabilities
- `mobile-android`: 워치 관람/잠금화면 라이브 스코어 상태는 해당 경기 당일에만 복원되어야 하며, 경기 종료로 동기화 service 가 스스로 종료되면 저장된 관람 상태도 함께 정리되어야 한다.
- `mobile-ios`: 워치 관람/Live Activity 상태는 해당 경기 당일에만 복원되어야 한다.

## Impact

- 증상 해소: 앱 실행 시 지난 경기 "워치로 관람 중..." ongoing 알림 반복 표시 제거. 워치로 보기를 켠 경우에만 알림 표시(원래 의도).
- Android: MainActivity.kt + GameSyncForegroundService.kt — `:mobile:compileDebugKotlin` 통과.
- iOS: BaseHapticApp.swift — 시뮬레이터 빌드 성공.
- 부작용 범위: 자정 넘긴 경기(KBO 에선 사실상 없음)는 재실행 시 관람 상태가 복원되지 않음 — 토글 재설정으로 복구 가능, 허용.
