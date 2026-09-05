## 1. 백엔드 · Redis 격리

- [x] 1.1 연결/명령/캐시 작업 타임아웃 + 실패를 캐시 미스로 흡수 + 카운터·스로틀 경고
- [x] 1.2 구독 클라이언트 connect 타임아웃·keepalive, 재연결 지수 백오프(상한 10s)
- [x] 1.3 테스트: 행 걸린 Redis 에서 get/set/delete/publish 1s 내 반환, 연결 오류 흡수, 라운드트립, 백오프 상한

## 2. 백엔드 · APNs 관측성

- [x] 2.1 JWT 생성(키 디코드/ES256 서명) 실패 60s 스로틀 traceback, HTTP/2 클라이언트 초기화 실패 로그
- [x] 2.2 fan-out 4곳(경기 시작·분풀이 패배·워치·Live Activity) + apns 배치 2곳 예외 요약 로그
- [x] 2.3 테스트: 잘못된 키 → None + 로그 1회(스로틀), 요약 포맷, 예외 없으면 무로그

## 3. 크롤러

- [x] 3.1 dispatcher 최종 동기화 재시도(백오프·상한·회수 로그) + 자정 수집 전날 포함
- [x] 3.2 crawler.py statusInfo 기반 종료 판정 + 경기 전 idle 6h 상한
- [x] 3.3 테스트(dispatcher 6건, crawler 4건) 및 기존 스위트 통과

## 4. 플랫폼 영향 확인

- [x] 4.1 Android/iOS 모바일·워치 클라이언트 코드 변경 없음, API 응답 스키마 불변
- [x] 4.2 DB 마이그레이션 없음

## 5. 배포·검증

- [ ] 5.1 staging 푸시(=프로덕션 배포) 후 두산:SSG(20260905OBSK02026) FINISHED 회수 확인
- [ ] 5.2 다음 경기에서 `[APNs-LA] sent>0` 이거나 원인 로그(`JWT creation failed` / `sends raised`) 확인 후 근본 원인 변경 착수
- [ ] 5.3 9/3 취소 경기(20260903HTNC02026) 크롤러 종료 확인
