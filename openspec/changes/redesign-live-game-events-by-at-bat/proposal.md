## Why

라이브 경기 상세 화면의 "실시간 이벤트"는 현재 평면 스트림(`EventCard` 시간 역순)으로만 나열돼, 같은 타자가 받은 BALL/STRIKE/FOUL/HIT 가 분리된 카드로 흘러간다. 사용자는 네이버 스포츠 릴레이(`m.sports.naver.com/.../relay`)처럼 "한 타석 = 한 카드"로 묶여, 타자/타순/투구 시퀀스/최종 결과가 한 단위로 보이는 흐름을 원한다.

데이터 측에서는 크롤러가 이미 네이버 API에서 타석 단위 키(`relayNo`)와 타석 내 순번(`seqno`)을 수집해 `source_event_id = "{inning:02d}-{relayNo:03d}-{seqno:04d}"` 형식으로 저장하고 있다(`crawler/backend_sender.py:763`). 다만 외부 응답 DTO `GameEventOut` 에는 이 분해 키가 노출되지 않아 클라이언트가 그룹화에 쓸 수 없다.

운영 제약: 운영 중인 서비스. 1000명+ 동시 접속 안정성이 우선이며 과거 DB pool 이슈가 있었으므로, DB 마이그레이션 / 추가 쿼리 / 신규 엔드포인트는 도입하지 않는다.

## What Changes

- **백엔드** (`backend/api/app/schemas.py`, `services.py`, `tests/test_api.py`)
  - `GameEventOut` 에 `atBatId: str | None`, `seqno: int | None` 추가.
  - `services._split_at_bat()` 헬퍼: `source_event_id` 가 `"NN-NNN-NNNN"` 형식(3-part, 모두 정수)일 때만 `("{inning}-{relay}", seqno)` 로 분해, 그 외(시뮬레이션·예제·구버전)에서는 `(None, None)` 폴백.
  - `to_event_out()` 이 두 키를 동시 산출. DB 컬럼·인덱스·크롤러·Redis 캐시·WS 직렬화 경로 무변경(직렬화 한 곳에서 자동으로 흐름).
  - 신규 테스트: 같은 `relayNo` 의 두 이벤트가 동일 `atBatId` 와 다른 `seqno` 를 받고, 비정형 sourceEventId 는 `null` 폴백 됨을 검증.

- **iOS 폰** (`ios/mobile/BaseHaptic/Models/LiveEvent.swift`, `Models/AtBatGroup.swift` (신규), `Screens/LiveGameScreen.swift`)
  - `LiveEvent` 에 `atBatId: String?`, `seqno: Int?` 디코딩 추가(`Codable` 미지 키 안전).
  - `AtBatGroup` 모델: `events: [LiveEvent]` → `[AtBatGroup]` 변환 함수. `atBatId` 로 그룹, 그룹 내 `seqno asc`, 그룹 사이는 cursor desc(최신 위). `atBatId == nil` 인 이벤트는 단일 그룹으로 폴백(현재 UI와 동등).
  - `LiveGameScreen` 의 이벤트 리스트 블록을 `ForEach(groups)` + 신규 `AtBatCard` 로 교체. 헤더(타자/타순) · 본문(투구 칩 시퀀스 B/S/F …) · 푸터(최종 결과). 강조는 그룹 최종 이벤트가 `EventFilterGate.isAllowed` 통과 시 Yellow500 토큰.

- **Android 폰** (`apps/mobile/.../model/LiveEvent.kt`, `model/AtBatGroup.kt` (신규), `ui/screens/LiveGameScreen.kt`)
  - 동일 패턴(`kotlinx.serialization` 미지 키 안전). `LazyColumn` `key = AtBatGroup.id` 로 스크롤 재배치 회피.

- **OpenSpec spec delta** (`openspec/specs/game-state` 후보)
  - `GameEventOut` 응답 스키마에 `atBatId`/`seqno` 명세 추가는 백엔드 PR 머지 시점에 갱신.

## Capabilities

### Modified Capabilities

- `game-state`
  - `GameEventOut` 응답에 타석 그룹화 키(`atBatId`, `seqno`)를 노출하여, 클라이언트가 추가 호출/엔드포인트 없이 평면 이벤트 스트림을 타석 단위로 재구성할 수 있게 확장.

- `mobile-ios`, `mobile-android`
  - 라이브 경기 상세의 실시간 이벤트 리스트가 평면 EventCard 나열에서 타석 단위 AtBatCard 그룹으로 재구성. 디자인 토큰(`AppEventColors`, `AppFont`, `AppSpacing`, `AppShapes`) 그대로 사용.

## Impact

- **백엔드**: 응답에 nullable 문자열·정수 필드 2개 추가(이벤트당 평균 +25 bytes). 기존 클라이언트는 미지 키 무시로 비호환 없음. 추가 SQL/쿼리/인덱스/마이그레이션 없음. Redis 캐시(`game:events:{game_id}`)와 WS payload는 동일 `to_event_out` 직렬화를 거치므로 자동 포함. 1000명 동시 접속 추가 부담은 트래픽 +0.5KB/접속 수준.
- **롤백**: 백엔드 PR 단독 revert 가능. 클라이언트는 `atBatId == nil` 시 평면 폴백.
- **단계적 출시**: 백엔드 PR 머지 → iOS PR → Android PR 순. 어느 한쪽만 배포돼도 폴백 덕에 안전.
- **Non-Goals**: 워치(Wear OS/watchOS) 라이브, LiveActivity / Long-look 노티 표시, 푸시 필터 정책, DB 스키마, 크롤러 로직, 라인업/필드 카드 — 모두 변경 없음.
