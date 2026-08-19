# fix-venting-metrics-collection-gaps — 분풀이 지표 수집 구멍 3개 수정

## Why

업데이트 온보딩 기획 중 "분풀이 진입을 어떤 타이밍에 몇 명이나 하는지" 집계 가능 여부를
점검한 결과, 수집 파이프라인에 데이터가 유실되는 구멍 3개가 확인됐다:

1. **워치 진입 전량 유실**: 클라이언트는 `watch_room_enter`를 전송하지만 백엔드
   `VALID_VENTING_EVENT_TYPES`에 없어 400으로 거절 → 워치 lite 룸 사용량이 0으로 집계.
2. **진입 경로 라벨 유실**: 클라이언트 실제 전송값은 `home_card`·`live`·`loss_push`인데
   백엔드 허용 목록은 `home_card`·`live_button`·`loss_prompt` → `live`(라이브 플로팅 버튼)와
   `loss_push`(패배 푸시 딥링크)가 NULL로 정규화되어 경로별 분석 불가.
3. **순 사용자 집계 불가**: 백엔드는 Bearer 토큰에서 `user_id(sub)`를 추출하도록 구현돼
   있으나 iOS·Android 리포터 모두 Authorization 헤더를 첨부하지 않아 `user_id`가 항상 NULL
   → "몇 건"만 가능하고 "몇 명"은 불가.

## What Changes

- **백엔드 허용 목록 정정** (`backend/api/app/venting.py`):
  - `VALID_VENTING_EVENT_TYPES`에 `watch_room_enter` 추가.
  - `VALID_ENTRY_SOURCES`를 클라이언트 실제 전송값 기준으로 교체:
    `{home_card, live, loss_push, whats_new}` — `whats_new`는 업데이트 팝업
    "지금 해보기" CTA(별도 change로 구현 예정) 선반영.
  - `models.py`의 `VentingEvent` 컬럼 주석 동기화.
- **리포터 토큰 첨부 (iOS·Android)**: 로그인 세션이 있으면
  `Authorization: Bearer <accessToken>`을 첨부(Supabase 세션). 비로그인·조회 실패 시
  헤더 없이 익명 이벤트로 전송 — 기존 best-effort/비차단 원칙 유지.
  - iOS `VentingMode/Data/VentingEventsReporter.swift`: detached Task 내
    `try? await SupabaseClientProvider.client.auth.session.accessToken`.
  - Android `venting/VentingEventReporter.kt`:
    `runCatching { ...auth.currentSessionOrNull()?.accessToken }`.
- **백엔드 테스트 추가** (`tests/test_venting.py`):
  `watch_room_enter` 수용 + `live`/`loss_push`/`whats_new` 소스 보존 + Bearer 토큰
  `user_id` 추출 저장.

## Impact

- 변경 파일 5개: 백엔드 2(`venting.py`, `models.py`) + 테스트 1 + 클라이언트 리포터 2.
- **DB 스키마 변경 없음** — `venting_event.entry_source`는 원래 자유 문자열 컬럼(주석만 갱신).
- 배포 순서 무관: 백엔드가 먼저 나가면 신규 값이 즉시 수용되고, 클라이언트가 먼저
  나가도 기존과 동일하게 best-effort 실패(워치)·NULL 정규화(경로)로 동작이 나빠지지 않는다.
- 검증: 백엔드 pytest 14 passed, Android `:app:compileDebugKotlin` 통과,
  iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과.

## Non-Goals

- `client_ts` 클라이언트 전송 추가(서버 `created_at`으로 타이밍 분석 충분).
- 비로그인 유저의 기기 단위 익명 ID 도입(필요해지면 별도 change).
- 지표 대시보드/조회 API 추가 — 현 단계는 raw 수집 정합성만.
- 업데이트 팝업 "지금 해보기" CTA 구현(온보딩 change에서 다룸 — `whats_new` 값만 선등록).

## Capabilities

### Modified Capabilities
- `venting-analytics`: 이벤트 타입에 워치 진입을 포함하고, 진입 경로 허용 목록을
  클라이언트 실제 값과 일치시키며, 로그인 유저의 순 사용자 식별을 가능하게 한다.
