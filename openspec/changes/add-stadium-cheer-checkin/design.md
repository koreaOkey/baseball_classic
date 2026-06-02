## Context

야구봄 사용자가 KBO 9구장에서 직관 시 본인 응원팀의 경기 시작 정각에 워치로 풀스크린 응원 문구·팀 컬러·햅틱을 동시에 받는 "현장 동시 응원" 경험을 추가한다. 동시에 팀별 누적 체크인 랭킹 화면을 신규 탭(`내 팀`)에 노출한다.

초기 설계는 전체 기능을 다크 머지한 뒤 한 번에 켜는 방식이었다. 실테스트 요청에 따라 현재 구현은 플랫폼별로 갈라진다. iOS phone/watchOS, 백엔드, DB, iOS 권한 매니페스트는 활성화했고, **2026-06-02 기준 iOS와 Android 모두 `내 팀` 탭 진입점은 활성화**했다. Android phone/Wear OS는 실제 geofence 등록, 권한 매니페스트, Wear 수신 dispatch를 후속 활성화 전까지 다크 상태로 유지한다.

핵심 인프라 재사용:
- 폰↔워치 통신: iOS `WatchThemeSyncManager` / Android `WearGameSyncManager`·`WearSettingsSyncManager`
- 라이브 햅틱 마스터 토글: `live_haptic_enabled` (iOS `@AppStorage`, Android SharedPreferences) — 산하에 `stadium_cheer_enabled` 서브토글 추가
- 백엔드: `backend/api/app/main.py` 인라인 라우트 + `models.py` SQLAlchemy + `db/migrations/YYYYMMDD_NNN_*.sql`

## Goals / Non-Goals

**Goals**
- 9구장 진입 자동 감지 + OS 로컬 알림 (iOS 실테스트 활성화, Android 실제 geofence 등록은 후속 적용)
- cheer_signals.json 기반 클라이언트 자율 응원 발화 (백엔드 푸시 fan-out 없음)
- 워치 풀스크린 응원 (팀 컬러 배경 + 응원 문구 + 햅틱)
- 폰 화면은 응원 발화 시 변화 없음 (사용자 명시 결정)
- 원정/홈 동일 응원 문구
- cheer_events 단일 테이블 + 사후 검증 워커
- 신규 탭 `내 팀` (응원 랭킹 1차, 팀별 뉴스 자리 확보)
- iOS와 Android 신규 진입점은 `내 팀` 탭으로 활성화. Android의 위치 권한/지오펜싱 실제 등록/설정 토글 노출은 다크 상태 유지

**Non-Goals**
- 이벤트별 응원(홈런/세이브 등) — Phase 2
- 실물 경품 — 법무 검토 후 Phase 2
- 디바이스 단일성 정밀 차단 — Phase 2
- 우천 취소 silent push 강제 갱신 — Phase 1.5
- 백그라운드 자동 발화(앱 강제 종료 상태) — Phase 2
- 워치 단독 모드(폰 없이) — Phase 2
- 팀별 뉴스 화면 구현 — 별도 change

## Decisions

### D1. 클라이언트 자율 발화 (vs 서버 fan-out 푸시)
**선택**: `cheer_signals.json` 매일 발행 → 클라이언트가 시각에 자율 발화.
**근거**: 동시 푸시 fan-out 0건 → 동시간 수천 디바이스 부하 회피. 동시성은 NTP 보정된 `fire_at_iso`로 확보(±수백 ms).
**대안 (기각)**: APNs 토픽 fan-out — 인프라 부담 + 토픽 구독 관리 복잡 + iOS 백그라운드 사운드 제약.

### D2. 발화 조건: 위치만 (체크인 무관)
**선택**: 응원 시각에 구장 반경 내 + 응원팀이 오늘 해당 구장 출전 시 무조건 발화. 체크인은 랭킹 카운트 용도로 분리.
**근거**: 자동 알림을 못 본 사용자도 "함께 응원" 경험 누릴 수 있음.
**대안 (기각)**: 체크인 필수 — 알림 미수신자 배제됨.

### D3. cheer_signals.json 구조: 한 경기 entry에 홈/원정 2개 signals 포함
```json
{
  "game_id": "20260502_JAMSIL_DOOSAN_LG",
  "stadium_code": "JAMSIL",
  "fire_at_iso": "2026-05-02T18:30:00+09:00",
  "signals": [
    { "team_code": "DOOSAN", "role": "home", "cheer_text": "...", "primary_color": "#13274F", "haptic_pattern_id": "doosan_intro_v1" },
    { "team_code": "LG", "role": "away", "cheer_text": "...", "primary_color": "#C30452", "haptic_pattern_id": "lg_intro_v1" }
  ]
}
```
클라이언트는 사용자 응원팀 = `signals[].team_code` 매칭으로 1개 선택. **원정팬은 홈팬과 동일 cheer_text** (사용자 결정). `role` 필드는 향후 차별화 여지로 보존.

### D4. 플랫폼별 활성화 메커니즘
**Android**: 기존 `SHOW_COMMUNITY_TAB=false` 컨벤션 재사용. `SHOW_MY_TEAM_TAB=true`로 `내 팀` 탭 진입점은 노출한다. 권한/지오펜싱 실제 등록과 설정 토글은 후속 활성화 전까지 가드 유지.
**iOS**: 실테스트를 위해 `SHOW_MY_TEAM_TAB=true`, `case .myTeam`, region monitoring, checkin card, cheer signal fetch, watch trigger 송신을 활성화했다. `Info.plist`에도 위치 권한과 background location mode를 추가했다.
**백엔드**: 신규 라우트는 실제 등록되어 있다. `POST /cheer-events`는 bearer token 기반 사용자 식별을 사용하고, background validation task를 연결한다.
**DB 마이그레이션**: SQL 파일을 Supabase에 적용했다. `cheer_events.user_id`는 uuid 타입으로 보정했고, RLS 정책을 확인했다.
**활성화 검색 키**: Android 잔여 다크 경로와 TODO 마커는 후속 Android 활성화 때 재검색한다.

### D5. 데이터 저장: cheer_events 단일 raw 테이블 + 집계 캐시
검증/집계는 비동기 워커가 별도. 좌표 raw는 90일 후 익명화 (사용자 동의 약관 추가). 집계는 `team_checkin_daily` / `team_checkin_season` 두 캐시 테이블.

### D6. 워치 발화 채널: 폰→워치 직접 (백엔드 미경유)
**iOS**: `WatchThemeSyncManager`에 `sendCheerTrigger(payload:)` 추가. `sendMessage`(foreground) + `transferUserInfo`(background) 이중 사용.
**Android**: `WearGameSyncManager`에 `sendCheerTrigger(payload:)` 추가. `Wearable.getDataClient().putDataItem("/cheer/{ts}")`.
워치 측은 `WatchConnectivityManager`/`DataLayerListenerService`에서 신규 path 핸들링 → 풀스크린 응원 화면 띄움.

### D7. region monitoring 등록 방식
**iOS**: 9구장 `CLCircularRegion` 등록. 시스템 한도 20개 내 여유. 실테스트 활성화 상태에서는 앱 시작 시 설정 토글과 권한 상태를 확인한 뒤 region monitoring을 시작한다.
**Android**: `GeofencingClient.addGeofences(...)` 동일. `ACCESS_BACKGROUND_LOCATION` 권한 요청은 Android 활성화 시점에만.

### D8. 신규 탭 콘텐츠 컨테이너
탭 화면은 `MyTeamScreen`(또는 동등)이 경기장 체크인 허브 역할을 한다. 경기장 체크인, 워치 응원 안내, 팀별 체크인 랭킹을 한 화면 안에서 보여주며, 홈 화면은 주 진입점이 아니다. 알림 탭 또는 앱 내 fallback 진입은 `내 팀` 탭으로 연결한다.

현재 iOS와 Android UI는 목업 방향을 반영해 다음 구성을 가진다.
- 상단 헤더: `내 팀`, 선택 응원팀 pill, `구장 체크인`/`워치 응원`/`랭킹` 설명 버튼
- 경기장 체크인 카드: 경기장+위치핀 히어로 비주얼, 이미지 오른쪽 상단의 오늘 응원팀 경기 구장명/지역 오버레이, 전체 폭 `체크인하기` CTA, `위치 확인 완료` 상태 컴포넌트, 상태별 완료/권한 필요/경기 없음 표시. 구장명/지역은 선택 응원팀의 홈구장 fallback이 아니라 오늘 경기 목록에서 선택 응원팀이 참여하는 경기의 홈팀 구장을 기준으로 산출한다. `위치 확인 완료` 상태는 선택 팀 컬러와 무관하게 성공 의미의 초록색으로 표시한다. 실제 위치/경기 상태 연동 전까지는 UI 확인을 위해 체크인 가능 상태를 기본 표시한다.
- 기능 설명 팝업: `구장 체크인`, `워치 응원`, `랭킹` 버튼을 탭했을 때만 모달로 열리며 각 기능의 목적과 동작 방식을 표시
- 팀 체크인 랭킹 카드: 주간/시즌 토글, `iOS · Android 합산 집계` 라벨, 내 팀 강조
- 개인 기록 요약 카드: 후속 달력/개인 통계 화면을 위한 자리 확보

iOS는 `pendingCheckinStadium`을 홈 화면 카드가 아니라 `MyTeamScreen` 입력으로 넘긴다. 홈 화면은 경기장 체크인의 주 진입점으로 쓰지 않는다. 2026-06-02 재오픈 요청에 따라 iOS와 Android 모두 `SHOW_MY_TEAM_TAB=true`로 전환했으며, `내 팀` 탭에서 체크인 허브 UI를 확인할 수 있다.

향후 `TeamNewsView`, 개인 체크인 달력 등 자식 화면 추가 시 `내 팀` 탭 내부 segmented control 또는 내부 navigation으로 확장한다. Android는 현재 단계에서 `SHOW_STADIUM_CHEER_TOGGLE=false`를 유지해 설정 토글 노출과 실제 geofence 활성화를 막는다.

## Risks / Trade-offs

- **위치 스푸핑** → mock_location 플래그 + 사후 워커 invalid 처리. 실시간 차단은 어려움.
- **NTP 보정 정확도** → 응원 시각 ±수백 ms 흩어짐 허용. 사용자 체감상 충분.
- **iOS "항상 허용" 거부율** → 자동 알림 미수신. 앱 진입 시 카드 fallback으로 보완.
- **Android 백그라운드 위치 권한 거부율** → 동일. fallback 동일.
- **플랫폼별 활성화 차이** → iOS와 Android 기능 상태가 달라질 수 있음. 후속 Android 적용 시 spec/tasks 기준으로 누락 항목 재검증 필요.
- **stadiums.json 시즌 외 변경** (홈구장 임시 변경, 우천 취소 등) → Phase 1.5 silent push로 갱신.
- **실테스트 임시 좌표** → 위치 테스트를 위해 `INCHEON`/SSG 구장 좌표를 일시적으로 서울 중구 세종대로 67 근처로 오버라이드했다. 테스트 종료 후 인천SSG랜더스필드 실제 좌표로 되돌려야 한다.
- **권한 매니페스트 사전 추가 vs 활성화 시 추가** → 사전 추가 시 앱스토어 노출 영향. **활성화 시점 추가** 채택.

## Migration Plan

**Phase 0 — 다크 머지 기반 구현**
1. DB 마이그레이션 SQL 작성
2. 백엔드 라우터 함수와 모델 추가
3. iOS/Android 신규 탭·화면·체크인 카드·region monitoring 코드 추가
4. 워치 응원 화면 + 햅틱 패턴 추가
5. 통합 빌드 검증

**Phase 1 — iOS 실테스트 활성화 (현재 반영)**
1. DB 마이그레이션 Supabase 적용
2. 백엔드 라우트 활성화 + cheer signal 응답 + 검증 워커 연결
3. iOS `SHOW_MY_TEAM_TAB=true`, 홈 체크인 카드, region monitoring, local notification, watch trigger 활성화
4. iOS `Info.plist` 위치 권한/background mode 추가
5. iOS watch 수신 dispatch + overlay 활성화
6. iOS build 및 백엔드 import 검증

**Phase 2 — Android 활성화 (잔여)**
1. `SHOW_MY_TEAM_TAB=true` 반영 완료. 남은 항목은 실제 geofence/권한/알림 운영 연결
2. AndroidManifest `ACCESS_BACKGROUND_LOCATION`, `POST_NOTIFICATIONS` 추가
3. Android `StadiumGeofenceManager.startMonitoringDark(...)`를 실제 `addGeofences(...)` 호출로 전환
4. Android 체크인 카드에 실제 위치/경기/체크인 완료 상태 연결
5. Android `SHOW_STADIUM_CHEER_TOGGLE=true` 전환 및 설정 토글 노출
6. Android Wear 실기기에서 `PATH_CHEER_TRIGGER` 수신, overlay, 진동, dismiss 테스트
6. Android build/실기기 테스트
7. 파일럿 팀(LG/두산) 한정 서버 토글 후 9구장 확대

**롤백**: 실테스트 중 문제 시 `cheer_signals` 응답을 비우거나 `stadium_cheer_enabled` 기본값/서버 게이트를 내려 응원 발화를 차단한다. 클라이언트 노출이 문제라면 iOS `SHOW_MY_TEAM_TAB=false` 핫픽스 빌드를 준비한다.

## Open Questions

- 응원 시각이 우천 취소되면 어떻게? Phase 1.5 silent push로 cheer_signals 비움 → 클라이언트 발화 트리거 무시. (현재 change에서는 Non-Goal)
- 더블헤더 처리: signals[]에 2 entry 분리. 시각으로 구분.
- 신규 가입 사용자 시즌 카운트: 비례 가중치 vs 0부터 → 운영 정책 결정 필요. Phase 1은 단순 0부터.
- `내 팀` 탭 한국어 라벨 확정 ("내 팀" / "팀" / "마이팀" 등) — 활성화 직전에 디자인 검토.
