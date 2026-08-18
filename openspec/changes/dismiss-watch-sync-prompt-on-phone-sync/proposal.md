# Dismiss Watch Sync Prompt on Phone Sync Start (폰 관람 시작 시 워치 팝업 자동 내림)

## Why

iOS에서 응원팀이 아닌 경기를 광고 관람 후 워치 동기화하면, 워치에는 응원팀 경기의
"관람하시겠습니까?" 팝업이 관람 화면 위에 남는다. 원인은 응원팀 LIVE 감지 경로 2개
(워치 독립 폴러 `WatchGamePoller`, 폰 자동 감지 `pollGames`)가 "지금 다른 경기를
동기화 중"이라는 상태를 확인하지 않고, 워치의 자동 해제(`handleGameData`)도
**같은 gameId**의 데이터가 도착할 때만 동작하기 때문.

2026-08-18 사용자 결정: 팝업 억제(감지 자체 차단)나 배너 강등이 아니라,
**폰에서 워치 관람 토글이 켜지면 — 워치가 아직 수락하지 않았어도 — 팝업을 내리는 것**만 한다.
응원팀 경기 감지·프롬프트 로직 자체는 유지.

## What Changes

### iOS Phone

- `WatchGameSyncManager.sendWatchSyncPromptDismiss()` 신규 — `watch_sync_prompt_dismiss`
  메시지 전송 (sendMessage 실패·unreachable 시 transferUserInfo 폴백).
- `BaseHapticApp` `.onChange(of: syncedGameId)` — 새 gameId가 세팅되어 관람이 시작되는
  모든 경로(광고 게이트 통과, 워치 수락 응답 등)에서 dismiss를 1회 전송.

### watchOS

- `WatchConnectivityManager.handleMessage` — `watch_sync_prompt_dismiss` 수신 시
  어떤 경기의 팝업이든 응답 없이 `watchSyncPrompt = nil`.
- `handleGameData` — 팝업과 **다른** 경기의 LIVE 데이터가 도착하면 응답 없이 팝업만 내림
  (dismiss 신호가 지나간 뒤 폴러가 뒤늦게 팝업을 띄우는 레이스 커버).
  같은 경기 자동 수락(기존 동작)은 유지.

## Capabilities

### watch-sync

- 폰에서 어떤 경기든 워치 관람이 시작되면, 워치에 떠 있던(또는 직후에 뜨는) 관람 팝업은
  사용자 수락 없이 자동으로 사라진다.
- 같은 경기의 데이터 도착 시 자동 수락(기존), 다른 경기의 LIVE 데이터 도착 시 무응답 해제(신규).
- 응원팀 경기 LIVE 감지와 프롬프트 발송 자체는 변경 없음 — 관람 중이 아닐 때는 기존과 동일하게 팝업 노출.

## Impact

- iOS: `WatchSync/WatchGameSyncManager.swift`, `BaseHapticApp.swift`
- watchOS: `WatchSync/WatchConnectivityManager.swift`
- Android/Wear OS·백엔드 변경 없음. **Android 동등성(같은 증상 존재 추정)은 후속 change.**

## Non-Goals (후속)

- Android/Wear OS 동등성 적용.
- 다른 경기 관람 중 응원팀 경기 시작을 알리는 대체 UI(배너/노티) — 현재는 조용히 내림.
- 폰 자동 감지(`pollGames`)·워치 폴러의 프롬프트 발송 조건 자체 수정.
