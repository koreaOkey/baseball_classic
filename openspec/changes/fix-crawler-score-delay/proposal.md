# fix-crawler-score-delay

## Why

홈런/안타 등 득점 이벤트가 발생했을 때, 이벤트 알림은 즉시 울리지만 점수가 다음 폴링 사이클(최대 30초 뒤)에야 반영되는 버그.

크롤러가 `game_data` API와 `relay` API를 순차 호출하는데, `game_data`의 점수가 항상 우선되어 relay의 `currentGameState`에 이미 반영된 최신 점수가 무시됨.

## What Changes

- `crawler/backend_sender.py`의 점수 결정 로직을 `game_data` 우선 fallback에서 `max()` 비교로 변경
- 야구 점수는 감소하지 않으므로 `max(game_data, relay)` 사용이 안전

## Impact

- **코드**: `crawler/backend_sender.py` 1개 파일, 2줄 → 8줄
- **위험도**: 낮음 (점수는 단조증가)

## Non-Goals

- 백엔드/클라이언트 변경 없음
- 폴링 주기 변경 없음
