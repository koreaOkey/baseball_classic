# Tasks — fix-venting-metrics-collection-gaps

## 1. 백엔드 허용 목록 정정
- [x] 1.1 `venting.py` `VALID_VENTING_EVENT_TYPES`에 `watch_room_enter` 추가
- [x] 1.2 `venting.py` `VALID_ENTRY_SOURCES` → `{home_card, live, loss_push, whats_new}` 교체
- [x] 1.3 `models.py` `VentingEvent` event_type/entry_source 주석 동기화

## 2. 리포터 토큰 첨부
- [x] 2.1 iOS `VentingEventsReporter.swift` — 세션 있으면 Bearer 첨부, 실패 시 익명 전송
- [x] 2.2 Android `VentingEventReporter.kt` — `currentSessionOrNull()` 토큰 첨부, 실패 시 익명 전송

## 3. 검증
- [x] 3.1 백엔드 테스트 추가: watch_room_enter 수용 / live·loss_push·whats_new 소스 보존 / Bearer→user_id 저장
- [x] 3.2 백엔드 `pytest tests/test_venting.py` 14 passed
- [x] 3.3 Android `:app:compileDebugKotlin` 통과
- [x] 3.4 iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과
- [ ] 3.5 배포 후: 실기기에서 room_enter/watch_room_enter가 entry_source·user_id와 함께 적재되는지 SQL 확인
