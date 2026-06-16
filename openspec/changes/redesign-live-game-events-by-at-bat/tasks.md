# Tasks

## Backend
- [x] `backend/api/app/schemas.py` — `GameEventOut.atBatId: str | None`, `seqno: int | None` 추가
- [x] `backend/api/app/services.py` — `_split_at_bat()` 헬퍼 + `to_event_out()` 에서 두 키 채우기 (3-part 정수일 때만, 그 외 None 폴백)
- [x] `backend/api/tests/test_api.py` — `test_event_at_bat_id_and_seqno_populated_from_source_event_id` 신규
- [x] `backend/api/app/schemas.py` — `GameEventOut.homeScoreAfter`, `awayScoreAfter: int | None` 추가 (라이브 상세 "득점" 탭 정확 점수 표기용)
- [x] `backend/api/app/services.py` — `_coerce_score()` 헬퍼 + `to_event_out()` 에서 `payload_json["homeScoreAfter"]`/`awayScoreAfter` pluck (없으면 None 폴백)
- [x] `crawler/backend_sender.py` — 각 option 의 `currentGameState` 에서 `homeScore`/`awayScore` 추출해 metadata 에 `homeScoreAfter`/`awayScoreAfter` 채움 (값이 없으면 키 생략)
- [x] `backend/api/tests/test_api.py` — `test_event_score_after_populated_from_payload_metadata` 신규: SCORE 이벤트가 응답에 정확 노출, 메타 누락 시 null 폴백, 0 도 명시적 유지
- [x] `pytest tests/` 전체 통과 (41 tests passed, 2026-06-04)
- [x] `backend/api/app/main.py` — `/games/{gameId}/events` 에 `inningNumber` / `scoringOnly` 선택 필터 추가(기존 호출 호환)
- [x] `backend/api/tests/test_api.py` — 이닝별/득점 이벤트 필터 응답 검증 추가
- [x] `crawler/backend_sender.py` — 네이버 relay option 의 `pitchNum` / `speed` / `stuff` / 투구 후 BSO / `batterRecord` / `metricOption` 을 이벤트 metadata 에 보존
- [x] `crawler/test_backend_sender.py` — 오윤석 5회초 타석 형태의 네이버 relay fixture성 테스트로 투구 상세 metadata 보존 검증
- [x] `backend/api/app/schemas.py` — `GameEventOut` 에 투구 상세 optional 필드(`pitchNum`, `pitchSpeed`, `pitchStuff`, `ballAfter`, `strikeAfter`, `outAfter`, `batterRecord`, 승리확률) 추가
- [x] `backend/api/app/services.py` — `payload_json` 에 저장된 투구 상세 metadata 를 안전 변환 후 이벤트 응답에 노출
- [x] `backend/api/tests/test_api.py` — 투구 상세 metadata 가 `/games/{gameId}/events` 응답에 노출되고 누락 시 null 폴백되는지 검증
- [x] `backend/api/scripts/simulate_crawler.py` — 로컬 백엔드에 네이버식 오윤석 타석 fixture를 1회 주입하는 `--naver-pitch-detail-once` 옵션 추가
- [x] `infra/staging/` — 운영과 같은 Railway Backend/Crawler + Supabase + Redis 구성을 별도 리소스로 준비하기 위한 env 예시, 마이그레이션 스크립트, 검증 문서 추가
- [x] `db/migrations/20260312_007_add_game_date_and_start_time_to_games.sql` — fresh staging Supabase에 현행 `games.game_date`/`start_time` 스키마가 재현되도록 누락 migration 추가
- [x] Railway — 운영 project 안에 임시 생성한 staging environment 삭제 후 별도 `baseball-classic-staging` project와 `staging` environment, Redis/backend/crawler 서비스, backend public domain 생성

## iOS Phone
- [x] `ios/mobile/BaseHaptic/Data/BackendGamesRepository.swift` — `LiveEvent` 에 `atBatId`, `seqno` 필드 + 명시적 init(기존 호출자 무영향) + `parseLiveEvent` 가 응답에서 디코딩
- [x] `ios/mobile/BaseHaptic/Models/AtBatGroup.swift` (신규) — `struct AtBatGroup` + `static func group([LiveEvent]) -> [AtBatGroup]`. outcome 후보 타입 set, seqno asc / leadCursor desc, atBatId nil → solo 그룹 폴백
- [x] `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — `filteredAtBats` computed 추가, 이벤트 리스트 블록을 `ForEach(filteredAtBats)` + `AtBatCard` 로 교체
- [x] `AtBatCard` / `FlowingPitchChips` / `PitchChip` 신규 컴포넌트 — 헤더(타자 + inning·vs pitcher) · 본문(투구 칩 6개/줄 wrap) · 푸터(outcome EventTypePill + description) · 강조(outcome 이 EventFilterGate 통과 시 yellow500 1.5pt border)
- [x] `pitchShortLabel()` 헬퍼 — B/S/안/홈/O/BB/DP/TP/득/도/태/교/교대
- [x] `ios/BaseHaptic.xcodeproj/project.pbxproj` — AtBatGroup.swift 를 PBXBuildFile / PBXFileReference / Models 그룹 / Sources phase 4곳에 등록 (plutil-lint OK)
- [x] `EventCard` 는 LiveActivity / 푸시 long-look 재사용 위해 보존
- [x] `ios/mobile/BaseHaptic/Data/BackendGamesRepository.swift` — 이벤트 조회에 `inningNumber` / `scoringOnly` query 지원 추가
- [x] `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — 현재 이닝 선로드 + 이닝/득점 탭 lazy load + 탭별 로딩 상태 및 캐시 추가
- [x] `ios/mobile/BaseHaptic/Data/BackendGamesRepository.swift` — 투구 상세 optional 응답 필드 파싱 추가(필드 없으면 기존 표시 폴백)
- [x] `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — `AtBatCard` 가 `batterRecord` 기반 타자 기록과 투구별 구속·구종·카운트 행을 표시하고, 승리확률은 숨기며 마지막 결과는 기존 이벤트 pill + description 으로 유지
- [x] `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — 경기 상세 상단 바에서 LIVE 배지를 제목 왼쪽으로 옮기고 Watch ON/OFF 배지를 최우측 액션으로 유지
- [x] iOS Debug 빌드가 스테이징 백엔드 URL을 build setting으로 주입할 수 있고 미주입 시 로컬 백엔드로 폴백하며, Release 빌드는 승격된 스테이징 서버를 운영 백엔드로 사용하도록 Info.plist / Xcode build setting 분리
- [x] iOS Debug 빌드가 스테이징 Supabase URL/publishable key를 build setting으로 주입할 수 있고, Release 빌드는 운영 Supabase 프로젝트를 사용하도록 Info.plist / Xcode build setting 분리

## iOS Watch
- [x] watchOS Debug 빌드가 스테이징 백엔드 URL을 build setting으로 주입할 수 있고 미주입 시 로컬 백엔드로 폴백하며, Release 빌드는 승격된 스테이징 서버를 운영 백엔드로 사용하도록 Info.plist / Xcode build setting 분리
- [x] watchOS는 네이버식 타석 상세 필드를 직접 표시하지 않고 기존 이벤트 타입 기반 표시/햅틱 흐름을 유지함 확인

## Android Phone
- [x] `apps/mobile/.../data/BackendGamesRepository.kt` — `LiveEvent` 에 `atBatId/seqno/homeScoreAfter/awayScoreAfter` 4필드 + `toLiveEvent()` 디코딩 갱신
- [x] `apps/mobile/.../data/model/AtBatGroup.kt` (신규) — `data class AtBatGroup` + `companion fun group(events) = ...`. iOS 와 동등 그룹화 규칙
- [x] `apps/mobile/.../ui/theme/EventColors.kt` — STRIKE=Yellow500, BALL=Green500, HIT=Blue500 분리
- [x] `apps/mobile/.../ui/screens/LiveGameScreen.kt` — `isScoreFilterActive` 상태, SCORE 분기 `filteredEvents`, `filteredAtBats`, `itemsIndexed` + 섹션 헤더 + `AtBatCard` 교체, "실시간 이벤트" → "실시간 중계"
- [x] `InningTabs` 컴포저블 — `onSelectInning/onSelectScore` 분리, "득점" 탭 활성화
- [x] `AtBatCard/AtBatSectionHeader/FlowingPitchChips/PitchChip/EventTypePill/pitchShortLabel/sectionKey/sectionTitle` 추가
- [x] SCORE outcome 그룹 푸터에 "{away} N : M {home}" Yellow500 정확 스코어 라인
- [x] `apps/mobile/.../ui/screens/LiveGameScreen.kt` — 경기 상세 상단 바에서 LIVE 배지를 제목 왼쪽으로 옮기고 Watch ON/OFF 배지를 최우측 액션으로 유지
- [x] `apps/mobile/app/build.gradle.kts` — Android Debug 빌드는 스테이징 Railway 백엔드와 스테이징 Supabase를, Release 빌드는 승격된 스테이징 서버를 운영 백엔드로 사용하도록 buildType 별 `BuildConfig` 분리
- [x] `DebugDummyLiveGame.events` 시연 데이터에 `atBatId/seqno + homeScoreAfter/awayScoreAfter` 부여 (BuildConfig.DEBUG 한정)
- [x] `EventCard` 는 LiveActivity / 푸시 long-look 재사용 위해 보존
- [x] `./gradlew :mobile:compileDebugKotlin` BUILD SUCCESSFUL
- [x] 이닝/득점 이벤트 필터 API는 선택 파라미터라 Android 기존 호출(`after`/`limit`)과 호환됨 확인

## Verification (앱 빌드 후)
- [ ] 로컬 시뮬레이션: `backend/api/scripts/simulate_crawler.py` 로 한 게임 풀 시드 → `curl /games/{id}/events` 응답에서 같은 `atBatId` 가 묶이고 비정형은 `null`
- [ ] iOS 시뮬레이터 라이브 상세: BALL→STRIKE→HIT 가 한 카드, 다음 타자로 넘어가면 새 카드가 위에 append
- [ ] HR/SCORE 필터 ON 시 해당 카드만 강조(노란 톤), 비매칭 카드는 평범한 톤으로 계속 보임
- [ ] Android 시뮬레이터 동등 시나리오 + `LazyColumn key` 로 스크롤 재배치 없음
- [ ] 백엔드 이전 버전(atBatId 없음)에 일부러 붙여 클라가 평면 폴백으로 동작 확인
- [ ] WebSocket 라이브 갱신 — 진행 중 카드에 칩 append, 결과 이벤트 도착 시 푸터 갱신
- [ ] 로컬 부하 sanity: `ab -n 1000 -c 100 /games/{id}/events` 응답 시간 ±5% 이내

## Non-Goals (이번 change 범위 밖)
- 워치(Wear OS / watchOS) 라이브 화면
- 워치 앱 이벤트 히스토리 lazy-load
- 워치 앱의 네이버식 타석 상세 카드 표시(기존 BALL/STRIKE/OUT 등 이벤트 타입 기반 표시/햅틱 유지)
- LiveActivity / Long-look 노티 표시 형식
- 백엔드 DB 스키마, 인덱스, RLS
- 푸시 필터 정책(HR/SCORE/HIT 디폴트 ON 그대로)
- 라인업/필드 카드 UI
