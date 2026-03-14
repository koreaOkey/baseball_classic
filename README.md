---

# ⚾️ Project: BaseHaptic (실시간 야구 중계 서비스)

> **"보지 않아도 손목으로 느끼는 현장감"**
> 
> 야구 경기의 결정적 순간을 실시간 진동과 애니메이션으로 전달하는 멀티 디바이스 서비스

---

## 1. 서비스 개요

- **목표:** 경기 전체를 시청하기 어려운 상황에서 핵심 이벤트(득점, 홈런 등)를 실시간으로 전달.
    
- **타겟:** 안드로이드(Wear OS) 우선 개발 후 iOS(watchOS) 확장.
    
- **핵심 가치:** 햅틱(Haptic) 피드백을 통한 몰입형 중계 경험.
    

---

## 2. 주요 기능 상세

### 📱 스마트폰 앱 (Hub & Community)

- [ ] **개인화 설정:** 응원 팀 선택 및 팀별 아이덴티티 UI 적용.
    
- [ ] **토탈 테마 상점:** - 팀별 캐릭터 애니메이션(Lottie), 전용 폰트, 컬러 스킨 패키지.
    
    - 구매 시 폰과 워치에 동시 적용.
        
- [ ] **자동화 (Calendar Sync):** - 응원 팀 경기 일정을 시스템 캘린더와 연동.
    
    - 경기 시작 전 워치로 "중계 활성화" 자동 팝업 전송.
        
- [ ] **팀 커뮤니티:** 실시간 응원 채팅 및 퀵 응원 이모티콘 연동.
    

### ⌚ 스마트 워치 앱 (Live Node)

- [ ] **실시간 이벤트 중계:** - Ball, Strike, Out, Homerun 등 상황별 UI 및 **차별화된 진동 패턴** 제공.
    
- [ ] **백그라운드 유지:** - 화면이 꺼진 상태(Doze 모드)에서도 포그라운드 서비스를 통해 실시간 진동 알림 유지.
    
- [ ] **원격 하이파이브 (Remote High-Five):** - **기술:** BLE(Bluetooth Low Energy) 활용.
    
    - **기능:** 1m 내 동일 팀 팬 감지 시 득점 순간 특수 진동 공유 (ON/OFF 가능).
        
- [ ] **퀵 응원 (Quick Cheer):** - 화면 하단 숏컷 버튼으로 커뮤니티에 즉시 응원 메시지(와아!, ㅠㅠ 등) 전송.
    

---

## 3. 기술 스택 (Tech Stack)

> [!info] **KMP(Kotlin Multiplatform) 기반 전략**
> 
> 안드로이드 우선 개발 후 iOS 확장을 위해 비즈니스 로직을 공유하는 구조로 설계합니다.

|**구분**|**기술 스택**|**비고**|
|---|---|---|
|**Common Logic**|**Kotlin Multiplatform (KMP)**|API 통신, 데이터 파싱, 캘린더 로직 공유|
|**Android UI**|**Jetpack Compose / Wear OS**|네이티브 퍼포먼스 최적화|
|**iOS UI**|**SwiftUI / watchOS**|향후 확장 단계에서 적용|
|**Data Feed**|**Python (Crawler) + FastAPI ingest**|중계 데이터 수집 후 backend ingest API로 전송|
|**Database**|**Supabase (PostgreSQL)**|경기/이벤트 영속 저장 및 조회|
|**Animation**|**Lottie**|저용량 고품질 벡터 애니메이션|
|**Proximity**|**BLE / UWB**|근거리 팬 감지 및 하이파이브 로직|

---

## 4. 데이터 워크플로우

코드 스니펫

```
graph LR
    A[Crawler Server] -->|JSON| B[App Backend]
    B -->|WebSocket/Push| C[Smartphone App]
    C -->|Data Layer API| D[Smartwatch App]
    D -->|Haptic/UI| E[User Experience]
```

---

## 5. 레포 구조 (Monorepo)

|폴더|설명|
|---|---|
|`crawler/`|중계 데이터 수집/정제(파이썬 크롤러)|
|`backend/`|API/실시간 전달 백엔드|
|`db/`|DB 스키마/마이그레이션|
|`apps/mobile/`|스마트폰 앱(안드로이드 우선)|
|`apps/watch/`|Wear OS 워치 앱|
|`docs/`|문서|
|`data/`|샘플/실행 결과 파일|
|`Test/`|테스트 산출물 및 검증 SQL|
|`Daily/`|일일 작업 로그|

---

## 5. MVP 설계 및 플랫폼 선택

### ✅ 목표 범위 (MVP)
- **수집/저장 파이프라인:** 중계 데이터를 10초 단위로 크롤링 → DB 저장
- **전달/소비 파이프라인:** DB 기반 이벤트를 스마트폰/워치로 실시간 전달

### 🧱 구성 단위
- **크롤러 서비스:** 경기별 API 폴링 + 이벤트 정제
- **저장 계층:** 이벤트/경기/선수 상태를 DB에 적재 (중복 방지)
- **전달 계층:** 최신 이벤트를 모바일/워치로 푸시/소켓 전달
- **클라이언트:** 폰에서 워치로 Data Layer 동기화 + 햅틱 출력

### 📌 플랫폼 선택 (MVP 제안)
- **DB:** Supabase(PostgreSQL)  
  - SQL 기반 분석/조회가 쉽고, MVP에서 운영 부담이 낮음
- **실시간 전달:** Firebase(FCM) + WebSocket  
  - 모바일 푸시에 강하고, 워치 전달은 폰이 중계
- **크롤러 실행:** Cloud Run or 간단 VM  
  - 10초 폴링 작업을 안정적으로 실행

### 🗺️ 로컬 MVP → 클라우드(정석 운영) 이관 계획
#### 로컬(MVP)에서 먼저 검증할 것
- **크롤러(10초 폴링)**: 중계 API를 주기적으로 호출하고, 이벤트를 파싱/정제
- **저장(중복 방지)**: 같은 이벤트가 여러 번 수집돼도 DB에 중복 저장되지 않게 설계(Upsert/Unique Key)
- **조회 API(최소)**:
  - 최신 이벤트 조회 (예: `GET /games/{id}/latest-events`)
  - 현재 상태 조회 (예: `GET /games/{id}/state`)
- **실시간 전달은 선택**:
  - MVP 초기에는 앱이 폴링(예: 2~5초)으로도 충분
  - 이후 필요 시 WebSocket/FCM으로 확장

#### 클라우드(정석 운영)로 옮길 때의 표준 구성
- **DB(관리형 Postgres)**: Supabase / RDS / Cloud SQL 중 택1
- **백엔드(API)**: 컨테이너 기반(예: Cloud Run) + HTTPS(도메인)
- **크롤러 실행**: Cloud Run Job/서비스 또는 VM에서 안정적으로 상시 실행
- **인증/권한**:
  - 사용자용: JWT/OAuth 등 토큰 기반 인증
  - 크롤러용: 별도 API Key/Service Account로 권한 분리
- **네트워크 원칙**:
  - DB는 외부에 직접 노출하지 않고(가능하면 private) 백엔드만 접근
  - “IP 화이트리스트”는 운영자/DB 관리용에만 사용(일반 사용자 앱은 토큰 인증)

#### 로컬→클라우드 이관을 쉽게 만드는 원칙
- **환경변수로 분리**: DB URL, API Base URL, 폴링 주기, 게임ID, 키/토큰
- **스키마 우선 고정**: 엑셀은 디버깅용, 운영의 소스는 DB
- **Idempotent 처리**: 재시도/중복 호출에도 결과가 깨지지 않게(중복 이벤트 방지)

---

## 6. 단계별 개발 로드맵

### 🚩 Phase 1: MVP 개발 (Andorid Focus)

- [ ] Python 기반 실시간 경기 데이터 크롤러 구축.
    
- [ ] Wear OS 포그라운드 서비스 및 기본 진동 로직 구현.
    
- [ ] 폰-워치 간 데이터 레이어 동기화.
    

### 🚩 Phase 2: UX 고도화

- [ ] 팀별 토탈 테마 시스템 및 Lottie 애니메이션 적용.
    
- [ ] 시스템 캘린더 연동 및 자동 알림 로직.
    
- [ ] 퀵 응원 버튼 및 커뮤니티 연동.
    

### 🚩 Phase 3: 인터랙션 및 확장

- [ ] BLE 기반 '원격 하이파이브' 기능 구현.
    
- [ ] iOS 및 Apple Watch용 UI 개발 (KMP 공유 모듈 활용).
    
- [ ] 배터리 및 데이터 소모 최적화.
    

---

## 7. 개발 참고 메모

- **크롤링 지연:** 실제 중계와의 시차를 1초 미만으로 줄이는 것이 핵심.
    
- **배터리 관리:** 경기 중(3~4시간) BLE 스캔과 포그라운드 유지에 따른 배터리 소모 모니터링 필요.
    
- **애니메이션:** 워치 저장 공간을 고려해 테마별 리소스는 온디맨드(On-demand) 다운로드 방식 채택.

## Recent Changes (2026-03-07)

- Added event classification updates in crawler/backend ingest:
  - video review verdict `OUT -> OUT` is classified as `OUT`
  - pitcher substitution is classified as `PITCHER_CHANGE`
- Added daily automation flow:
  - schedule import at `00:05` (KST)
  - relay availability check every minute for 10 minutes from each game `startTime`
  - start live crawler only when relay is available
- Added game start-time flow:
  - `games.start_time` persisted and exposed as `startTime`
  - mobile home list now fetches `GET /games?date=<today>`
- Updated home cards:
  - start time is shown on cards
  - finished games show `game start time HH:MM game finished`
  - finished games are sorted by start time
- Updated watch sync UX:
  - synced live game cards are highlighted
  - sync confirm dialog appears only for unsynced live games
  - already synced live game does not show confirm dialog
- Updated count/inning behavior:
  - ball/strike reset immediately when out count reaches 3
  - on finished games, watch inning label is forced to `game finished` and B/S/O is normalized
- Added event-level player columns and ingest handling:
  - `game_events.pitcher`
  - `game_events.batter`

## Recent Changes (2026-03-08)

- Added `games.game_date` and changed `/games?date=` filtering to use `game_date` first.
- Kept safe fallback for legacy rows: if `game_date` is null, filter falls back to `game_id` date prefix.
- Improved game-date inference from `game_id`:
  - supports standard `YYYYMMDD...`
  - supports WBC-like `####MMDD...YYYY` (example: `88880308AUJP02026`)
- Updated schedule import to store `gameDate` from source (`gameDate`/`gameDateTime`) instead of forcing target date.
- Fixed and backfilled previously mis-dated rows so `2026-03-07` and `2026-03-08` are separated correctly.
- Hardened live dispatcher backend polling:
  - accepts list JSON payloads from `/games`
  - uses `limit=100` to match backend API validation limits
- Mobile home screen behavior update:
  - removed stale fallback list retention so previous-day games do not remain after date rollover
  - fixed multiple mojibake/broken Korean UI strings (home cards, watch-sync dialog, live screen labels)

## Recent Changes (2026-03-12)

- Expanded schedule/dispatcher flow to support KBO as first-class input:
  - dispatcher now accepts `--league kbo` and `--schedule-url` like
    `https://m.sports.naver.com/kbaseball/schedule/index?category=kbo&date=2026-03-12`
  - added `crawler/live_baseball_dispatcher.py` entrypoint (alias to existing dispatcher)
- Expanded backend schedule import script to support KBO/WBC using the same options:
  - `backend/api/scripts/import_wbc_schedule.py --league kbo`
  - `backend/api/scripts/import_wbc_schedule.py --schedule-url <naver-url>`
- Mobile backend team mapping now supports KBO Korean team names/aliases (`두산`, `롯데`, `한화`, etc.),
  so KBO games resolve to proper team logos/themes instead of `Team.NONE`.

## Recent Changes (2026-03-13)

- Dispatcher/crawler pregame lineup flow was expanded:
  - added dispatcher option `--enable-preview-lineup-precheck`
  - when enabled, dispatcher treats `/schedule/games/{gameId}/preview` lineup data as available signal
  - this allows crawler launch before relay text appears
- Crawler lineup extraction was expanded:
  - if relay lineup/entry payload is empty, crawler now uses preview lineup data
  - preview lineup is normalized into relay-like `homeLineup/awayLineup/homeEntry/awayEntry`
  - `lineupSlots`, `batterStats`, `pitcherStats` can be synced before first pitch
- Mobile watch-sync UX was expanded:
  - app now detects when the user's team game becomes `LIVE`
  - app shows confirmation popup: `��ġ�� ��� �����Ͻðڽ��ϱ�?`
  - `��`: sync current game to watch
  - `�ƴϿ�`: do not sync
  - existing manual watch-sync flow from live card tap remains unchanged

## Recent Changes (2026-03-14)

- Added KBO team-rank ingest pipeline (`crawler -> backend -> Supabase`):
  - crawler/dispatcher fetches
    `GET /statistics/categories/{categoryId}/seasons/{seasonCode}/teams`
  - backend ingest endpoint added:
    `POST /internal/crawler/team-records`
  - Supabase table added:
    `public.team_record`
- Added team-rank persistence model and migration:
  - backend model/schema/service for `team_record` upsert
  - migration file:
    `db/migrations/20260314_008_add_team_record_table.sql`
  - includes unique key, rank index, `updated_at` trigger, and public read RLS policy
- Added team-rank read API for clients:
  - `GET /team-records/{teamId}?categoryId=kbo&seasonCode=YYYY`
  - used by mobile app (no direct DB access from app)
- Hardened backend team-rank API behavior:
  - ingest endpoint enforces `X-API-Key` validation (`401` on invalid key)
  - ingest upsert deduplicates payload rows by `teamId` before DB write
  - team-record lookup returns `404` when data is not found
  - added backend tests for ingest upsert/auth/not-found flows
- Updated mobile Home quick stats cards:
  - `현재 순위` now uses `team_record.ranking`
  - `승률` now uses `team_record.wra`
  - data source is backend API ingest output, not direct DB query from mobile
- Improved watch team-sync reliability when favorite team changes:
  - mobile theme sync no longer skips when `connectedNodes` is temporarily empty
  - theme sync path is now stable (`/theme/current`) so latest favorite team is kept as current state
- Updated watch haptic event behavior while screen is off:
  - when a haptic event is received, watch now wakes screen and foregrounds `MainActivity`
  - `MainActivity` now uses `setShowWhenLocked(true)` and `setTurnScreenOn(true)`
- Tuned watch live screen layout position:
  - entire `LiveGameScreen` is shifted upward with `offset(y = (-30).dp)`
  - pitcher/batter info line is moved downward with `offset(y = 35.dp)`
- Incident fixes: snapshot ingest lock/contention and stability hardening:
  - symptom: intermittent `503` / lock-timeout while ingesting `/internal/crawler/games/{gameId}/snapshot`
  - root cause: concurrent writers touching the same `games` row (`schedule import` + live crawler)
  - backend mitigation:
    - added lock-timeout detection and retry loop in snapshot ingest
    - if retries are exhausted, return `503 snapshot ingest busy; retry shortly`
    - added safe rollback handling when DB connection is already broken during rollback
  - crawler/dispatcher mitigation:
    - dispatcher now passes crawler backend retry options (`--crawler-backend-retries`, `--crawler-backend-timeout-sec`)
    - default values raised to `retries=9`, `timeout=15s`
    - schedule import now skips `LIVE` games to avoid conflicting writes with live crawler
- Incident fixes: dispatcher duplicate-run / process overlap:
  - symptom: same dispatcher command observed in multiple processes, increasing duplicate ingest pressure
  - mitigation:
    - added single-instance lock file support (`--dispatcher-lock-file`)
    - added optional replica leader guard (`--leader-replica-id`, env: `DISPATCHER_LEADER_REPLICA_ID`)
- Incident fixes: Supabase Session Pooler connection exhaustion:
  - symptom: `MaxClientsInSessionMode: max clients reached - in Session mode max clients are limited to pool_size`
  - mitigation:
    - added SQLAlchemy pool tuning config (env-driven) in backend:
      - `BASEHAPTIC_DB_POOL_SIZE` (default `2`)
      - `BASEHAPTIC_DB_MAX_OVERFLOW` (default `0`)
      - `BASEHAPTIC_DB_POOL_TIMEOUT_SEC` (default `30`)
      - `BASEHAPTIC_DB_CONNECT_TIMEOUT_SEC` (default `10`)
      - `BASEHAPTIC_DB_POOL_RECYCLE_SEC` (default `1800`)
    - recommendation for Railway + Supabase Session Pooler:
      - set `BASEHAPTIC_DB_POOL_SIZE=1`
      - set `BASEHAPTIC_DB_MAX_OVERFLOW=0`
