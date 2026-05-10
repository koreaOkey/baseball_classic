# Tasks

- [x] `backend/api/app/main.py` `_send_push_for_game_events` `base_payload` 에 `pitcher_pitch_count` 추가 (sentinel -1)
- [x] `ios/mobile/BaseHaptic/WatchSync/WatchGameSyncManager.swift` `cachedPitcherPitchCount(forGameId:)` 추가
- [x] `ios/mobile/BaseHaptic/AppDelegate.swift` `sendGameDataToWatch(from:)` 에서 누락 시 캐시 폴백
- [ ] iOS Xcode 빌드 검증
- [ ] 백엔드 배포 → silent push 페이로드에 `pitcher_pitch_count` 포함 확인 (Railway logs 또는 디바이스 콘솔)
- [ ] 실기기: 라이브 경기에서 iPhone 백그라운드 시 워치 투구수 유지 확인
- [ ] 실기기: 라이브 경기에서 iPhone 포그라운드 시 깜빡임 사라짐 확인
