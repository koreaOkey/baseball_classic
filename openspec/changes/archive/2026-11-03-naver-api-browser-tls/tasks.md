# Tasks

- [x] 백엔드 `/health`·`/ready`·`/games`로 데이터 정지 범위 확인 (마지막 갱신 10/25 15:05Z, 10/26 이후 경기 0건)
- [x] Railway CLI로 서비스 상태·크롤러 로그 확인 (3개 서비스 SUCCESS, 네이버 Read timeout·RemoteDisconnected)
- [x] 맥에서 같은 증상 재현, 네이버 웹 번들의 API 호스트 변경 여부 확인 (변경 없음)
- [x] 사용자가 직접 브라우저 TLS 위장 요청 200 확인, 3번 방식 선택
- [x] `crawler/naver_http.py` 추가 및 네이버 GET 호출부 교체 (`crawler.py` 1곳, `live_wbc_dispatcher.py` 5곳)
- [x] `crawler/requirements.txt`에 `curl_cffi==0.16.2` 추가, Linux py3.12 휠 확인
- [x] `crawler/test_naver_http.py` 예외 변환 단위 테스트 추가, 크롤러 테스트 83개 통과 (uv, Python 3.12)
- [x] Android/iOS, 모바일/워치 영향 확인: API·WS 프로토콜 무변경, 클라이언트 영향 없음
- [ ] 로컬 실네트워크 스모크: 수정된 크롤러 코드로 11/3 일정과 한국시리즈 1차전 중계 조회 (사용자 실행)
- [ ] staging 커밋·푸시 → Railway `crawler_staging` 재빌드, 로그에서 `[import]` 성공과 `windows_loaded` 확인
- [ ] 누락 경기(플레이오프 2~5차전, 한국시리즈 1차전) 백필 여부 확인
- [ ] 다음 한국시리즈 경기에서 라이브 ingest·푸시·워치 수신 확인
- [ ] 후속: spark Railway 모니터에 데이터 최신성 점검 추가 (헬스는 정상인데 경기 데이터만 멈추는 장애 감지)
