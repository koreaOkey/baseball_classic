## Tasks

### iOS — Phone
- [x] `BaseHapticApp.swift`: `register(defaults: ["live_haptic_enabled": true])` + `.onAppear`에서 `WatchThemeSyncManager.syncLiveHapticEnabledToWatch` 호출
- [x] `BaseHapticApp.swift`: `streamSyncedGame()` 의 `.events` case 진입부에 `live_haptic_enabled` 가드 (false면 cursor만 갱신하고 햅틱 skip)
- [x] `BaseHapticApp.swift`: `.update` case 의 events 루프에 `liveHapticEnabled` 조건 + 종료 시 VICTORY 햅틱 가드
- [x] `BaseHapticApp.swift`: `streamGames()` 등 다른 VICTORY 발사 지점도 가드
- [x] `AppDelegate.swift`: `didReceiveRemoteNotification` 에서 워치로 햅틱 push 전 `live_haptic_enabled` 체크
- [x] `Screens/SettingsScreen.swift`: `@State` → `@AppStorage("live_haptic_enabled")`, `.onChange` 훅에서 `WatchThemeSyncManager.syncLiveHapticEnabledToWatch`
- [x] `WatchSync/WatchThemeSyncManager.swift`: `syncLiveHapticEnabledToWatch(enabled:)` 추가 (`type: "settings_update"`)

### iOS — Watch
- [x] `WatchSync/WatchConnectivityManager.swift`: `applicationContext` 복원 분기에 `live_haptic_enabled` UserDefaults 저장 추가
- [x] `WatchSync/WatchConnectivityManager.swift`: `handleSettingsUpdate` 에 `live_haptic_enabled` 분기 추가
- [x] `WatchSync/WatchConnectivityManager.swift`: `handleHapticEvent` 에 마스터 스위치 가드
- [x] `WatchSync/WatchConnectivityManager.swift`: `handleDirectPushHapticEvent` (워치 직접 APNs 경로)에 가드
- [x] `WatchSync/WatchConnectivityManager.swift`: `handleGameData` 인라인 이벤트 분기에 가드
- [x] `WatchSync/WatchConnectivityManager.swift`: `handleGameData` 진입부에 game_data 마스터 가드 (`handleDirectPushGameData` 자동 커버)

### Android — Phone
- [x] `ui/screens/SettingsScreen.kt`: `remember{}` → `SharedPreferences`(`live_haptic_enabled`) 영구 저장, 변경 시 `WearSettingsSyncManager.syncLiveHapticEnabledToWatch`
- [x] `wear/WearSettingsSyncManager.kt`: `syncLiveHapticEnabledToWatch(context, enabled)` 추가 + 공통 `putBool` 리팩토링
- [x] `MainActivity.kt`: `LaunchedEffect(Unit)` 워치 초기 sync 에 `live_haptic_enabled` 추가
- [x] `service/GameSyncForegroundService.kt`: `applyIncomingEvents` 햅틱 발사 루프에 `liveHapticEnabled` 가드
- [x] `service/GameSyncForegroundService.kt`: 경기 종료 VICTORY 햅틱 발사 분기에 가드
- [x] `wear/WearGameSyncManager.kt`: `sendGameData` 진입부 game_data 마스터 가드 (GameSyncForegroundService/WatchTestScreen 자동 커버)

### Android — Watch
- [x] `DataLayerListenerService.kt`: `PREF_KEY_LIVE_HAPTIC_ENABLED` 상수 추가
- [x] `DataLayerListenerService.kt`: `handleSettingsUpdate` 에 `live_haptic_enabled` 분기 추가
- [x] `DataLayerListenerService.kt`: `triggerHapticFeedback` 진입부에 마스터 스위치 가드 (DataLayer 햅틱 + game_data 인라인 이벤트 둘 다 커버)
- [x] `DataLayerListenerService.kt`: `handleGameData` 진입부에 game_data 마스터 가드 (UI 갱신·테마 동기화·tile 갱신 모두 차단)

### iOS — Phone (game_data 게이트)
- [x] `WatchSync/WatchGameSyncManager.swift`: `sendGameData` 진입부 game_data 마스터 가드 (BaseHapticApp `.update`/.events 푸시 + `AppDelegate.sendGameDataToWatch` 자동 커버)

### OFF→ON 즉시 복원 (last_watch_game_data 캐시 + resync API)
- [x] `WatchGameSyncManager.swift`(iOS): payload 빌드 → `cacheLastGameData(payload)` 항상 실행 → 게이트 평가 → `deliverGameData(payload)`. `resyncLastGameDataToWatch()` 노출.
- [x] `Screens/SettingsScreen.swift`(iOS): 토글 onChange ON 분기에서 `WatchGameSyncManager.shared.resyncLastGameDataToWatch()` 호출.
- [x] `wear/WearGameSyncManager.kt`(Android): `GameDataPayload` 데이터 클래스 추출, `cacheLastGameData(JSON)`/`readCachedGameData()` SharedPreferences `basehaptic_user_prefs` 키 `last_watch_game_data`. `resyncLastGameDataToWatch(context)` 노출.
- [x] `ui/screens/SettingsScreen.kt`(Android): 토글 onCheckedChange ON 분기에서 `WearGameSyncManager.resyncLastGameDataToWatch(context)` 호출.

### 검증
- [ ] iOS 모바일/워치 빌드 (xcodebuild) 성공
- [ ] Android `:app:compileDebugKotlin` + `:watch:app:compileDebugKotlin` 성공
- [ ] 실기기: 마스터 OFF → 모의/실 라이브 이벤트 모두 진동 없음 + 워치 화면 마지막 값으로 freeze (점수·이닝 갱신 안 됨)
- [ ] 실기기: 마스터 ON 복원 시 워치 점수·이닝 즉시 갱신(캐시 resync) + 다음 tick부터 햅틱 재개
- [ ] 실기기: 비라이브(경기 시작 전·종료 후)에서 OFF→ON 복원 시에도 캐시 resync로 워치 즉시 정합
- [ ] 실기기: 워치 직접 APNs(폰 잠금) 경로에서도 OFF 시 진동·갱신 차단 확인
- [ ] 실기기: ball/strike 토글이 마스터 ON 하위 필터로 정상 동작
