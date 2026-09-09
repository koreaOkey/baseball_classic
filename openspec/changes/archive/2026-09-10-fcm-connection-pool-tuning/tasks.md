# Tasks

- [x] 원인 확인: 경기 시작 CPU 스파이크(1.10 vCPU)가 워커 수 무관(전날 2워커도 1.76), FCM send_each 연결 풀 churn임을 로그·코드로 확인
- [x] `config.py`에 `fcm_http_pool_size` 설정 추가(기본 50, env `BASEHAPTIC_FCM_HTTP_POOL_SIZE`)
- [x] `fcm.py` `_tune_connection_pool` 구현: requests 세션 풀 확대 + 재시도 보존 + 방어적 예외 처리, 초기화 시 1회 호출
- [x] 검증: py_compile, requests 어댑터 풀 10→50·재시도 보존 스모크, firebase-admin 내부 경로 존재 확인, pytest 푸시 관련 10건 통과
- [x] Android/iOS·모바일/워치 영향 확인: FCM(안드로이드) 발송 경로만 변경, APNs·페이로드·클라이언트 무변경
- [ ] Railway 백엔드 재배포 (staging 푸시 = 프로덕션 배포)
- [ ] 다음 경기 시작 시각 로그 확인: `Connection pool is full` 경고 소멸 + 경기시작 CPU 스파이크 완화
