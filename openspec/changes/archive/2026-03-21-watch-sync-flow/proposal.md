## Why
기존 동기화 플로우가 모바일 팝업 중심이어서 워치 사용자가 능동적으로 관람 여부를 결정할 수 없었음.
워치 팝업 중심으로 전환하여 사용자 경험을 개선.

## What Changes
- 동기화 플로우를 "모바일 팝업 중심"에서 "워치 팝업 중심"으로 변경
- Data Layer 양방향 통신 경로 추가
- 워치 UI 반응형 체계 구축 (3종 디바이스 프로파일)

## Capabilities
### Modified Capabilities
- `mobile-app`: LIVE 전환 시 워치에 동기화 프롬프트 전송으로 변경
- `watch-app`: 동기화 동의 팝업 + 수락/거부 응답 플로우 추가
- `watch-app`: 3종 디바이스 프로파일(small_round, large_round, square) 반응형 UI

### New Capabilities
- `mobile-app`: MobileDataLayerListenerService, WearWatchSyncBridge 추가
- `watch-app`: WatchUiProfile 기반 40+ 디멘션 파라미터 자동 조정

## Impact
- apps/mobile — DataLayerListenerService, WearWatchSyncBridge, MainActivity
- apps/watch — MainActivity, WatchSyncResponseSender, LiveGameScreen, WatchUiProfile
- Data Layer 경로: /watch/prompt/current, /watch/sync-response/{timestamp}
