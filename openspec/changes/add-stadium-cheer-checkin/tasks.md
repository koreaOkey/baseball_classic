## 1. DB & Backend (iOS 실테스트 활성화 반영)

- [x] 1.1 `db/migrations/20260502_009_add_cheer_events_and_aggregates.sql` 작성 및 Supabase 적용 (cheer_events, team_checkin_daily, team_checkin_season + 인덱스 + RLS). 기존 ORM 생성 컬럼 보정을 위해 `cheer_events.user_id`를 uuid로 변환 후 적용.
- [x] 1.2 `backend/api/app/models.py`에 SQLAlchemy 모델 추가: `CheerEvent`, `TeamCheckinDaily`, `TeamCheckinSeason`
- [x] 1.3 `backend/api/app/main.py`에 경기장 응원 라우트 활성화:
  - `GET /stadiums`
  - `GET /cheer-signals`
  - `POST /cheer-events`
  - `POST /cheer-events/validate-pending`
  - `GET /rankings/teams?period=season|weekly`
  - `GET /cheer-events/me`
- [x] 1.4 `backend/api/app/cheer_signals.py` 추가: KBO 구장 payload와 오늘 경기 기반 cheer signal 응답 생성.
- [x] 1.5 `backend/api/app/workers/cheer_validator.py` 추가 및 `POST /cheer-events` background validation 연결.

## 2. iOS Phone (실테스트 활성화)

- [x] 2.1 `ios/mobile/BaseHaptic/BaseHapticApp.swift` `enum Screen`에 `case myTeam` 추가, `SHOW_MY_TEAM_TAB=true`로 활성화, ContentView switch case 연결
- [x] 2.2 `ios/mobile/BaseHaptic/Screens/MyTeamScreen.swift` 신규
- [x] 2.3 `ios/mobile/BaseHaptic/Screens/TeamCheckinRankingView.swift` 신규 (주간/시즌 토글, 백엔드 랭킹 fetch + fallback, 내 팀 강조)
- [x] 2.4 `ios/mobile/BaseHaptic/Screens/CheerCheckinCard.swift` 신규 및 홈 화면 체크인 카드 호출부 연결
- [x] 2.5 `ios/mobile/BaseHaptic/Stadium/StadiumRegionMonitor.swift` 신규 및 region monitoring 시작 경로 연결
- [x] 2.6 `ios/mobile/BaseHaptic/Stadium/CheerSignalsLoader.swift` 신규 및 `/cheer-signals`, `/rankings/teams`, `/cheer-events` 연동 활성화
- [x] 2.7 `WatchThemeSyncManager.sendCheerTrigger(...)` 메서드 추가 및 응원 시각 스케줄러 호출부 연결
- [x] 2.8 `ios/mobile/BaseHaptic/Stadium/Stadiums.swift` 신규 — KBO 9구장 좌표/반경/홈팀 정적 데이터
- [x] 2.9 SettingsScreen에 `stadium_cheer_enabled` `@AppStorage` 토글 (활성화 단계에서 추가)
- [x] 2.10 권한 매니페스트(`Info.plist`)에 위치 권한 문구와 `location` background mode 추가
- [x] 2.11 `ios/mobile/BaseHaptic/Screens/WatchTestScreen.swift`에 현장 응원 풀스크린 테스트 버튼 추가
- [x] 2.12 `ios/mobile/BaseHaptic/Screens/MyTeamScreen.swift`를 체크인 허브 UI로 개편 — 헤더, 경기장 체크인 카드, 워치 응원 팝업, 랭킹 카드 구성. `SHOW_MY_TEAM_TAB=true`로 탭 노출.
- [x] 2.13 iOS 경기장 체크인 fallback 표시를 홈 화면 주 진입점이 아니라 `내 팀` 탭 상태로 연결.
- [x] 2.14 iOS 경기장 체크인 카드에 히어로 이미지, 전체 폭 `체크인하기` 버튼, `위치 확인 완료` 상태 컴포넌트 추가.
- [x] 2.15 iOS `워치 응원` 버튼 클릭 시 워치 응원 안내가 팝업으로 표시되도록 변경.
- [x] 2.16 iOS `구장 체크인`, `워치 응원`, `랭킹` 버튼 클릭 시 각 기능 설명 팝업 표시. 실제 위치 상태 연동 전까지 체크인 가능 UI를 기본 표시.
- [x] 2.17 iOS `위치 확인 완료` 상태 색상을 선택 팀과 무관한 성공 초록색으로 고정.
- [x] 2.18 iOS 체크인 히어로 이미지 오른쪽 상단에 오늘 응원팀 경기 구장명과 지역 오버레이 표시.
- [x] 2.19 iOS 체크인 구장 오버레이를 선택팀 홈구장이 아니라 오늘 경기 데이터의 홈팀 구장 기준으로 표시.
- [x] 2.20 iOS 위치 실테스트를 위해 `INCHEON`/SSG 구장 좌표와 표시 지역을 서울 중구 세종대로 67 근처로 임시 변경. 테스트 종료 후 원복 필요.
- [x] 2.21 iOS `체크인하기` 버튼 탭 시 같은 카드가 체크인 완료 상태로 전환되도록 로컬 UI 상태 추가.
- [x] 2.22 iOS 체크인 완료 상태를 하루 단위로 저장하고, 앱 재실행 시 당일 완료 상태·주간 랭킹 보정·내 직관 기록 수치가 유지되도록 반영.
- [x] 2.23 iOS 현재 위치가 오늘 응원팀 경기 구장 500m 밖이면 위치 상태를 `현재 경기장 장소가 아닙니다`로 표시하고, 체크인 시 `<응원팀> 경기 구장이 아닙니다.` 팝업으로 차단.

## 3. iOS Watch (실테스트 활성화)

- [x] 3.1 `ios/watch/BaseHapticWatch/Screens/StadiumCheerScreen.swift` 신규 (StadiumCheerPayload + Coordinator + Screen)
- [x] 3.2 `WatchConnectivityManager`에 `stadium_cheer_trigger` case 추가 및 dispatch 활성화, watch app overlay 연결
- [x] 3.3 현장 응원 풀스크린 표시 시 홈런 이벤트처럼 강한 3회 탭틱 패턴 재생

## 4. Android Phone (다크 머지)

- [x] 4.1 `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt`:
  - `Screen.MyTeam` sealed object 추가
  - `private const val SHOW_MY_TEAM_TAB = true` 추가 (기존 SHOW_COMMUNITY_TAB 패턴)
  - `BottomNavigationBar`에 4번째 BottomNavItem 추가 (`if (SHOW_MY_TEAM_TAB)` 가드)
  - `BaseHapticApp` when 분기에 `Screen.MyTeam -> MyTeamScreen(...)` 추가
- [x] 4.2 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/MyTeamScreen.kt` 신규 — 컨테이너 + `TeamCheckinRankingScreen` 임베드
- [x] 4.3 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/TeamCheckinRankingScreen.kt` 신규 — 주간/시즌 토글 + LazyColumn
- [x] 4.4 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/CheerCheckinCard.kt` 신규 — 홈 화면 임베드용 카드 (호출부 가드)
- [x] 4.5 `apps/mobile/app/src/main/java/com/basehaptic/mobile/stadium/StadiumGeofenceManager.kt` 신규 — GeofencingClient 래퍼. `addGeofences` 호출부 주석 처리.
- [x] 4.6 `apps/mobile/app/src/main/java/com/basehaptic/mobile/stadium/CheerSignalsLoader.kt` 신규 — cheer_signals.json 다운로드/캐시.
- [x] 4.7 `apps/mobile/app/src/main/java/com/basehaptic/mobile/wear/WearGameSyncManager.kt`에 `sendCheerTrigger(payload:)` 추가.
- [x] 4.8 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt`에 `stadium_cheer_enabled` SharedPreferences 토글 추가하되 UI는 `// TODO(stadium-cheer):` 주석 처리.
- [x] 4.9 `apps/mobile/app/src/main/res/values/strings.xml`에 `tab_my_team`, `cheer_checkin_card_title` 등 문자열 리소스 추가.
- [x] 4.10 `AndroidManifest.xml`은 변경하지 않음 — 활성화 시점에 `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS` 추가.
- [x] 4.11 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/WatchTestScreen.kt`에 현장 응원 풀스크린 테스트 버튼 추가.
- [x] 4.12 `apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/MyTeamScreen.kt`를 체크인 허브 UI로 개편 — 헤더, 경기장 체크인 카드, 워치 응원 팝업, 랭킹 카드 구성. `SHOW_MY_TEAM_TAB=true`로 탭 노출.
- [x] 4.13 `openspec/changes/add-stadium-cheer-checkin/specs/my-team-tab/spec.md`에 `내 팀` 탭이 경기장 체크인의 메인 진입점이라는 UI 요구사항 추가.
- [x] 4.14 Android 경기장 체크인 카드에 히어로 이미지, 전체 폭 `체크인하기` 버튼, `위치 확인 완료` 상태 컴포넌트 추가.
- [x] 4.15 Android `워치 응원` 버튼 클릭 시 워치 응원 안내가 팝업으로 표시되도록 변경.
- [x] 4.16 Android `구장 체크인`, `워치 응원`, `랭킹` 버튼 클릭 시 각 기능 설명 팝업 표시. 실제 위치 상태 연동 전까지 체크인 가능 UI를 기본 표시.
- [x] 4.17 Android `위치 확인 완료` 상태 색상을 선택 팀과 무관한 성공 초록색으로 고정.
- [x] 4.18 Android 체크인 히어로 이미지 오른쪽 상단에 오늘 응원팀 경기 구장명과 지역 오버레이 표시.
- [x] 4.19 Android 체크인 구장 오버레이를 선택팀 홈구장이 아니라 오늘 경기 데이터의 홈팀 구장 기준으로 표시.
- [x] 4.20 위치 실테스트를 위해 `INCHEON`/SSG 구장 좌표와 표시 지역을 서울 중구 세종대로 67 근처로 임시 변경. 테스트 종료 후 원복 필요.
- [x] 4.21 Android `체크인하기` 버튼 탭 시 같은 카드가 체크인 완료 상태로 전환되도록 로컬 UI 상태 추가.
- [x] 4.22 Android 체크인 완료 상태를 하루 단위로 저장하고, 앱 재실행 시 당일 완료 상태·주간 랭킹 보정·내 직관 기록 수치가 유지되도록 반영. 로그인 세션이 있으면 같은 액션에서 `/cheer-events` 서버 체크인도 전송.
- [x] 4.23 Android 현재 위치가 오늘 응원팀 경기 구장 500m 밖이면 위치 상태를 `현재 경기장 장소가 아닙니다`로 표시하고, 체크인 시 `<응원팀> 경기 구장이 아닙니다.` 팝업으로 차단. 위치 판정을 위해 foreground 위치 권한 선언 추가.

## 5. Android Wear OS (다크 머지)

- [x] 5.1 `apps/watch/app/src/main/java/com/basehaptic/watch/ui/StadiumCheerScreen.kt` 신규 (Payload + Coordinator + Screen, Vibrator 패턴 포함)
- [x] 5.2 `DataLayerListenerService`에 `PATH_CHEER_TRIGGER` 추가 + `handleCheerTrigger` 메서드 추가 및 when 분기 활성화
- [x] 5.3 `WearGameSyncManager.sendCheerTrigger(...)` 추가 + `PATH_CHEER_TRIGGER` 상수
- [x] 5.4 Wear OS root UI에 현장 응원 overlay mount
- [x] 5.5 현장 응원 풀스크린 표시 시 홈런 이벤트 수준의 강한 진동 패턴 유지

## 6. 검증 & 정리

- [x] 6.1 `openspec validate add-stadium-cheer-checkin --strict` 통과
- [x] 6.2 Android 앱 빌드 통과 (`./gradlew :mobile:assembleDebug :watch:assembleDebug`)
- [x] 6.3 iOS 앱 빌드 통과 (`xcodebuild -project BaseHaptic.xcodeproj -scheme BaseHaptic -configuration Debug -destination 'generic/platform=iOS Simulator' build`)
- [x] 6.4 백엔드 import 통과 (`python3 -c "from app.main import app"`) — 29 routes 확인, 경기장 응원 라우트 등록 확인
- [x] 6.5 활성화 상태/절차 문서 갱신 ([ACTIVATION.md](ACTIVATION.md))
- [x] 6.6 Supabase DB 마이그레이션 적용 및 테이블/RLS 정책 검증

## 7. Out of scope (이번 change 미포함)

- 이벤트별 응원 (홈런/세이브)
- 실물 경품 시상
- 디바이스 단일성 정밀 차단
- 우천 취소 silent push
- `내 팀` 탭의 팀별 뉴스 화면 구현 (별도 change)
