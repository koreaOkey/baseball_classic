# Tasks

- [x] 1.1 `crawler/backend_sender.py`에 `_detect_fielding_error(text)` 추가 (포지션 토큰 표 + '실책' 바로 앞 최근접 포지션 채택 + 오탐 제외: 실책성/무실책/실책 없이)
- [x] 1.2 이벤트 빌드 루프에서 감지 시 `metadata["isError"/"errorPosition"/"errorPositionCode"]` 부착 — `event_type` 불변
- [x] 2.1 `crawler/test_backend_sender.py` 단위 테스트: 최근접 포지션 채택 / 외야 구체 토큰 우선 / 실책성·무실책 제외 / 키워드 없음 / 포지션 미상 플래그
- [x] 2.2 스냅샷 통합 테스트: 무득점 실책이어도 이벤트 전송 + metadata 부착 + defenseTeam=실책팀 + event_type 불변 확인
- [x] 2.3 크롤러 테스트 전체 통과 (`pytest crawler` 68 passed)
- [x] 3.1 Railway 크롤러 서비스 재배포 — staging push(22779948) → overflowing-solace/crawler 배포 18a8a60d SUCCESS(2026-08-17 12:16 UTC), 직전 e6c76a66 REMOVED
- [ ] 3.2 라이브/완료 경기에서 실책 발생 시 `game_events.payload_json`에 `isError/errorPosition` 적재 확인
- [ ] 4.1 (후속 change) `venting._select_candidates`가 실책 metadata를 소비해 fielder regret 후보 생성 + 클라 TargetRow fielder 렌더
