# Tasks

- [x] `crawler/backend_sender.py` `pitchesThrown` 매핑에 `"ballCount"` 키 추가 (lineup pitcher 레코드 기준)
- [x] `crawler/test_backend_sender.py` 회귀 테스트 추가 (`test_pitcher_stats_pick_up_ballcount_as_pitches_thrown`)
- [x] 로컬 테스트 통과: `pytest crawler/test_backend_sender.py` 8 passed
- [x] Railway 크롤러 서비스 재배포
- [x] production `/games/{LIVE_ID}/state` 가 `pitcherPitchCount > 0` 으로 갱신되는지 확인 (2026-05-08, 사용자 확인)
- [ ] iOS/Android 워치 라이브 화면에서 투구수 카운트업 실기기 확인 (앱 빌드 배포 후)
