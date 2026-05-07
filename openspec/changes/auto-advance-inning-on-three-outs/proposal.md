## Why

현재 `backend/api/app/services.py` 의 `_normalize_state_for_three_outs()` 는 **API 응답 시점에만** `out >= 3` 조건으로 BSO=0 + inning advance 를 반환할 뿐, DB 에는 반영하지 않는다. 한편 크롤러는 네이버 라이브를 2~5초 간격 폴링하면서 받은 값을 그대로 백엔드로 전달한다. 네이버에서 BSO 리셋과 inning 텍스트 변경이 atomic 하지 않기 때문에 다음과 같은 흐름이 발생한다:

```
t=0  out=2, inning="3회초"
t=1  out=3, inning="3회초"   ← 크롤러 폴링이 잡지 못하고 지나가는 경우 흔함
t=2  out=0, inning="3회초"   ← BSO 만 리셋, inning 은 아직 그대로 ← 문제
t=3  out=0, inning="3회말"
```

`t=2` 가 DB 에 박히면 `out=0` 이라 응답 정규화도 발동하지 않아 inning 이 "3회초" 에 멈춘다. 또한 `t=1` 이 잡혀 응답에서 advance 되더라도 `t=2` 가 들어오는 순간 DB 가 `out=0, inning="3회초"` 로 덮어써져 advance 효과가 사라진다.

추가로 9회 이상 연장 룰(KBO 11회까지) 및 무승부 텍스트가 도메인에 반영되어 있지 않다.

## What Changes

- `backend/api/app/services.py`
  - KBO 회차 룰 상수화: `MAX_REGULATION_INNING=9`, `MAX_EXTRA_INNING=11`, `FINISHED_INNING_TEXT="경기 종료"`, `DRAW_INNING_TEXT="경기 종료 (무승부)"`.
  - `_advance_inning()` 제거, `_resolve_inning_after_three_outs(inning, home_score, away_score) -> (new_inning, should_finish)` 로 교체. 9~10회 점수 결정 / 11회말 / N회초 홈팀 리드 케이스에서 `should_finish=True` 반환.
  - `_finished_inning_label(home, away)` 추가: 동점이면 `"경기 종료 (무승부)"`, 아니면 `"경기 종료"`.
  - `upsert_game_from_snapshot()` 에 다음 로직 통합:
    - **B 안**: payload `out >= 3` 즉시 advance / 종료 결정. BSO/주자 0 으로 DB 저장.
    - **A 안**: 직전 `out >= 2` 이고 새 payload `out=0` 이며 inning 텍스트 동일하면 transition 으로 advance / 종료 결정.
    - **inning 후퇴 방지 가드**: LIVE 상태에서 advance 직후가 아니면, 새 payload inning 이 직전 DB inning 보다 진행도 낮으면 inning 텍스트만 보존(BSO/score 등은 갱신).
    - FINISHED 전이 시 `_finished_inning_label()` 로 `"경기 종료"` / `"경기 종료 (무승부)"` 분기.
  - `_normalize_state_for_three_outs()` / `build_game_state()` 시그니처에 점수 인자 추가(안전망 호환).
- `backend/api/tests/test_api.py`
  - 기존 `test_game_state_resets_ball_strike_when_three_outs` 어서션 갱신(out 도 0 으로).
  - 추가: 한국어 inning 즉시 advance / transition advance / 후퇴 방지 / 9회초 홈리드 종료 / 9회초 동점 advance / 9회말 동점 → 10회초 / 9회말 점수차 종료 / 11회말 무승부 라벨 / 11회말 점수차 종료.
- `openspec/specs/game-state/spec.md`
  - "BSO 카운트 관리" Requirement 를 "3아웃 시 이닝 자동 전환" 으로 확장하고, KBO 11회 룰 + walk-off 룰 + 무승부 라벨을 명시한다.

## Capabilities

### Modified Capabilities

- `game-state`
  - 3아웃 시 BSO 리셋만 보장하던 항목을 점수·연장 룰을 반영한 자동 이닝 전환 / 종료 / 무승부 라벨링까지 보장하도록 확장.

## Impact

- 백엔드: `services.py` 단일 모듈 변경. 외부 API 시그니처 변경 없음(응답 inning 텍스트가 더 빨리 정확해짐). FINISHED 시 동점이면 inning 필드가 `"경기 종료 (무승부)"` 로 옴.
- 클라이언트: iOS/Android/Watch 의 종료 감지 코드는 모두 `inning.contains("경기 종료")` 패턴이라 무승부 라벨도 그대로 종료로 인식됨(회귀 없음). 무승부 텍스트 자체는 inning 필드를 표시하는 화면에 자동 노출.
- DB 스키마: 변경 없음.
- 마이그레이션: 없음. 배포 즉시 효과.

### Non-Goals (별건으로 분리)

- 네이버 미들 윈도우(점수 변화에 의한 walk-off) 자체 추론. 현재는 이미 `_has_game_end_signal` ("승리투수/패전투수/세이브투수" 텍스트) 와 네이버 FINISHED 신호로 충분히 잡힌다.
- 클라이언트 UI 의 무승부 전용 시각 강조(스코어보드 색 변경 등). 현재는 텍스트 노출만 보장.
- 시즌 룰 변경 자동 반영. `MAX_EXTRA_INNING` 상수 1회 수정으로 충분하므로 스펙 외.

## Rollout

1. PR 머지 → Railway 자동 배포(백엔드 단일).
2. 다음 라이브 경기에서 다음을 확인:
   - "N회초/말 + out=3" 스냅샷이 들어오면 다음 회로 즉시 전환.
   - 9회말 점수차 경기는 종료 신호 도착과 무관하게 `inning="경기 종료"` 로 빠르게 정정.
   - 동점 무승부 종료 경기(드물지만 발생) 시 `inning="경기 종료 (무승부)"` 노출.
3. 회귀 모니터링: Railway 로그에서 `[three-outs-advance]` / `[three-outs-finish]` / `[inning-regress-guard]` 카운트 확인.

### Rollback

`git revert` 한 번. DB 변경 없으므로 스키마 작업 불필요.
