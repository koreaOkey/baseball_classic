# Apply Event Filter to Wear OS Visual Effects

## Why

이벤트 필터로 끈 이벤트(예: 아웃)가 Wear OS 워치에서는 햅틱만 차단되고 시각 효과는 그대로 노출됐다. `DataLayerListenerService`가 필터 검사 없이 `saveLatestEvent`를 무조건 호출해서, 이를 읽는 전체화면 펭귄 애니메이션·이벤트 오버레이·워치페이스 ongoing 칩 라벨("아웃" 등)이 모두 필터를 무시했다. iOS 워치는 차단 이벤트를 `latestEventType`에 저장하지 않아 햅틱·애니메이션·오버레이가 함께 꺼진다 (`WatchConnectivityManager.swift` 저장 게이트). openspec tune-event-filter-defaults-for-long-look task 5.4(Wear ongoing 차단 이벤트 skip) 미구현 건이기도 함.

## What Changes

- `DataLayerListenerService` 두 이벤트 수신 경로(게임 데이터 동봉 이벤트 / 단독 햅틱 이벤트) 모두에서 `isEventTypeAllowedByFilter` 를 저장 전에 검사 — 차단 이벤트는 `saveLatestEvent`·햅틱·GAME_UPDATED 브로드캐스트를 모두 skip.
- 저장 게이트 하나로 소비자 셋(전체화면 애니메이션, `WatchEventOverlay`, ongoing 칩 `eventTypeToKorean` 라벨)이 일괄 차단됨 — iOS 와 동일 동작.
- VICTORY·MOUND_VISIT 등 필터 매핑 밖 이벤트는 기존대로 항상 허용.

## Capabilities

### watch-event-filter

- 필터로 끈 이벤트는 Wear OS 에서 햅틱·전체화면 애니메이션·오버레이·ongoing 칩 라벨 어디에도 나타나지 않는다.
- 게임 데이터(점수·이닝·BSO)는 이벤트 차단과 무관하게 계속 갱신된다.

## Impact

- `apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt`
- tune-event-filter-defaults-for-long-look 의 Wear task 5.4 해소. iOS·백엔드 변경 없음.
