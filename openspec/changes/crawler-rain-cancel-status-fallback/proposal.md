## Why

네이버 API가 우천 취소 시 `statusCode`를 변경하지 않고 `statusInfo`만 "우천취소"로 바꾸는 경우, 크롤러/디스패처가 이를 감지하지 못해 게임카드가 SCHEDULED 상태로 남는 버그가 있다.

## What Changes

- 크롤러(`backend_sender.py`)의 `_normalize_status`에 `statusInfo` 폴백 로직 추가
- 디스패처(`live_wbc_dispatcher.py`)의 `_map_schedule_status`에 `statusInfo` 폴백 로직 추가
- 디스패처의 `_relay_is_available`, `_should_skip_schedule_snapshot`, `_schedule_game_to_snapshot`에서 `statusInfo` 전달
- `statusCode`가 매핑되지 않을 때 한국어("우천취소", "경기취소", "경기연기", "노게임") 및 영어 키워드로 취소/연기 판별

## Capabilities

### New Capabilities

(없음 — 기존 상태 매핑 로직의 버그 수정)

### Modified Capabilities

(없음 — 스펙 수준 요구사항 변경 없이 크롤러 내부 구현 수정)

## Impact

- `crawler/backend_sender.py` — `_normalize_status` 시그니처 변경 (선택적 `status_info` 파라미터 추가)
- `crawler/live_wbc_dispatcher.py` — `_map_schedule_status` 시그니처 변경 + 호출부 4곳 수정
- 앱(iOS/Android) 및 백엔드 변경 없음
