## Why

Wear OS 앱 성능 감사(2026-07-16)에서 라이브 경기 중 매 투구(경기당 250~400회)마다 반복되는 낭비가 확인됐다: (1) `startForegroundService()` 재호출로 채널 생성·알림 빌드·`startForeground()`가 매번 재실행되고 FINISHED 미수신 시 FGS가 무기한 생존, (2) ongoing 알림을 두 번 빌드하고 정작 OngoingActivity 메타데이터가 없는 쪽을 게시(워치페이스 칩 버그)하며 내용이 같아도 매번 재게시, (3) 같은 브로드캐스트를 리시버 2곳이 중복 처리, (4) 앰비언트에서도 30초 HTTP 폴링이 전체 경기 목록을 재다운로드, (5) 이벤트마다 deprecated SCREEN_BRIGHT 웨이크락이 `setTurnScreenOn`과 중복, (6) ExoPlayer가 이벤트 사이 MediaCodec 점유, (7) `is_live` 가드가 죽은 코드라 관람 중에도 팝업·화면 웨이크 발생, (8) 미참조 팀 로고 PNG ~1.9MB.

실시간성·앱 유지에는 영향이 없는 순수 낭비 제거만 포함한다. `FLAG_KEEP_SCREEN_ON` 제거와 stale isLive 화면 정책은 별도 change(`enable-wear-ambient-during-live`)로 분리했다.

## What Changes

- FGS: 실행 중이면 `start()` no-op(`@Volatile isRunning`), 5분 주기 워치독이 `KEY_GAME_UPDATED_AT` 기준 30분 무수신 시 `stopSelf()` (이후 새 데이터 오면 재시작).
- ongoing 알림: 단일 빌더로 OngoingActivity 메타데이터가 붙은 알림을 게시, (title, text) 동일 시 재게시 생략.
- `ACTION_GAME_UPDATED` 리시버를 Compose 쪽 1개로 통합 (상태 갱신 + 알림 갱신).
- 폴링: 앰비언트 중 3분 간격(해제 시 CONFLATED 채널 시그널로 즉시 재개), 내 팀 경기 특정 후 `GET /games/{gameId}` 단건 조회(추적 경기 종료 시 더블헤더 대응 위해 목록 복귀).
- 이벤트 웨이크락 제거, `setTurnScreenOn(true)`/`setShowWhenLocked(true)`에 위임.
- ExoPlayer 지연 생성 + 클립 종료 시 `stop()`+`clearMediaItems()`로 코덱 반환.
- `KEY_IS_LIVE`를 handleGameData에서 실제로 기록해 "이미 관람 중" 가드 복원.
- 설정 수신 prefs 쓰기를 단일 Editor 1회 apply로 배칭.
- 미참조 팀 로고 PNG 10개(~1.88MB) 삭제, 의존성 없는 tiles/protolayout ProGuard keep 제거.

## Capabilities

### Modified Capabilities

- `watch-android`: 라이브 관전 성능 정책(FGS 수명, 알림 재게시, 폴링, 코덱 점유)을 명확히 한다.

## Impact

- `apps/watch/app/src/main/java/com/basehaptic/watch/GameForegroundService.kt`, `DataLayerListenerService.kt`, `MainActivity.kt`, `WatchGamePoller.kt`, `WatchFinishedGameCache.kt`, `ui/components/LiveGameScreen.kt`
- `apps/watch/app/src/main/res/drawable/` 팀 로고 PNG 10개 삭제
- `apps/watch/app/proguard-rules.pro`
- 백엔드 변경 없음 (`/games/{game_id}` 기존 엔드포인트 사용)
- 실기기 검증 1건 필요: 화면 꺼진 상태 이벤트 수신 시 화면 웨이크 (웨이크락 제거 후)
