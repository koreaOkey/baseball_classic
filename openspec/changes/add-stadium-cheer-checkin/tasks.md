## 1. DB & Backend (다크 머지)

- [ ] 1.1 `db/migrations/YYYYMMDD_NNN_add_cheer_events_and_aggregates.sql` 작성 (cheer_events, team_checkin_daily, team_checkin_season + 인덱스 + RLS). 적용은 보류.
- [ ] 1.2 `backend/api/app/models.py`에 SQLAlchemy 모델 추가: `CheerEvent`, `TeamCheckinDaily`, `TeamCheckinSeason`
- [ ] 1.3 `backend/api/app/main.py`에 라우트 함수 추가하되 `@app.post`/`@app.get` 데코레이터를 `# TODO(stadium-cheer): 활성화 시 데코레이터 주석 해제`로 비활성화:
  - `POST /api/cheer-events`
  - `GET /api/rankings/teams?period=season|weekly`
- [ ] 1.4 `stadiums.json` / `cheer_signals.json` 정적 데이터 + 발행 잡 스텁 (`backend/api/app/cheer_signals.py`). 스케줄러 등록은 주석 처리.
- [ ] 1.5 검증 워커 함수 스텁 (`backend/api/app/workers/cheer_validator.py`). 스케줄러 등록은 주석 처리.

## 2. iOS Phone (다크 머지)

- [x] 2.1 `ios/mobile/BaseHaptic/BaseHapticApp.swift` `enum Screen`에 `case myTeam` 추가, `SHOW_MY_TEAM_TAB=false` 가드 + ContentView switch case 주석 처리
- [x] 2.2 `ios/mobile/BaseHaptic/Screens/MyTeamScreen.swift` 신규
- [x] 2.3 `ios/mobile/BaseHaptic/Screens/TeamCheckinRankingView.swift` 신규 (시즌/주간 토글, 10팀 mock, 내 팀 강조)
- [x] 2.4 `ios/mobile/BaseHaptic/Screens/CheerCheckinCard.swift` 신규 (호출부 없음)
- [x] 2.5 `ios/mobile/BaseHaptic/Stadium/StadiumRegionMonitor.swift` 신규 (`startMonitoring` 본문 주석)
- [x] 2.6 `ios/mobile/BaseHaptic/Stadium/CheerSignalsLoader.swift` 신규 (refresh fetch 주석)
- [x] 2.7 `WatchThemeSyncManager.sendCheerTrigger(...)` 메서드 추가 (호출부 없음)
- [x] 2.8 `ios/mobile/BaseHaptic/Stadium/Stadiums.swift` 신규 — KBO 9구장 좌표/반경/홈팀 정적 데이터
- [ ] 2.9 SettingsScreen에 `stadium_cheer_enabled` `@AppStorage` 토글 (활성화 단계에서 추가)
- [ ] 2.10 권한 매니페스트(`Info.plist`)는 활성화 시점에 추가

## 3. iOS Watch (다크 머지)

- [x] 3.1 `ios/watch/BaseHapticWatch/Screens/StadiumCheerScreen.swift` 신규 (StadiumCheerPayload + Coordinator + Screen)
- [x] 3.2 `WatchConnectivityManager`에 `stadium_cheer_trigger` case 추가, dispatch는 주석 처리

## 4. Android Phone (다크 머지)

- [ ] 4.1 `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`:
  - `Screen.MyTeam` sealed object 추가
  - `private const val SHOW_MY_TEAM_TAB = false` 추가 (기존 SHOW_COMMUNITY_TAB 패턴)
  - `BottomNavigationBar`에 4번째 BottomNavItem 추가 (`if (SHOW_MY_TEAM_TAB)` 가드)
  - `BaseHapticApp` when 분기에 `Screen.MyTeam -> MyTeamScreen(...)` 추가
- [ ] 4.2 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/MyTeamScreen.kt` 신규 — 컨테이너 + `TeamCheckinRankingScreen` 임베드
- [ ] 4.3 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/TeamCheckinRankingScreen.kt` 신규 — 시즌/주간 토글 + LazyColumn (mock 데이터)
- [ ] 4.4 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/CheerCheckinCard.kt` 신규 — 홈 화면 임베드용 카드 (호출부 가드)
- [ ] 4.5 `apps/mobile/app/src/main/java/com/basehaptic/mobile/stadium/StadiumGeofenceManager.kt` 신규 — GeofencingClient 래퍼. `addGeofences` 호출부 주석 처리.
- [ ] 4.6 `apps/mobile/app/src/main/java/com/basehaptic/mobile/stadium/CheerSignalsLoader.kt` 신규 — cheer_signals.json 다운로드/캐시.
- [ ] 4.7 `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WearGameSyncManager.kt`에 `sendCheerTrigger(payload:)` 추가.
- [ ] 4.8 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt`에 `stadium_cheer_enabled` SharedPreferences 토글 추가하되 UI는 `// TODO(stadium-cheer):` 주석 처리.
- [ ] 4.9 `apps/mobile/app/src/main/res/values/strings.xml`에 `tab_my_team`, `cheer_checkin_card_title` 등 문자열 리소스 추가.
- [ ] 4.10 `AndroidManifest.xml`은 변경하지 않음 — 활성화 시점에 `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS` 추가.

## 5. Android Wear OS (다크 머지)

- [x] 5.1 `apps/watch/app/src/main/java/com/basehaptic/watch/ui/StadiumCheerScreen.kt` 신규 (Payload + Coordinator + Screen, Vibrator 패턴 포함)
- [x] 5.2 `DataLayerListenerService`에 `PATH_CHEER_TRIGGER` 추가 + `handleCheerTrigger` 메서드 추가 (when 분기 주석 처리)
- [x] 5.3 `WearGameSyncManager.sendCheerTrigger(...)` 추가 + `PATH_CHEER_TRIGGER` 상수

## 6. 검증 & 정리

- [x] 6.1 `openspec validate add-stadium-cheer-checkin --strict` 통과
- [x] 6.2 Android 앱 빌드 통과 (`./gradlew :mobile:assembleDebug :watch:assembleDebug`)
- [ ] 6.3 iOS 앱 빌드 통과 (Xcode build) — 사용자 환경에서 별도 검증
- [x] 6.4 백엔드 import 통과 (`python3 -c "from app.main import app"`) — 23 routes 확인 (cheer-events 미등록)
- [x] 6.5 활성화 절차 README 작성 ([ACTIVATION.md](ACTIVATION.md))

## 7. Out of scope (이번 change 미포함)

- 이벤트별 응원 (홈런/세이브)
- 실물 경품 시상
- 디바이스 단일성 정밀 차단
- 우천 취소 silent push
- `내 팀` 탭의 팀별 뉴스 화면 구현 (별도 change)
