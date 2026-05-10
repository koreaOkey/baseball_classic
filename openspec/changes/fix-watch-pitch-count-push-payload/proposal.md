# fix-watch-pitch-count-push-payload

## Why

`add-watch-pitch-count` + `fix-crawler-pitch-count-key` 적용 후 production `/state` 는 정상 누적 투구수를 반환하지만, 사용자가 다음 회귀를 보고:

1. iPhone 백그라운드 → 워치 라이브 화면에서 투구수 영구 사라짐.
2. iPhone 포그라운드 → 워치에서 투구수가 나타났다 사라졌다 깜빡임.

원인은 **silent push payload 가 `pitcher_pitch_count` 키를 포함하지 않는 것**.

- 백엔드 `_send_push_for_game_events` (`backend/api/app/main.py:741-757`) 가 만드는 `base_payload` 에 `pitcher_pitch_count` 미포함.
- iOS `AppDelegate.didReceiveRemoteNotification` (`ios/mobile/BaseHaptic/AppDelegate.swift:71-93`) 은 모든 silent push 를 `sendGameDataToWatch(from:)` 로 워치에 forward.
- `userInfo["pitcher_pitch_count"] as? Int` → nil → `WatchGameSyncManager.sendGameData(... pitcherPitchCount: nil)` → wire 에서 sentinel `-1` 로 직렬화 → 워치 `WatchConnectivityManager` 가 `-1` 을 nil 로 디코딩 → UI 가드 `if let count, gameData.isLive` 실패 → 투구수 행이 사라짐.

**증상별 정합:**
- 백그라운드: WebSocket 끊김 → push 만 도달 → 투구수 누락 상태가 지속.
- 포그라운드: WebSocket 가 정확한 값을 push, AppDelegate silent push 가 nil 로 덮어씀 → 다음 WebSocket `.state` 도착 시 다시 복귀 → 깜빡임.

Android FCM 핸들러 `BaseHapticMessagingService.onMessageReceived` 는 시스템 알림만 띄우고 Wear 로 forward 하지 않으므로 영향 없음.

## What Changes

### Backend (root cause)

- `backend/api/app/main.py` `_send_push_for_game_events` `base_payload`
  - `"pitcher_pitch_count": state_payload.get("pitcherPitchCount") if not None else -1` 추가.
  - sentinel `-1` 은 iOS WatchGameSyncManager 와 동일 — null 표현.

### iOS phone (defensive fallback)

- `ios/mobile/BaseHaptic/WatchSync/WatchGameSyncManager.swift`
  - `cachedPitcherPitchCount(forGameId:)` 추가. UserDefaults `last_watch_game_data` 에서 동일 game_id 의 마지막 투구수를 읽음.

- `ios/mobile/BaseHaptic/AppDelegate.swift` `sendGameDataToWatch(from:)`
  - `userInfo["pitcher_pitch_count"]` 가 누락되거나 sentinel 인 경우 위 캐시값으로 폴백.
  - 이로써 백엔드 미배포 빌드/구버전 푸시 페이로드에서도 워치 UI 안정.

## Impact

- **백엔드**: `_send_push_for_game_events` 1줄 + 보조 변수 1개. silent push payload 크기 < 8B 증가.
- **iOS**: 신규 메서드 1개 + AppDelegate 폴백 블록. wire 호환성 유지 (sentinel 그대로).
- **Android**: 영향 없음.
- **위험도**: 낮음. nullable sentinel 규칙은 기존과 동일.

## Non-Goals

- iOS Watch decoder 변경 없음 (현행 sentinel 처리 유지).
- WebSocket `.state` / `.update` 경로는 이미 정상 (변경 불필요).
- Live Activity payload 변경 없음 (별도 경로).

## Rollout

1. iOS 빌드 배포 → 라이브 경기 중 백그라운드/포그라운드에서 투구수 유지 확인.
2. 백엔드 배포 → 새 push payload 확인 (`pitcher_pitch_count` 키 존재).
3. 두 배포 후 회귀 종결.

### Rollback

- `git revert` 가능. iOS 폴백은 백엔드 미배포 환경에서도 동작하므로 백엔드 롤백만 해도 무해 (원상복귀).
