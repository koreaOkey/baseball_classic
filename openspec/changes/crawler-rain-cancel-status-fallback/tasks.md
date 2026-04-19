## 1. 크롤러 statusInfo 폴백

- [x] 1.1 `backend_sender.py`의 `_normalize_status`에 `status_info` 선택적 파라미터 추가 및 한국어/영어 키워드 폴백 로직 구현
- [x] 1.2 `build_snapshot_payload`에서 `game_data.get("statusInfo")` 전달

## 2. 디스패처 statusInfo 폴백

- [x] 2.1 `live_wbc_dispatcher.py`의 `_map_schedule_status`에 `status_info` 선택적 파라미터 추가 및 폴백 로직 구현
- [x] 2.2 `_schedule_game_to_snapshot`에서 `statusInfo` 전달
- [x] 2.3 `_should_skip_schedule_snapshot`에서 `statusInfo` 전달
- [x] 2.4 `_relay_is_available`에서 `statusInfo` 기반 terminal 판별 추가

## 3. 검증

- [x] 3.1 기존 테스트(`test_backend_sender.py`) 통과 확인
- [x] 3.2 statusInfo 폴백 시나리오 수동 테스트 (우천취소, 경기연기, statusCode 우선 등)
