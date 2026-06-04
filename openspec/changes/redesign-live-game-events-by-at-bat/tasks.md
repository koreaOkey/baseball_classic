# Tasks

## Backend
- [x] `backend/api/app/schemas.py` — `GameEventOut.atBatId: str | None`, `seqno: int | None` 추가
- [x] `backend/api/app/services.py` — `_split_at_bat()` 헬퍼 + `to_event_out()` 에서 두 키 채우기 (3-part 정수일 때만, 그 외 None 폴백)
- [x] `backend/api/tests/test_api.py` — `test_event_at_bat_id_and_seqno_populated_from_source_event_id` 신규: 같은 `relayNo` 이벤트가 동일 `atBatId` + 다른 `seqno`, 비정형 sourceEventId 는 `null` 폴백
- [x] `pytest tests/` 전체 통과 (40 tests passed, 2026-06-04)

## iOS Phone
- [ ] `ios/mobile/BaseHaptic/Models/LiveEvent.swift` — `atBatId: String?`, `seqno: Int?` 디코딩
- [ ] `ios/mobile/BaseHaptic/Models/AtBatGroup.swift` (신규) — `struct AtBatGroup` + `static func group([LiveEvent]) -> [AtBatGroup]` (atBatId 그룹 / seqno asc / cursor desc 정렬 / nil 폴백)
- [ ] `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — 이벤트 리스트 블록을 `ForEach(groups)` + `AtBatCard` 로 교체
- [ ] `AtBatCard` 신규 컴포넌트 — 헤더(타자/타순) · 본문(`PitchChip` 시퀀스) · 푸터(결과 텍스트) · 강조(EventFilterGate 통과 시 Yellow500)
- [ ] `EventCard` 는 LiveActivity / 푸시 long-look 재사용 가능성 위해 보존

## Android Phone
- [ ] `apps/mobile/.../model/LiveEvent.kt` — `atBatId: String? = null`, `seqno: Int? = null` (kotlinx 기본값으로 미지 키 안전)
- [ ] `apps/mobile/.../model/AtBatGroup.kt` (신규) — `data class AtBatGroup` + `fun List<LiveEvent>.groupByAtBat(): List<AtBatGroup>`
- [ ] `apps/mobile/.../ui/screens/LiveGameScreen.kt` — `LazyColumn` `items(groups, key=AtBatGroup::id)` + `AtBatCard` 컴포저블
- [ ] `AtBatCard` 컴포저블 — iOS와 동일 디자인 토큰(`AppEventColors`, `AppFont`, `AppSpacing`, `AppShapes`)
- [ ] `PitchChip` 컴포저블 — Capsule shape, EventColors 매핑

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
- LiveActivity / Long-look 노티 표시 형식
- 백엔드 DB 스키마, 인덱스, RLS
- 크롤러 로직, payload_json 구조
- 푸시 필터 정책(HR/SCORE/HIT 디폴트 ON 그대로)
- 라인업/필드 카드 UI
