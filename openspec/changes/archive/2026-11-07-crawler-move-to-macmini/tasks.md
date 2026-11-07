# Tasks

- [x] 재정지 범위 확인: 11/5 03:05Z 마지막 성공, 이후 네이버 요청 전부 0바이트 타임아웃. 3차전(11/6)·4차전(11/7) SCHEDULED 고착, freshness 알림 정상 작동
- [x] 원인 판별: 집 공인 IP(121.138.76.13)에서 크롤러와 같은 위장 요청 OK → Railway IP 차단
- [x] 이전 대상 결정: spark 대신 맥미니 (검증된 IP, 모니터와 기기 분리) — 사용자 승인
- [x] 맥미니 `~/baseball_crawler` 준비: crawler rsync, venv(Python 3.14, curl_cffi 0.16.2), `run.sh` 래퍼, launchd plist(`plutil -lint` OK), 가드 경로로 래퍼 실행 확인
- [x] `crawler/start.sh` `CRAWLER_DISABLED` 가드 추가 (macOS 호환을 위해 `sleep infinity` 대신 반복 sleep)
- [x] spark `~/baseball_crawler`에 예비 준비(venv, systemd unit 파일, 미가동)
- [x] 맥미니 `.env` 작성 (사용자 실행, 권한 600)
- [x] 맥미니에서 위장 요청 확인 후 launchd 에이전트 등록·시작 (사용자 실행, 11/7 21:39 `state = running`)
- [x] Railway crawler_staging: `CRAWLER_DISABLED=1` 설정 + `railway down` (사용자 실행, 배포 a3c6cb43 REMOVED)
- [ ] 이 커밋 staging 푸시 (auto mode가 production deploy로 차단 → 사용자 실행), Railway 재배포 시 대기 로그 확인
- [x] 맥미니 import 성공: 3차전 `77771106KTSS02026` KT 1-5 삼성, 4차전 `77771107KTSS02026` KT 5-10 삼성 FINISHED 반영, 팀 순위 동기화, 네이버 일정·경기 상세 요청 정상
- [ ] relay(`/relay?inning=N`) 요청만 맥미니에서 타임아웃 2회 — 엔드포인트별 진단(`naver_diag_relay.py`, 사용자 실행) 후 대응. 라이브 중계의 핵심 경로
- [ ] 11/8 14:00 한국시리즈 5차전 라이브 ingest 확인
- [ ] 선택: 맥미니 이더넷 연결, `sudo pmset -a autorestart 1`, 자동 로그인 확인
- [x] Android/iOS, 모바일/워치 영향 확인: 백엔드 무변경이라 영향 없음
