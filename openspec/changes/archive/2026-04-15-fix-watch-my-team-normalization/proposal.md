## Why
2026-04-05 커밋 `30720af`("자잘한 수정")이 워치 `WatchConnectivityManager`에 `displayTeamName`을 추가하면서 `home_team`/`away_team`을 마스코트("베어스", "트윈스", "랜더스"...)로 변환하기 시작했다. 그러나 `my_team`은 변환되지 않고 팀 코드("DOOSAN", "LG", "SSG"...) 그대로 저장되어, [BaseHapticWatchApp.swift:221](../../../ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift#L221)의 단순 uppercase 비교 `myTeam.uppercased() == game.homeTeam.uppercased()`가 항상 실패했다.

그 결과 응원팀 HIT/HOMERUN/SCORE 이벤트에서 `isMyTeamBatting=false`로 평가되어 [라인 248의 else 분기](../../../ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift#L248)로 빠져 **영상 대신 아이콘 오버레이만 표시**되는 회귀가 발생. 사용자 ~500명 운영 중 발견. 앱이 이미 App Store 배포된 상태이므로 즉시 hotfix는 백엔드에서만 가능.

### 첫 시도(실패)
같은 날 먼저 iPhone `.update` WS 경로([BaseHapticApp.swift:511](../../../ios/mobile/BaseHaptic/BaseHapticApp.swift#L511))의 `state.homeTeamId.teamName`(마스코트) 버그를 원인으로 판단하고 백엔드 broadcast 형식을 `events`+`state` 두 메시지로 분리했다 (deployment `998f07af`). 그러나 사용자가 앱을 **백그라운드**에서 테스트한다는 정보가 추가되면서, 백그라운드 경로는 WS가 아니라 APNs silent push → 워치 직접 수신이라 broadcast split이 효과 없음이 확인됨. 로컬 revert 후 진짜 원인(워치 `displayTeamName` + `my_team` 비동기)으로 재조사.

## What Changes
- [backend/api/app/main.py](../../../backend/api/app/main.py) `_send_push_for_game_events`
  - `_TEAM_CODE_TO_MASCOT` 10개 구단 매핑 테이블 신설 (DOOSAN→베어스, LG→트윈스, ...)
  - `_normalize_my_team_for_watch(raw)` 헬퍼 — 팀 코드 입력 시 마스코트로 변환, 미매칭 시 원본 유지 (멱등성 보장)
  - `_fanout` 내부 payload 생성 시 `"my_team": info["my_team"] or ""` → `_normalize_my_team_for_watch(info["my_team"])`로 교체
- 워치의 `displayTeamName`은 멱등이라 `home_team`도 마스코트가 되고 양쪽 형식이 일치 → 비교 성립 → 영상 재생

## Non-Goals
- **iPhone foreground → WCSession → 워치 경로 수정** — [BaseHapticApp.swift:445,525](../../../ios/mobile/BaseHaptic/BaseHapticApp.swift#L445)가 `selectedTeam.rawValue`("DOOSAN") 그대로 워치로 보내고, 워치는 WCSession 메시지의 `my_team`을 변환 없이 `syncedTeamName`에 저장. 이 경로는 백엔드를 거치지 않아 이번 hotfix로 해결 불가. 다음 iOS 릴리즈에서 별도 수정 (Foreground 사용자는 iPhone 앱 켠 상태에서만 영향).
- **iPhone `.update` WS 케이스 팀명 누락 수정** — [BaseHapticApp.swift:511-512](../../../ios/mobile/BaseHaptic/BaseHapticApp.swift#L511-L512) `state.homeTeamId.teamName`(마스코트) → `state.homeTeam` 수정. 다음 iOS 릴리즈.
- **워치 팀명 비교 로직 근본 교체** — [BaseHapticWatchApp.swift:221-222](../../../ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift#L221-L222) 단순 uppercase 매칭을 `Team.fromBackendName` 패턴 정규화로 교체. 다음 iOS 릴리즈.
- **`_normalize_my_team_for_watch` 제거** — 위 3건이 릴리즈되고 구버전 앱 사용자가 충분히 업데이트된 후에만 제거 가능.

## Verification
- 라이브 경기 백그라운드 상태에서 응원팀 안타/홈런/득점 이벤트 시 워치 영상 재생 확인
- 상대팀 이벤트는 기존대로 아이콘 오버레이만 (회귀 없음)
- 10개 구단 모두에서 정상 동작 확인 (매핑 테이블 완전성)
- iPhone foreground 경로는 여전히 미수정 상태로 동작 확인 (Non-Goal 범위)
