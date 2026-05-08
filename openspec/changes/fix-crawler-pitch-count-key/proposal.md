# fix-crawler-pitch-count-key

## Why

`add-watch-pitch-count` 이후에도 라이브 워치 화면에 항상 `0구` 만 표시되는 회귀.

production `/games/{id}/state` 를 조회하면 모든 LIVE 경기에 대해 `pitcherPitchCount: 0` 이 내려오고 있다 (예: 2026-05-08 한화 박준영 1회 16구 던지는 중인데도 0). DB → 백엔드 → iOS/Android 폰 → 워치 까지의 경로는 모두 정상이며, 원인은 한 단계 위 — Naver 릴레이 API 의 `lineup.pitcher` 레코드가 누적 투구수를 **`ballCount`** 키로 노출하는데 크롤러는 `["np", "pc", "pitchCount", "pitchesThrown"]` 만 보고 있어서 항상 0 이 백엔드로 흘러들어가고 있었다.

실측 페이로드 (Naver `/schedule/games/.../relay?inning=1`):

```json
{"name":"박준영","pcode":"52731","seqno":1,"ballCount":16,"inn":"1.0","bb":1,"kk":3, ...}
```

## What Changes

- `crawler/backend_sender.py`
  - `pitcherStats[].pitchesThrown` 매핑 키 리스트의 **첫 번째**에 `"ballCount"` 를 추가. (KBO Naver 가 실제로 사용하는 키, 다른 별칭은 fallback 으로 유지)

- `crawler/test_backend_sender.py`
  - `build_snapshot_payload` 가 lineup pitcher 의 `ballCount` 를 `pitchesThrown` 으로 옮기는지 검증하는 회귀 테스트 1건 추가.

## Impact

- **코드**: 크롤러 1개 파일 1줄(+주석), 테스트 1개 추가.
- **데이터 흐름**: 크롤러 다음 폴링 사이클부터 `GamePitcherStat.pitches_thrown` 이 실제 값으로 갱신 → `build_game_state` 가 활성 투수의 누적 투구수를 응답에 포함 → 폰 → 워치까지 자동 반영. 클라이언트 변경 없음.
- **위험도**: 낮음. `ballCount` 가 누락된 과거 페이로드에서는 다른 별칭/0 으로 fallback. 다른 곳의 `ballCount` (게임 상태의 볼 카운트) 와는 컨텍스트가 다르므로 충돌 없음 (lineup pitcher 레코드 한정).

## Non-Goals

- 백엔드/iOS/Android 코드 변경 없음 (회로 자체는 정상이었음).
- DB 스키마/마이그레이션 변경 없음.
- 투구수 리미트 알림, 분석 통계 등은 별건.

## Rollout

1. 크롤러 PR 머지 → Railway 크롤러 서비스 자동 재배포.
2. 다음 폴링 사이클에서 production `/state` 응답의 `pitcherPitchCount` 가 0 → 실제 누적 값으로 전환되는지 확인.
3. 라이브 워치 화면(iOS/Android) 에서 투구수 카운트업 정상 동작 확인.

### Rollback

- `git revert` 1회. DB/스키마 영향 없음.
