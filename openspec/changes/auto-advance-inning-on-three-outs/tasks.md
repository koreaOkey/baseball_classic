# Tasks

## Backend
- [x] `backend/api/app/services.py`
  - [x] KBO 회차/라벨 상수 추가
  - [x] `_parse_inning`, `_inning_progress_key` 헬퍼 추가
  - [x] `_advance_inning` 제거 → `_resolve_inning_after_three_outs(inning, home, away)` 도입
  - [x] `_finished_inning_label(home, away)` 추가 (무승부 분기)
  - [x] `_normalize_state_for_three_outs` / `build_game_state` 점수 인자 추가
  - [x] `upsert_game_from_snapshot` 에 B(out>=3 즉시) + A(transition) + 후퇴 방지 가드 + FINISHED 라벨 분기 통합

## Tests
- [x] `backend/api/tests/test_api.py`
  - [x] `test_game_state_resets_ball_strike_when_three_outs` 어서션 갱신 (out=0)
  - [x] `test_three_outs_advances_inning_in_regulation`
  - [x] `test_three_outs_transition_advances_when_payload_resets_only_bso`
  - [x] `test_inning_regress_guard_keeps_advanced_inning`
  - [x] `test_ninth_top_three_outs_finishes_when_home_leads`
  - [x] `test_ninth_top_three_outs_advances_when_tied`
  - [x] `test_ninth_bottom_three_outs_advances_to_extra_when_tied`
  - [x] `test_ninth_bottom_three_outs_finishes_when_score_diff`
  - [x] `test_eleventh_bottom_three_outs_draw_label`
  - [x] `test_eleventh_bottom_three_outs_finishes_with_score_diff`
- [x] `pytest tests/test_api.py` 전체 통과 (33/33)

## Spec
- [x] `openspec/specs/game-state/spec.md` 의 "BSO 카운트 관리" Requirement 를 "3아웃 시 이닝 자동 전환" 으로 확장 (delta 적용)

## Verification (배포 후)
- [ ] 운영 라이브 경기에서 "N회초/말 + out=3" 즉시 다음 회 전환 확인
- [ ] 9회말 점수차 경기 종료 시 `inning="경기 종료"` 신속 노출 확인
- [ ] 무승부 종료 경기 발생 시 `inning="경기 종료 (무승부)"` 노출 확인
- [ ] Railway 로그에서 `[inning-regress-guard]` 가 빈번하지 않은지(기대치: 가끔 발생, 폭증 아님) 확인

## Non-Goals (별도 change 로 분리)
- 점수 변화 기반 walk-off 자동 종료 추론
- 클라이언트 UI 의 무승부 전용 시각 강조
