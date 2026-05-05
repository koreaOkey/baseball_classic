## Why

KBO 9구장에서 직관 중인 사용자가 본인 응원팀의 경기 시작 정각에 워치로 풀스크린 응원 문구 + 팀 컬러 + 햅틱을 동시에 받는 "현장 동시 응원" 경험을 제공한다. 동시에 팀별 누적 체크인 랭킹을 노출하여 팬덤 경쟁/시상 동기를 만든다. 백엔드 푸시 fan-out 없이 클라이언트가 cheer_signals.json 시각에 자율 발화하므로 부하가 거의 없으면서도 "같이 울리는" 경험을 만들 수 있다.

## Rollout Strategy (현재 상태)

초기 계획은 다크 머지였지만, 실테스트를 위해 **2026-05-05 기준 iOS phone/watchOS + 백엔드 + DB + iOS 권한 매니페스트는 활성화 상태**로 전환했다. Android phone/Wear OS는 일부 화면·송신 코드만 들어가 있으며 geofence, 체크인 카드, 권한 매니페스트, Wear 수신 dispatch는 아직 다크/미완료 상태로 유지한다.

- **DB (Supabase)**: `cheer_events`, `team_checkin_daily`, `team_checkin_season` 마이그레이션을 적용했다. 기존 ORM 생성 이력으로 `cheer_events.user_id`가 varchar였던 상태를 uuid로 보정한 뒤 RLS 정책을 확인했다.
- **백엔드 (FastAPI)**: 신규 라우트는 실제 등록되어 있다. 활성 경로는 `GET /stadiums`, `GET /cheer-signals`, `POST /cheer-events`, `POST /cheer-events/validate-pending`, `GET /rankings/teams`, `GET /cheer-events/me`이다.
- **iOS phone**: `내 팀` 탭, 경기장 체크인 카드, region monitoring, cheer signal fetch, 체크인 POST, 워치 응원 trigger 송신, `stadium_cheer_enabled` 설정 토글, 위치 권한 매니페스트를 활성화했다.
- **iOS watch**: `stadium_cheer_trigger` 수신 dispatch와 풀스크린 응원 overlay를 활성화했다.
- **Android phone/Wear OS**: `내 팀` 화면 골격과 `sendCheerTrigger`/watch 수신 함수는 추가되어 있으나, 사용자 노출/권한/지오펜싱/체크인/수신 dispatch 활성화는 후속 Android 적용 범위로 남긴다.

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
  - 1차 콘텐츠: 응원 많이 간 팀 순위 (주간/시즌 토글 + 내 팀 위치 표시)
  - 향후 추가 예정 콘텐츠: **팀별 뉴스** (별도 change에서 다룰 수 있도록 인터페이스 자리만 마련)
  - iOS는 실테스트를 위해 탭 진입점을 활성화했고, Android는 `SHOW_MY_TEAM_TAB=false` 가드로 노출 차단 유지
- 기존 `live_haptic_enabled` 마스터 토글 산하에 `stadium_cheer_enabled` 서브토글 추가
- 워치 발화 채널: WatchConnectivity (iOS) / Wearable Data Layer (Android), 기존 폰→워치 직접 통신 인프라 재사용

## Capabilities

### New Capabilities
- `stadium-cheer`: 경기장 반경 진입 자동 체크인, cheer_signals.json 기반 응원 시각 자율 발화, 워치 풀스크린 응원 표시, 원정/홈 동일 처리
- `team-checkin-ranking`: cheer_events 기록·검증, 팀별 일/주/시즌 집계, 랭킹 화면, 시즌 1위 디지털 뱃지
- `my-team-tab`: 하단 탭 4번째 진입점("내 팀") + 탭 내부 콘텐츠 컨테이너. 1차 콘텐츠는 `team-checkin-ranking` 임베드, 향후 팀별 뉴스 등 추가 가능한 자리 확보. iOS는 실테스트 활성화, Android는 가드 유지.

### Modified Capabilities
- `live-haptic`: 기존 마스터 토글(`live_haptic_enabled`) 산하에 `stadium_cheer_enabled` 서브토글 추가하여 게이트 재사용
- `release`: Phase 1 파일럿 팀(LG/두산) 롤아웃 일정 반영

## Impact

- **백엔드 (FastAPI)**: `/stadiums`, `/cheer-signals`, `/cheer-events`, `/cheer-events/validate-pending`, `/rankings/teams`, `/cheer-events/me`, 검증 워커, 집계 캐시
- **DB (Supabase)**: 신규 테이블 `cheer_events`, `team_checkin_daily`, `team_checkin_season`. 좌표 저장 정책 + 90일 익명화 잡
- **iOS phone (SwiftUI)**: CLLocationManager region monitoring, UNUserNotificationCenter 로컬 알림, 체크인 카드 UI, 응원 시각 타이머, WatchConnectivity 송신
- **iOS watch (watchOS)**: 응원 풀스크린 화면, 햅틱 패턴 재생
- **Android phone (Compose)**: GeofencingClient, NotificationManager, 체크인 카드, Wearable MessageClient 송신
- **Android Wear OS**: 응원 풀스크린, 햅틱 (Vibrator)
- **권한**: iOS "항상 허용" 위치 + 알림 권한, Android `ACCESS_BACKGROUND_LOCATION` + POST_NOTIFICATIONS. 거부 시 앱 진입 시 카드 fallback
- **개인정보**: 좌표 raw 저장 정책 약관 추가, 90일 익명화. 디지털 뱃지 단계는 실물 경품 미해당
- **외부 데이터**: KBO 일정 크롤러에서 fire_at_iso 도출 (기존 crawler 재사용)
- **신규 탭 ("내 팀")**: iOS는 4번째 탭으로 활성화, Android phone은 `BottomNavigation`/`NavigationBar` 4번째 destination을 `SHOW_MY_TEAM_TAB=false` 가드로 유지
- **Phase 1 파일럿**: LG/두산 (커뮤니티 파일럿 팀과 일치)
- **Non-Goals (Phase 2 이후)**: 이벤트별 응원(홈런 등), 실물 경품, 디바이스 단일성 정밀 차단, 우천 취소 silent push, 백그라운드 자동 발화(앱 미실행 시), 워치 단독 모드, **`내 팀` 탭의 팀별 뉴스 화면**(자리만 확보, 구현은 별도 change)
- **남은 활성화 체크리스트**: ① Android phone 지오펜싱/체크인 카드/랭킹 API 연동 ② AndroidManifest 위치·알림 권한 추가 ③ Android Wear 수신 dispatch 및 overlay 활성화 ④ stadiums/cheer signal 시즌 데이터 운영 검증 ⑤ 파일럿 팀 한정 서버 토글
