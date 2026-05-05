## Why

Wear OS 워치에서 경기 종료 후 같은 날에는 최종 스코어를 다시 확인할 수 있어야 하지만, 다음날이 되면 이전 경기 결과가 계속 남아 있어 사용자가 오늘 진행 중인 경기로 오인할 수 있다.

기존 구현은 다음날 자정 이후 종료 경기를 화면에서 숨기지만, 저장된 경기 캐시는 그대로 남겨 둔다. 앱 화면과 타일이 같은 기준으로 만료 여부를 판단하고, 만료된 종료 경기 캐시를 실제로 삭제하도록 정리한다.

## What Changes

- Wear OS 앱 화면에서 종료 경기 캐시가 다음날 KST 자정을 지나면 삭제되고 경기 없음 UI로 전환된다.
- Wear OS 타일에서도 동일한 기준으로 만료된 종료 경기 캐시를 삭제하고 경기 없음 레이아웃으로 전환된다.
- 만료 기준은 기기 로컬 타임존이 아니라 KBO 경기 기준인 Asia/Seoul 자정으로 고정한다.

## Capabilities

### Modified Capabilities

- `watch-ux`: 종료 경기 캐시 만료 및 경기 없음 UI 전환 정책을 명확히 한다.

## Impact

- `apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt` — 앱 화면의 종료 경기 캐시 만료 처리
- `apps/watch/app/src/main/java/com/basehaptic/watch/tile/GameTileService.kt` — 타일의 종료 경기 캐시 만료 처리
- `apps/watch/app/src/main/java/com/basehaptic/watch/WatchFinishedGameCache.kt` — 공용 만료/삭제 정책
