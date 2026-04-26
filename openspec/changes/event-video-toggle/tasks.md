## Tasks

### iOS
- [x] `SettingsScreen.swift`: `@AppStorage("event_video_enabled")` 토글 추가, `.onChange` 훅에서 `WatchThemeSyncManager.syncEventVideoEnabledToWatch` 호출
- [x] `BaseHapticApp.swift`: 기본값 등록(`register(defaults: ["event_video_enabled": true])`) + `.onAppear`에서 워치 초기 sync
- [x] `WatchThemeSyncManager.swift`: `syncEventVideoEnabledToWatch(enabled:)` 메서드 추가 (`type: "settings_update"`)
- [x] `WatchConnectivityManager.swift` (워치): `settings_update` 메시지 핸들러 추가, applicationContext 복원 시에도 처리, `UserDefaults`에 저장
- [x] `BaseHapticWatchApp.swift`: `@AppStorage("event_video_enabled")` 추가, `showTransition(for:)` 진입부에서 video event 5종 (VICTORY/HOMERUN/HIT/DOUBLE_PLAY/SCORE) early return, 경기 종료 자동 victory 트리거에도 가드 추가

### Android
- [x] `SettingsScreen.kt` (mobile): `event_video_enabled` SharedPreferences 토글 추가, 변경 시 `WearSettingsSyncManager.syncEventVideoEnabledToWatch` 호출
- [x] `MainActivity.kt` (mobile): `LaunchedEffect(Unit)`로 앱 진입 시 워치 초기 sync
- [x] `WearSettingsSyncManager.kt` (mobile): 신규 작성. `/settings/current` DataItem put
- [x] `DataLayerListenerService.kt` (watch): `PATH_SETTINGS`/`SETTINGS_PREFS_NAME`/`PREF_KEY_EVENT_VIDEO_ENABLED`/`ACTION_SETTINGS_UPDATED` 상수 추가, `handleSettingsUpdate(item)` 핸들러 + 분기 추가
- [x] `MainActivity.kt` (watch): `eventVideoEnabled` 상태 + `ACTION_SETTINGS_UPDATED` 브로드캐스트 수신 → 즉시 갱신, video token 부여하는 `LaunchedEffect(latestEvent?.timestamp)` 끝에서 가드, 경기 종료 자동 victory 분기에도 가드

### 검증
- [ ] iOS 모바일 빌드 (xcodebuild) 성공
- [ ] iOS 워치 빌드 성공
- [ ] Android 모바일 `:app:compileDebugKotlin` 성공
- [ ] Android Wear `:watch:app:compileDebugKotlin` 성공
- [ ] 실기기: 토글 OFF 후 모의 이벤트(워치 테스트 시뮬레이션) — 영상 차단, 햅틱은 정상
- [ ] 실기기: 토글 ON 복원 시 영상 다시 재생
