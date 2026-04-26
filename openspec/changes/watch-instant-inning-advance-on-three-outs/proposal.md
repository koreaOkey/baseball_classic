## Why

워치(iOS Watch / Android Wear OS)에서 3out이 발생하면 ball/strike/out은 즉시 0으로 정규화되어 표시되지만, **inning 필드는 백엔드의 `state` 메시지가 도착할 때까지 이전 값을 유지**한다. 사용자가 워치를 보면 "3out인데 아직 같은 이닝"으로 표시되어 다음 이벤트가 들어와야 비로소 다음 이닝으로 갱신되는 갭이 생김.

ball/strike/out은 워치 측에서 `out >= 3`일 때 0으로 정규화하는 로직이 이미 있어 즉시 반영되지만, inning은 백엔드가 보내준 문자열을 그대로 사용하기 때문 (iOS: `WatchConnectivityManager.swift:144-148`, Android: `DataLayerListenerService.kt:110-114`).

## What Changes

- 워치 클라이언트(iOS/Android 양쪽)가 `out >= 3 && !isFinished` 조건일 때 inning 문자열을 자체적으로 다음 이닝으로 전진:
  - `"N회초"` → `"N회말"` (9회초 → 9회말도 포함; 9회초 종료 후 경기 종료 판정은 백엔드의 `isFinished` 신호로 도착)
  - `"N회말"` (N < 9) → `"(N+1)회초"`
  - `"N회말"` (N >= 9) → 원본 유지 (점수/연장룰 판정 필요 — 백엔드 state 신뢰)
  - 형식 불일치(예: "경기 종료", "", 비정형 문자열) → 원본 유지
- 백엔드 변경 없음. `_normalize_state_for_three_outs()`의 단순 전진 로직은 그대로 유지.
- 9회 이상 정확한 종료/연장 판정은 별도 change로 분리 (메모리 `project_backend_end_of_game_judgment_todo.md` 참고).

## Capabilities

### Modified Capabilities
- `live-game-watch-display`: 워치에 표시되는 inning 값이 백엔드 state 도착 전에도 1~8회 한정으로 자동 전진. 외부 통신 동작은 동일.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 워치가 잘못된 이닝으로 전진 | 정규식 `^(\d+)회(초\|말)$` 매칭 실패 시 원본 유지. 백엔드 `_advance_inning()`과 동일 정규식 |
| 9회 이상에서 잘못된 "경기 종료" 표기 | 9회말 이상은 자체 전진하지 않음. 백엔드 `isFinished` 플래그로만 종료 판정 |
| 9회초 3out 시점 짧은 갭 동안 "9회말 + 0/0/0" 표시 (실제로는 경기 종료인데) | 기존에도 동일 동작. 이번 변경으로 새로 생기는 회귀 아님. Plan B로 별도 처리 예정 |
| 백엔드 state 도착 시 워치의 자체 전진 값과 다를 가능성 (예: 백엔드가 늦게 다른 이닝으로 보냄) | 새 state 도착 시 정상적으로 덮어씀 (gameData를 통째 갱신). 일시적 차이뿐 |

## Status

- [x] 구현 완료 (iOS / Android 워치)
- [ ] 실기기 검증
- [ ] 커밋

## 관련 메모리

- `project_backend_end_of_game_judgment_todo.md` — 9회 이상 정확한 종료/연장 판정은 백엔드 작업으로 분리 (Plan B 보류)
