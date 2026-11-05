# Tasks

- [x] Railway 7일 메트릭·플랜 문서로 비용 구조 확인 (RAM 85%, Free 플랜 불가, Hobby 적합 조건 RAM ≤ 0.35 GB)
- [x] 메트릭 차분으로 메모리 계단식 상승(경기 밤 단위) 패턴 확인
- [x] 단일 워커 정합성 확인 (event_bus 직접 브로드캐스트 + Redis 자기 메시지 필터, LA 발송 락은 Redis 기반)
- [x] Railway 시작 명령 `--workers 1` 적용
- [x] 환경변수 `MALLOC_ARENA_MAX=2`, `BASEHAPTIC_DB_MAX_OVERFLOW=14` 적용 (MCP 차단 → Railway CLI로 적용)
- [x] 배포 c0674924 검증: 서버 프로세스 1개, Redis SUBSCRIBED 1회, /health·/health/verbose·/games 200, WS state 프레임·pong
- [x] `infra/staging/backend.env.example` 실제 값 반영
- [x] Android/iOS, 모바일/워치 영향 확인: API·WS 프로토콜 무변경, 클라이언트 영향 없음
- [x] spark 모니터 cron 추가: `/home/lee/baseball_monitor/railway_monitor.py` + `run_railway.sh`, 매일 09:00 KST 텔레그램(사용자 현황과 같은 채널) — 헬스·WS·메모리·CPU·월 환산 usage·Hobby $5 판정
- [x] spark `.env`에 `RAILWAY_PROJECT_TOKEN`(production 스코프, 이름 spark-railway-monitor) 등록 — 9/9 17:00 첫 전체 보고 전송(24h 평균 0.92 GB는 변경 전 구간 포함, 현재 0.09 GB)
- [ ] 오늘(9/9) 저녁 경기: ingest 지연·WS 연결 수·APNs send-stats 로그 평소 대비 확인
- [ ] 경기 밤 2회 경과 후 백엔드 RAM 평균 ≤ 0.35 GB 유지 여부 확인 (미달 시 폴백: 05:00 KST 일일 재배포)
- [ ] Railway Workspace Usage에서 Estimated Usage ≤ $5 확인
- [ ] 후속(선택): 메모리 성장 근본 원인 프로파일링 (weather `_forecast_cache` 프로덕션 미제거 등)
