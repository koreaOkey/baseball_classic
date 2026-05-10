# Realtime Specification (delta)

## MODIFIED Requirements

### Requirement: 하이브리드 Push + Pull 아키텍처
시스템은 평소 Push, 끊김 시 Pull 복구 방식을 사용해야 한다(MUST).

silent push 페이로드는 워치 라이브 화면이 폰 백그라운드 상황에서도 동일한 정보를 그릴 수 있도록 `/games/{gameId}/state` 응답과 동일한 키 집합을 포함해야 한다. 특히 `pitcher_pitch_count` 는 누락 시 클라이언트가 마지막 값을 잃기 때문에, push 페이로드에 항상 포함되어야 한다.

규칙:
- silent push `base_payload` 는 game_id, 양 팀 표시명, score, status, inning, ball/strike/out, base_first/second/third, pitcher, batter, **pitcher_pitch_count** 를 포함한다.
- `pitcher_pitch_count` 는 nullable 이며, null 표현은 sentinel `-1` 을 사용한다 (iOS WatchGameSyncManager wire format 과 동일).
- 이벤트가 동반될 때는 동일 base 위에 `event_type`, `event_cursor` 를 추가한다.

#### Scenario: 정상 상태 (Push)
- GIVEN WebSocket 연결이 정상일 때
- WHEN 새 이벤트가 발생하면
- THEN 즉시 Push로 전달한다

#### Scenario: 재연결 복구 (Pull)
- GIVEN 연결이 끊겼다 복구되었을 때
- WHEN 재연결 직후
- THEN /games/{gameId}/events?after={lastCursor} + /games/{gameId}/state 1회 Pull로 보정한다

#### Scenario: silent push 페이로드 완정성
- GIVEN LIVE 경기에서 활성 투수가 16구를 던졌을 때
- WHEN 백엔드가 구독자에게 silent push 를 발송하면
- THEN APNs/FCM 페이로드에 `pitcher_pitch_count: 16` 이 포함되어 폰 백그라운드에서도 워치가 투구수를 표시할 수 있다

#### Scenario: silent push pitcher_pitch_count nullable
- GIVEN 활성 투수의 `GamePitcherStat` 행이 없거나 경기가 SCHEDULED 일 때
- WHEN silent push 가 발송되면
- THEN 페이로드의 `pitcher_pitch_count` 는 sentinel `-1` 로 직렬화된다 (워치는 -1 을 nil 로 디코딩해 투구수 영역을 미표시)
