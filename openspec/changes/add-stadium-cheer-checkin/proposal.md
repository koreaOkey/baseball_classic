## Why

KBO 9구장에서 직관 중인 사용자가 본인 응원팀의 경기 시작 정각에 워치로 풀스크린 응원 문구 + 팀 컬러 + 햅틱을 동시에 받는 "현장 동시 응원" 경험을 제공한다. 동시에 팀별 누적 체크인 랭킹을 노출하여 팬덤 경쟁/시상 동기를 만든다. 백엔드 푸시 fan-out 없이 클라이언트가 cheer_signals.json 시각에 자율 발화하므로 부하가 거의 없으면서도 "같이 울리는" 경험을 만들 수 있다.

## Rollout Strategy (중요)

**모든 신규 기능은 구현하되 사용자에게는 노출되지 않는 "다크" 상태로 머지한다.** 활성화 명령이 별도로 떨어질 때까지 UI 진입점·알림·응원 발화·신규 탭·백엔드 엔드포인트 등록을 모두 차단한다.

- **클라이언트(iOS/Android phone, Watch)**: 화면·탭·체크인 카드·로컬 알림·응원 발화 호출부를 `// TODO(stadium-cheer): 활성화 시 주석 해제` 형태의 **블록 주석**으로 감싼다. 컴파일은 되지만 실행 경로에서 빠진다.
- **신규 탭 ("내 팀")**: TabView/BottomNavigation 등록 라인을 주석 처리. 라우트·아이콘·문자열 리소스는 추가하되 진입점만 비활성.
- **백엔드 (FastAPI)**: 신규 라우터 (`/api/cheer-events`, `/api/rankings/teams`)는 파일은 추가하되 `app.include_router(...)` 호출을 주석 처리. 마이그레이션은 보류(별도 명령 시 적용).
- **DB 마이그레이션**: 신규 테이블 SQL은 작성하되 **적용은 하지 않음** (`db/migrations/` 파일만 추가, 실행 보류).
- **Region monitoring 등록**: 코드는 작성하되 `startMonitoring`/`addGeofences` 호출 라인을 주석 처리 (위치 권한 요청도 비활성).
- **권한 매니페스트 (Info.plist / AndroidManifest)**: "항상 허용" 위치·POST_NOTIFICATIONS 키 추가는 **활성화 시점**에 진행. 미리 추가하면 앱스토어 심사 시 노출 영향 → 보류.
- **활성화 절차**: 추후 "stadium-cheer 활성화" 요청 시 주석 해제 + DB 마이그레이션 적용 + 라우터 include + 매니페스트 키 추가를 한 번에 수행.

## What Changes

- KBO 9구장 stadiums.json (좌표/반경/홈팀 등) 클라이언트 내장 + 원격 오버라이드 추가
- 매일 백엔드가 cheer_signals.json 발행 (경기별 홈/원정 2개 signals: team_code, fire_at_iso, cheer_text, primary_color, haptic_pattern_id)
- iOS Region Monitoring / Android Geofencing API로 9구장 진입 자동 감지 → 사용자 응원팀이 오늘 그 구장에서 경기 중일 때 OS 로컬 알림 발송
- 자동 체크인 팝업: 알림 탭 시 앱 진입 → [확인] 버튼으로 백엔드에 cheer_event INSERT
- 응원 시각 자율 발화: 사용자가 구장 반경 내 + 응원팀 출전 시 워치로 풀스크린 응원 문구 + 팀 컬러 배경 + 햅틱 진동 (폰 화면은 변화 없음 — 의도된 결정)
- 원정팬도 홈팬과 동일 응원 문구로 발화 (team_code 1개 = cheer_text 1개)
- cheer_events 테이블 신규: raw 좌표 + mock_location 플래그 포함, 좌표는 90일 후 익명화
- 검증 워커 신규: 좌표 재검증, mock_location 차단, 응원 시각 윈도우(-60분 ~ +30분) 외 클릭 invalid 처리, 1일 1체크인 (`user_id, date` 유니크)
- 팀별 응원 랭킹 화면 (시즌 + 주간) + 시즌 1위 팀 디지털 뱃지 시상
- 개인 체크인 이력 조회 인프라 (인덱스 + `GET /cheer-events/me` 라우트 스텁) — 향후 달력 UI / 개인 통계 화면을 위한 사전 준비. 화면 구현은 별도 change.
- **신규 탭 추가**: 기존 하단 탭(`홈` / `상점` / `설정`) 옆에 **`내 팀`** 탭(가칭) 신설.
  - 1차 콘텐츠: 응원 많이 간 팀 순위 (시즌/주간 토글 + 내 팀 위치 표시)
  - 향후 추가 예정 콘텐츠: **팀별 뉴스** (별도 change에서 다룰 수 있도록 인터페이스 자리만 마련)
  - 탭 자체는 라우트·아이콘·strings 모두 등록하되, **탭 진입점은 주석 처리하여 노출 차단**
- 기존 `live_haptic_enabled` 마스터 토글 산하에 `stadium_cheer_enabled` 서브토글 추가
- 워치 발화 채널: WatchConnectivity (iOS) / Wearable Data Layer (Android), 기존 폰→워치 직접 통신 인프라 재사용

## Capabilities

### New Capabilities
- `stadium-cheer`: 경기장 반경 진입 자동 체크인, cheer_signals.json 기반 응원 시각 자율 발화, 워치 풀스크린 응원 표시, 원정/홈 동일 처리
- `team-checkin-ranking`: cheer_events 기록·검증, 팀별 일/주/시즌 집계, 랭킹 화면, 시즌 1위 디지털 뱃지
- `my-team-tab`: 하단 탭 4번째 진입점("내 팀") + 탭 내부 콘텐츠 컨테이너. 1차 콘텐츠는 `team-checkin-ranking` 임베드, 향후 팀별 뉴스 등 추가 가능한 자리 확보. 진입점은 주석 처리로 차단(다크 상태)

### Modified Capabilities
- `live-haptic`: 기존 마스터 토글(`live_haptic_enabled`) 산하에 `stadium_cheer_enabled` 서브토글 추가하여 게이트 재사용
- `release`: Phase 1 파일럿 팀(LG/두산) 롤아웃 일정 반영

## Impact

- **백엔드 (FastAPI)**: cheer_signals.json 발행 잡, `/api/cheer-events` POST, `/api/rankings/teams` GET, 검증 워커, 집계 캐시 스케줄러
- **DB (Supabase)**: 신규 테이블 `cheer_events`, `team_checkin_daily`, `team_checkin_season`. 좌표 저장 정책 + 90일 익명화 잡
- **iOS phone (SwiftUI)**: CLLocationManager region monitoring, UNUserNotificationCenter 로컬 알림, 체크인 카드 UI, 응원 시각 타이머, WatchConnectivity 송신
- **iOS watch (watchOS)**: 응원 풀스크린 화면, 햅틱 패턴 재생
- **Android phone (Compose)**: GeofencingClient, NotificationManager, 체크인 카드, Wearable MessageClient 송신
- **Android Wear OS**: 응원 풀스크린, 햅틱 (Vibrator)
- **권한**: iOS "항상 허용" 위치 + 알림 권한, Android `ACCESS_BACKGROUND_LOCATION` + POST_NOTIFICATIONS. 거부 시 앱 진입 시 카드 fallback
- **개인정보**: 좌표 raw 저장 정책 약관 추가, 90일 익명화. 디지털 뱃지 단계는 실물 경품 미해당
- **외부 데이터**: KBO 일정 크롤러에서 fire_at_iso 도출 (기존 crawler 재사용)
- **신규 탭 ("내 팀")**: iOS는 `RootTabView`(또는 동등) `TabView` 4번째 항목, Android phone은 `BottomNavigation`/`NavigationBar` 4번째 destination. 등록은 모두 **주석 처리** 상태로 머지
- **Phase 1 파일럿**: LG/두산 (커뮤니티 파일럿 팀과 일치)
- **Non-Goals (Phase 2 이후)**: 이벤트별 응원(홈런 등), 실물 경품, 디바이스 단일성 정밀 차단, 우천 취소 silent push, 백그라운드 자동 발화(앱 미실행 시), 워치 단독 모드, **`내 팀` 탭의 팀별 뉴스 화면**(자리만 확보, 구현은 별도 change)
- **활성화 시 해야 할 일 (체크리스트)**: ① 클라이언트 주석 해제(검색 키: `TODO(stadium-cheer)`, `TODO(my-team-tab)`) ② `app.include_router` 주석 해제 ③ DB 마이그레이션 실행 ④ Info.plist/AndroidManifest 권한 키 추가 ⑤ stadiums.json 시즌 데이터 검증 ⑥ 파일럿 팀 한정 서버 토글
