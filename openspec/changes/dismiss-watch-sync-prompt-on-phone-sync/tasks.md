# Tasks

## 1. iOS Phone — dismiss 발송

- [x] 1.1 `WatchGameSyncManager.sendWatchSyncPromptDismiss()` — `watch_sync_prompt_dismiss` 메시지 (transferUserInfo 폴백)
- [x] 1.2 `BaseHapticApp` `.onChange(of: syncedGameId)` — 관람 시작 시 dismiss 1회 전송

## 2. watchOS — 팝업 해제

- [x] 2.1 `handleMessage` — `watch_sync_prompt_dismiss` 케이스: 어떤 경기든 응답 없이 `watchSyncPrompt = nil`
- [x] 2.2 `handleGameData` — 다른 경기 LIVE 데이터 도착 시 무응답 해제 (같은 경기 자동 수락은 유지)

## 3. Verification

- [x] 3.1 iOS mobile 스킴 빌드 통과 (iOS Simulator)
- [x] 3.2 watchOS 스킴 빌드 통과 (watchOS Simulator)
- [ ] 3.3 실기기: 응원팀 경기 LIVE 팝업 표시 중 → 폰에서 다른 경기 광고 관람+동기화 → 팝업 자동 해제 확인
- [ ] 3.4 실기기: 응원팀 경기 자체를 폰에서 동기화 → 기존 자동 수락 동작 회귀 없음 확인
- [ ] 3.5 후속: Android/Wear OS 동등성 change 생성
