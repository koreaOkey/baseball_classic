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

- `SHOW_MY_TEAM_TAB=true`로 `내 팀` 탭 활성화
- `MyTeamScreen`, `TeamCheckinRankingView`, `CheerCheckinCard` 연결
- `StadiumRegionMonitor.start()` 경로 활성화
- `CheerSignalsLoader`에서 `/cheer-signals`, `/rankings/teams`, `/cheer-events` 연동 활성화
- `WatchThemeSyncManager.sendCheerTrigger(...)` 호출부 연결
- `SettingsScreen`에 `stadium_cheer_enabled` 토글 추가
- `Info.plist`에 위치 권한 문구와 `location` background mode 추가
- `xcodegen generate`로 신규 Swift 파일을 Xcode project에 반영

### iOS Watch

- `StadiumCheerScreen` 추가
- `WatchConnectivityManager`의 `stadium_cheer_trigger` dispatch 활성화
- watch app root에 풀스크린 응원 overlay 연결

## 2. 아직 남은 Android 적용

### Android Phone

- `SHOW_MY_TEAM_TAB=false` 가드 유지
- `MyTeamScreen`, `TeamCheckinRankingScreen`, `WearGameSyncManager.sendCheerTrigger(...)`는 추가됨
- 남은 작업:
  - 홈 체크인 카드 추가 및 호출부 연결
  - `StadiumGeofenceManager` 추가
  - `CheerSignalsLoader` 추가 및 백엔드 API 연동
  - `SettingsScreen`의 `stadium_cheer_enabled` 토글 추가
  - `strings.xml` 신규 문구 추가
  - `AndroidManifest.xml` 위치/알림 권한 추가

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

`pytest`는 현재 환경에 설치되어 있지 않아 실행하지 못했다.

## 4. 실테스트 전 점검

- iOS 앱에서 위치 권한 요청 문구가 의도대로 노출되는지 확인
- `stadium_cheer_enabled` OFF 시 region monitoring, local notification, watch trigger가 모두 차단되는지 확인
- 경기장 반경 진입 fallback 카드가 홈 화면에서 과도하게 반복 노출되지 않는지 확인
- `POST /cheer-events` 요청에 실제 세션 bearer token이 포함되는지 확인
- Supabase RLS 때문에 익명/타 사용자 체크인 조회가 차단되는지 확인
- `cheer_signals` 응답을 빈 배열로 돌렸을 때 워치 발화가 즉시 멈추는지 확인

## 5. 롤백

긴급 차단 필요 시:

1. `GET /cheer-signals` 응답을 빈 배열로 내려 워치 발화를 차단한다.
2. `stadium_cheer_enabled` 기본값 또는 서버 게이트를 내려 신규 자동 체크인 경로를 멈춘다.
3. UI 노출이 문제면 iOS `SHOW_MY_TEAM_TAB=false` 핫픽스 빌드를 준비한다.
4. DB는 RLS를 유지하고, 문제 이벤트는 `validation_status='invalid'`로 정리한다.
