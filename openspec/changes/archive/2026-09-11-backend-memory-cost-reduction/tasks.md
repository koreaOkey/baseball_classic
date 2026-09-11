# Tasks

- [x] 운영 안전성 검토: 야간 재배포는 헬스체크 무중단·상태 Redis 보존·크롤러 무관·실패 시 구 컨테이너 유지. 인메모리 구조 정적 점검 결과 논리적 누수 없음 → 힙 단편화로 결론.
- [x] 재배포 수단 확인: `serviceInstanceRedeploy`(빌드 없음, 고정 ID) 선택. 프로젝트 토큰 유효성·환경 스코프 검증(--check).
- [x] #1 spark: `railway_redeploy.sh` 배포 + crontab `0 5 * * *`(KST) 등록
- [x] #2 config: `memory_trim_enabled`(기본 True) + `memory_trim_interval_sec`(기본 600) 추가
- [x] #2 main: malloc_trim/RSS 헬퍼 + `_memory_trim_loop` + lifespan 등록, ctypes/os/sys 임포트
- [x] 검증: py_compile, mac no-op 스모크, pytest 143건 통과
- [x] Android/iOS·모바일/워치 영향 확인: 백엔드 내부 유지보수 루프만 추가, API·페이로드·클라이언트 무변경
- [ ] Railway 백엔드 재배포 (staging 푸시 = 프로덕션 배포)
- [ ] 배포 후 로그 확인: 기동 시 `_memory_trim_loop` 정상, 경기 후 `[mem-trim] freed=..MB` 로 반환량 관측
- [ ] 며칠 뒤: 24h 평균 RAM 하락·다일 누적 소멸·Railway Estimated Usage 재확인
- [ ] 첫 야간 재배포(05:00 KST) 후 `railway-redeploy-cron.log` 성공 로그 확인
