# Stadium Cheer + 내 팀 탭 — 활성화 절차

다크 머지로 들어간 코드를 실제 사용자에게 노출시키는 단계별 체크리스트.

## 0-pre. iOS 신규 파일 Xcode 등록 (선행 필수)

이번 다크 머지로 추가된 iOS 신규 파일들은 `project.pbxproj`에 참조가 없어 **Xcode에서 수동으로 타깃에 추가**해야 빌드에 포함된다.

**BaseHaptic 타깃에 추가:**
- `ios/mobile/BaseHaptic/Screens/MyTeamScreen.swift`
- `ios/mobile/BaseHaptic/Screens/TeamCheckinRankingView.swift`
- `ios/mobile/BaseHaptic/Screens/CheerCheckinCard.swift`
- `ios/mobile/BaseHaptic/Stadium/Stadiums.swift`
- `ios/mobile/BaseHaptic/Stadium/StadiumRegionMonitor.swift`
- `ios/mobile/BaseHaptic/Stadium/CheerSignalsLoader.swift`
- `ios/mobile/BaseHaptic/Models/StadiumCheerThemes.swift`

**BaseHapticWatch Watch App 타깃에 추가:**
- `ios/watch/BaseHapticWatch/Screens/StadiumCheerScreen.swift`

방법: Xcode → 프로젝트 네비게이터 우클릭 → Add Files to "BaseHaptic"... → 위 파일 선택 → "Copy items if needed: 끄기" / 타깃 체크 정확히.

## 0. 사전 점검

```bash
# 다크 상태에서 검색되어야 할 마커
rg "TODO\(stadium-cheer\)" --type-add 'kt:*.{kt,kts}' --type-add 'swift:*.swift' -t kt -t swift -t py -t sql
rg "TODO\(my-team-tab\)"    --type-add 'kt:*.{kt,kts}' --type-add 'swift:*.swift' -t kt -t swift
```

활성화 PR 머지 전에 위 두 grep 결과 **모두 사라져야** 정상.

## 1. DB 마이그레이션 적용

```bash
psql "$SUPABASE_URL" -f db/migrations/20260502_009_add_cheer_events_and_aggregates.sql
```

검증:
```sql
\d public.cheer_events
\d public.team_checkin_daily
\d public.team_checkin_season
```
RLS 정책 4개(`user_read_own_cheer_events`, `user_insert_own_cheer_events`, `public_read_team_checkin_daily`, `public_read_team_checkin_season`) 적용 확인.

## 2. 백엔드 라우트 활성화

`backend/api/app/main.py`에서 데코레이터 주석 해제:

```diff
-# @app.post("/cheer-events")  # TODO(stadium-cheer): 활성화 시 주석 해제
+@app.post("/cheer-events")
 def post_cheer_event(...):

-# @app.get("/rankings/teams")  # TODO(stadium-cheer): 활성화 시 주석 해제
+@app.get("/rankings/teams")
 def get_team_rankings(...):

-# @app.get("/cheer-events/me")  # TODO(stadium-cheer): 활성화 시 주석 해제
+@app.get("/cheer-events/me")
 def get_my_cheer_events(...):
```

활성화 시 `user_id` 쿼리 파라미터를 토큰(Authorization 헤더 추출)으로 대체하는 작업도 동시 진행. 다크 단계에서는 `user_id`를 직접 받지만 보안상 활성화 시 token-derived 형태로 변경 필요.

검증:
```bash
python3 -c "from app.main import app; print(len(app.routes))"  # 23 → 26 routes
```

추가 활성화 (나중 단계):
- `cheer_signals.json` 발행 잡 스케줄러 등록
- 검증 워커 스케줄러 등록 (좌표 재검증 / mock_location 차단 / 윈도우 외 invalid / 1일 1체크인)

## 3. iOS phone 활성화

### 3.1 `ios/mobile/BaseHaptic/BaseHapticApp.swift`

```diff
-private let SHOW_MY_TEAM_TAB = false
+private let SHOW_MY_TEAM_TAB = true
```

ContentView switch에서 `case .myTeam` 주석 해제:
```diff
-                // case .myTeam:
-                //     MyTeamScreen(selectedTeam: selectedTeam)
+                case .myTeam:
+                    MyTeamScreen(selectedTeam: selectedTeam)
```

### 3.2 권한 매니페스트 (`ios/mobile/BaseHaptic/Info.plist`)

```xml
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>경기장 진입 시 응원팀 팬들과 함께하는 시작 응원을 보내드립니다.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>경기장 진입 감지에 사용됩니다.</string>
```

### 3.3 region monitoring 호출

`ios/mobile/BaseHaptic/Stadium/StadiumRegionMonitor.swift`의 `start()` 함수 본문 주석 해제 + 호출부 추가 (예: `BaseHapticApp.init` 또는 onAppear).

### 3.4 응원 트리거 호출

cheer_signals.json 다운로드 → 응원 시각 도달 시 `WatchThemeSyncManager.sendCheerTrigger(...)` 호출 로직을 활성화 (스케줄러 컴포넌트 신규 추가 필요, design.md D6 참고).

### 3.5 iOS Watch (`WatchConnectivityManager.swift`)

```diff
             case "stadium_cheer_trigger":
-                // self.handleStadiumCheerTrigger(message)
-                break
+                self.handleStadiumCheerTrigger(message)
```

`StadiumCheerCoordinator.shared.current`를 `BaseHapticWatchApp` 진입 트리에서 overlay로 표시.

## 4. Android phone 활성화

### 4.1 `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`

```diff
-private const val SHOW_MY_TEAM_TAB = false
+private const val SHOW_MY_TEAM_TAB = true
```

### 4.2 권한 매니페스트 (`apps/mobile/app/src/main/AndroidManifest.xml`)

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 4.3 응원 트리거 호출

cheer_signals.json 로딩 + 응원 시각 도달 시 `WearGameSyncManager.sendCheerTrigger(...)` 호출 활성화.

### 4.4 Android Wear (`DataLayerListenerService.kt`)

```diff
                     path?.startsWith(PATH_SETTINGS) == true -> handleSettingsUpdate(item)
-                    // path?.startsWith(PATH_CHEER_TRIGGER) == true -> handleCheerTrigger(item)
+                    path?.startsWith(PATH_CHEER_TRIGGER) == true -> handleCheerTrigger(item)
```

`StadiumCheerOverlayCoordinator.current`를 MainActivity Compose 트리에 overlay로 mount.

## 5. 파일럿 게이트

활성화 직후 사고 차단을 위해 cheer_signals.json에 파일럿 팀(LG/두산) 경기만 포함:
- 잠실 두산 홈경기만 발행
- 잠실 LG 원정 경기만 발행
- 다른 구장은 빈 signals 배열로 발행 → 발화되지 않음

이상 패턴/오발화 모니터링 후 점진 확대.

## 6. 빌드·심사 체크리스트

- [ ] `./gradlew :mobile:assembleDebug :watch:assembleDebug` 통과
- [ ] iOS Xcode build 통과 (시뮬레이터 onboarding 테스트)
- [ ] 백엔드 `python3 -c "from app.main import app"` → 25 routes 확인
- [ ] App Store / Play Store 심사 제출 (위치 권한 신규 추가 사유 명시)
- [ ] 파일럿 팀 한정 cheer_signals.json 배포 후 24h 모니터링
- [ ] 정상 확인 후 9구장 전체 확대

## 7. 롤백

긴급 차단 필요 시:
1. `cheer_signals.json`을 빈 배열로 교체 → 전 사용자 발화 즉시 정지
2. 클라이언트 강제 업데이트 없이 차단 가능
3. 추가로 `SHOW_MY_TEAM_TAB=false` 핫픽스 필요 시 신규 빌드 배포

## 8. 활성화 후 주의

- `live_haptic_enabled`, `stadium_cheer_enabled` 마스터/서브 토글 게이트는 모든 발화 경로에서 유지
- raw 좌표 90일 익명화 잡 동작 확인 (`UPDATE cheer_events SET lat=NULL, lng=NULL WHERE created_at < now() - interval '90 days'`)
- 이상 패턴(동일 디바이스 다중 구장, mock_location, 윈도우 외 클릭) 일별 모니터링
- 응원 테마(`active_cheer_theme_id`)는 워치 페이스(`active_theme_id`)와 **별도 영속화 키**. 워치 → 풀스크린 응원 화면 렌더링 시 cheer 테마를 사용하도록 폰→워치 동기 페이로드에 포함 필요. WatchThemeSyncManager(iOS) / WearSettingsSyncManager(Android)에 cheer 테마 path 추가 (활성화 시점)
