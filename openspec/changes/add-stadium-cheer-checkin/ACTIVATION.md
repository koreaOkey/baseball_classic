# Stadium Cheer + 내 팀 탭 — 활성화 상태

이 문서는 `add-stadium-cheer-checkin` change의 현재 활성화 상태와 남은 실테스트 작업을 기록한다.

## 1. 현재 적용 완료

### DB / Supabase

- `db/migrations/20260502_009_add_cheer_events_and_aggregates.sql` 적용 완료
- `public.cheer_events`, `public.team_checkin_daily`, `public.team_checkin_season` 생성 확인
- `cheer_events.user_id`는 기존 ORM 생성 이력으로 varchar였던 상태를 uuid로 보정
- RLS 정책 확인:
  - `user_insert_own_cheer_events`
  - `user_read_own_cheer_events`
  - `public_read_team_checkin_daily`
  - `public_read_team_checkin_season`

### Backend

`backend/api/app/main.py` 기준 경기장 응원 라우트가 실제 등록되어 있다.

- `GET /stadiums`
- `GET /cheer-signals`
- `POST /cheer-events`
- `POST /cheer-events/validate-pending`
- `GET /rankings/teams`
- `GET /cheer-events/me`

`POST /cheer-events`와 `GET /cheer-events/me`는 `Authorization: Bearer ...` 기반 사용자 식별 경로를 사용한다.

### iOS Phone

- `SHOW_MY_TEAM_TAB=true`로 `내 팀` 탭 사용자 노출 활성화
- `MyTeamScreen`은 체크인 허브 UI로 개편됨
  - 상단 `내 팀` 헤더
  - 경기장 체크인 카드
  - `구장 체크인` / `워치 응원` / `랭킹` 버튼 클릭 시 표시되는 기능 설명 팝업
  - 팀 체크인 랭킹 카드
  - 개인 직관 기록 요약 카드
- `pendingCheckinStadium`은 홈 화면 카드가 아니라 `MyTeamScreen` 상태로 전달됨
- `CheerCheckinCard`는 상태별 UI(`체크인하기`, 완료, 권한 필요, 경기 없음, 숨김 상태)를 지원함. 실제 위치/경기 상태 연동 전까지 `내 팀` 탭에서는 체크인 가능 UI를 기본 표시함
- `StadiumRegionMonitor.start()` 경로 활성화
- `CheerSignalsLoader`에서 `/cheer-signals`, `/rankings/teams`, `/cheer-events` 연동 활성화
- `WatchThemeSyncManager.sendCheerTrigger(...)` 호출부 연결
- `SettingsScreen`의 `stadium_cheer_enabled` 토글은 `hide-stadium-cheer-features` 변경으로 사용자 노출 제거됨. UserDefaults 키와 게이트 로직은 유지
- `Info.plist`에 위치 권한 문구와 `location` background mode 추가
- `xcodegen generate`로 신규 Swift 파일을 Xcode project에 반영

### iOS Watch

- `StadiumCheerScreen` 추가
- `WatchConnectivityManager`의 `stadium_cheer_trigger` dispatch 활성화
- watch app root에 풀스크린 응원 overlay 연결

## 2. 아직 남은 Android 적용

### Android Phone

- `SHOW_MY_TEAM_TAB=true`로 `내 팀` 탭 사용자 노출 활성화
- `MyTeamScreen`은 체크인 허브 UI로 개편됨
  - 상단 `내 팀` 헤더
  - 경기장 체크인 카드
  - `구장 체크인` / `워치 응원` / `랭킹` 버튼 클릭 시 표시되는 기능 설명 팝업
  - 팀 체크인 랭킹 카드
  - 개인 직관 기록 요약 카드
- `TeamCheckinRankingScreen`은 `/rankings/teams?period=weekly|season` 조회 후 fallback 팀 목록으로 보강
- `CheerCheckinCard` 추가됨. 현재 Android는 `내 팀` 탭 내부 카드로 사용하며 홈 화면 주 진입점으로 쓰지 않음
- `CheerSignalsLoader` 추가됨
  - `GET /stadiums`
  - `GET /cheer-signals`
  - `GET /rankings/teams`
  - `POST /cheer-events`
  - 실패 시 캐시 또는 fallback UI 유지
- `StadiumGeofenceManager` 추가됨. 현재 `startMonitoringDark(...)`는 실제 `addGeofences(...)`를 호출하지 않는 다크 래퍼
- `SettingsScreen`에 `stadium_cheer_enabled` 토글 경로 추가됨. `SHOW_STADIUM_CHEER_TOGGLE=false`로 UI 미노출 유지
- `strings.xml`에 `tab_my_team`, `cheer_checkin_card_title`, `cheer_checkin_card_cta`, `cheer_checkin_watch_preview_title`, `team_checkin_ranking_title` 추가됨
- `build.gradle.kts`에 `play-services-location` 추가됨
- `AndroidManifest.xml`은 이번 작업에서 변경하지 않음
- 남은 활성화 작업:
  - `내 팀` 탭 시각 QA
  - 실제 위치/경기 상태와 체크인 카드 상태 연결
  - 실제 geofence 등록 호출 활성화
  - Android 위치 권한 매니페스트 및 런타임 권한 플로우 추가
  - 설정 토글 노출 여부 결정 후 `SHOW_STADIUM_CHEER_TOGGLE=true` 전환

### Android Wear OS

- `StadiumCheerScreen`, `PATH_CHEER_TRIGGER`, `handleCheerTrigger`는 추가됨
- 남은 작업:
  - `DataLayerListenerService` when 분기에서 `PATH_CHEER_TRIGGER` dispatch 활성화
  - watch Compose root에 응원 overlay mount
  - 실기기 진동/화면 dismiss 테스트

## 3. 현재 검증 결과

```bash
python3 -m compileall backend/api/app
```

통과.

```bash
cd backend/api
python3 -c "from app.main import app; print(len(app.routes)); print([r.path for r in app.routes if 'cheer' in r.path or 'ranking' in r.path or 'stadium' in r.path])"
```

통과. route count는 29이며 경기장 응원 라우트가 등록되어 있다.

```bash
cd ios
xcodebuild -list -project BaseHaptic.xcodeproj
xcodebuild -project BaseHaptic.xcodeproj -scheme BaseHaptic -configuration Debug -destination 'generic/platform=iOS Simulator' build
```

통과.

```bash
openspec validate add-stadium-cheer-checkin --strict
```

통과.

```bash
./gradlew :mobile:compileDebugKotlin
```

통과. Android `내 팀` 체크인 허브 UI, 경기장 응원 로더, geofence 다크 래퍼, 설정 토글 다크 경로가 Kotlin 컴파일을 통과했다.

```bash
cd ios
xcodebuild -project BaseHaptic.xcodeproj -scheme BaseHaptic -configuration Debug -destination 'generic/platform=iOS Simulator' build
```

통과. iOS `내 팀` 체크인 허브 UI, 홈 화면 fallback 제거, 체크인 카드 상태 UI가 Swift 컴파일을 통과했다.

`pytest`는 현재 환경에 설치되어 있지 않아 실행하지 못했다.

## 4. 실테스트 전 점검

- iOS 앱에서 위치 권한 요청 문구가 의도대로 노출되는지 확인
- `stadium_cheer_enabled` OFF 시 region monitoring, local notification, watch trigger가 모두 차단되는지 확인
- 경기장 반경 진입 fallback 카드가 `내 팀` 탭으로 연결되고 과도하게 반복 노출되지 않는지 확인
- `POST /cheer-events` 요청에 실제 세션 bearer token이 포함되는지 확인
- Supabase RLS 때문에 익명/타 사용자 체크인 조회가 차단되는지 확인
- `cheer_signals` 응답을 빈 배열로 돌렸을 때 워치 발화가 즉시 멈추는지 확인
- Android `내 팀` 탭에서 체크인 허브 UI가 정상 노출되는지 확인
- Android `SHOW_STADIUM_CHEER_TOGGLE=true` 전환 시 설정 토글이 워치 설정 동기화를 정상 수행하는지 확인
- iOS 홈 화면이 아니라 `내 팀` 탭에서 pending 체크인 카드가 표시되는지 확인
- 현재 `INCHEON`/SSG 구장 좌표는 위치 실테스트를 위해 서울 중구 세종대로 67 근처로 임시 변경되어 있음. 테스트 종료 후 인천SSG랜더스필드 실제 좌표로 원복 필요

## 5. 롤백

긴급 차단 필요 시:

1. `GET /cheer-signals` 응답을 빈 배열로 내려 워치 발화를 차단한다.
2. `stadium_cheer_enabled` 기본값 또는 서버 게이트를 내려 신규 자동 체크인 경로를 멈춘다.
3. UI 노출이 문제면 iOS `SHOW_MY_TEAM_TAB=false` 핫픽스 빌드를 준비한다.
4. DB는 RLS를 유지하고, 문제 이벤트는 `validation_status='invalid'`로 정리한다.
