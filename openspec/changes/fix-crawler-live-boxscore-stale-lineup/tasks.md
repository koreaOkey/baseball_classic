# Tasks

- [x] `crawler/backend_sender.py`의 `_extract_latest_entry`를 `_relays_latest_first` 순서로 lineup/entry 선택하도록 변경
- [x] `crawler/test_backend_sender.py`에 회귀 테스트 추가 (stale 상위 이닝 vs 신선한 현재 이닝)
- [x] 크롤러 테스트 통과 확인 (`pytest crawler` 20 passed)
- [ ] Railway 크롤러 서비스 재배포
- [ ] 라이브 경기에서 박스스코어 실시간 갱신 검증 (다음 경기: 19:00 KST)
