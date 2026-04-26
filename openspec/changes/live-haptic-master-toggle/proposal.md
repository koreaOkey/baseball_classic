## Why

설정 화면 "알림" 섹션의 **"경기 라이브 알림"** 토글이 UI만 있고 어떤 알림/햅틱/푸시 경로에도 연결되어 있지 않은 더미 상태였다 (iOS는 `@State`, Android는 `remember`로 메모리 전용 — 앱 재시작 시 초기화). 부제 "실시간 경기 내용을 워치로 알림 받기"가 의도한 **워치 라이브 동기화 전체 마스터 스위치**로 동작하도록 구현한다.

## What Changes

- iOS/Android 양쪽에서 토글 값을 영구 저장 (키: `live_haptic_enabled`, 기본 ON).
- 모바일 → 워치 동기화 (iOS `WCSession`, Android Wearable DataClient `/settings/current`).
- **OFF 시 워치 라이브 동기화 전체 차단** (햅틱 + 게임 데이터):
  - 햅틱 게이트:
    - iOS phone: `BaseHapticApp.swift` 의 `.events`/`.update` 햅틱 발사, VICTORY 햅틱, `AppDelegate` 의 APNs 햅틱 전달.
    - iOS watch: `WatchConnectivityManager` 의 WCSession 햅틱 핸들러, 인라인 이벤트, 직접 APNs 햅틱.
    - Android phone: `GameSyncForegroundService` 의 `applyIncomingEvents` 햅틱 발사, VICTORY 햅틱.
    - Android watch: `DataLayerListenerService.triggerHapticFeedback` 진입부.
  - **게임 데이터(점수·이닝·BSO·주자·투수/타자) 게이트**:
    - iOS phone: `WatchGameSyncManager.sendGameData` 진입부 (BaseHapticApp/AppDelegate 모든 발사 지점 자동 커버).
    - Android phone: `WearGameSyncManager.sendGameData` 진입부 (GameSyncForegroundService/WatchTestScreen 자동 커버).
    - iOS watch: `WatchConnectivityManager.handleGameData` 진입부 (WCSession + APNs `handleDirectPushGameData` 자동 커버).
    - Android watch: `DataLayerListenerService.handleGameData` 진입부.
- 앱 진입 시 모바일이 현재 토글 값을 워치에 push (페어링 직후/재설치 정합성).
- OFF 동안 워치는 마지막 알려진 게임 화면을 **freeze** — UserDefaults/SharedPreferences/Published var 모두 마지막 값 유지.
- **OFF→ON 즉시 복원**: phone 측 매니저(`WatchGameSyncManager`/`WearGameSyncManager`)가 모든 `sendGameData` 호출 직전에 payload를 로컬 캐시(`last_watch_game_data`)에 mirror 저장. 게이트는 캐시 write 후에 평가되므로 OFF 중에도 캐시는 phone-side 백엔드 스트림으로 계속 최신 상태 유지. 토글 onChange가 ON 으로 바뀌는 순간 settings_update push + `resyncLastGameDataToWatch()` 호출하여 캐시된 마지막 state를 즉시 워치에 1회 push. 라이브/비라이브 모두 ms 단위로 워치 화면 정합.

## Capabilities

### New Capabilities
- `live-haptic-master-toggle`: 워치 라이브 동기화 전체를 끄고 켜는 마스터 스위치. OFF 시 햅틱·점수·이닝·BSO 등 모든 워치 라이브 갱신을 차단(워치는 마지막 표시 freeze). ON 시 다음 스트림 tick에서 자동 재개.

### Modified Capabilities
- None

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| OFF 직후 phone↔watch 동기화 지연으로 햅틱·게임 데이터가 한 번 더 발사 | phone+watch 양쪽에서 게이트하므로 동기화가 늦어도 watch 측 진입부에서 차단. |
| 워치 직접 APNs 경로(워치 독립 sync)에서 OFF 후 첫 이벤트가 옛 prefs로 트리거 | 모바일 토글 변경 시 즉시 워치로 동기화. 또한 워치 앱 부팅 시 `applicationContext` 로 복원. 잠깐의 잔여 햅틱·갱신은 허용. |
| `ball_strike_haptic_enabled` 와 의미가 겹쳐 보임 | 마스터 스위치(`live_haptic_enabled`)는 전체, ball/strike는 그 하위 필터. UI 순서는 마스터가 위. |
| WatchTest 시뮬레이션도 차단됨 | 의도된 동작 — 마스터 OFF 시 일관된 차단. 테스트하려면 마스터 ON 필요. |
| OFF→ON 시 워치가 즉시 갱신되지 않음 | phone 매니저가 매 tick `last_watch_game_data` 캐시를 mirror 저장. 토글 onChange ON 분기에서 `resyncLastGameDataToWatch()` 호출하여 캐시 1회 push. 비라이브 상태에서도 즉시 워치 정합. |
| OFF 동안 워치 마지막 화면이 stale 표시 | 의도된 freeze. 사용자가 OFF 한 의식적 선택으로 간주. UI에 "동기화 일시 중지" 인디케이터는 추후 개선 항목. |

## Status

- [x] 구현 완료
- [ ] 빌드 검증 (iOS xcodebuild + Android compileDebugKotlin)
- [ ] 실기기 검증 (마스터 OFF → 라이브 햅틱 차단, ON 복원 → 즉시 재개)
- [ ] 커밋 및 푸시
