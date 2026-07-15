## Backend (backend/api)

- [x] B1: JWT HS256 서명 + audience 검증, secret 미설정 시 fail-closed 401, 위조/무서명 토큰 테스트 추가
- [x] B2: 체크인 검증 `FOR UPDATE SKIP LOCKED` + 원자적 카운트 upsert
- [x] B3: 영구 실패 토큰 즉시 삭제 + 일일 퍼지 태스크 (live 7일 / device 90일)
- [x] B4: 날씨 호출을 DB 세션 밖으로 분리
- [x] B5: 락 딕셔너리 512 LRU 캡 + 이벤트 캐시 락 game_id 단위화
- [x] B6: FCM `send_each()` 500건 배치 전환
- [x] B7: debug 엔드포인트 인증, 계정 삭제 데이터 정리, LIMIT/offset, 주간 랭킹 SQL 집계, .db git 제거
- [x] X2: weather/cheer-signals/rankings 기본 날짜 KST 화
- [x] pytest 88 passed (기존 79 + 신규 9)

## Crawler (crawler)

- [x] C1: 크롤러 사망 시 백오프 재기동 (60s→15m, 10회 캡) + 워치 루프 광역 예외 처리
- [x] C2: 임포트 실패 시 완료 마킹 보류 → 30분 재시도
- [x] C3: 네이버 실패 rate-limit 로깅 + 연속 실패 ERROR
- [x] C4: 자정 윈도우 갱신 시 이전 날짜 미종료 윈도우 48h 유지
- [x] C5: 완료 이닝 캐싱 (폴당 GET 9+회 → 2회)
- [x] C6: pregame 크롤러 기동 조건 강화 + 시작 전 60초 폴링
- [x] C7: 파일 로깅 옵트인 전환 + TimedRotatingFileHandler
- [x] pytest 56 passed (기존 41 + 신규 15)

## iOS (ios)

- [x] I1: WebSocket 전용 URLSession 분리
- [x] I2: 워치 폴러 실패/빈 스케줄 구분 + 백오프 + 20분 스케줄 재조회
- [x] I3: 직접 푸시 경로 이벤트 필터 가드 순서 수정
- [x] I4: 워치 APNs 토큰 pending 보관 + 활성화 시 재전송
- [x] X1: RewardedAdOutcome 4상태 enum + 게이트 정책 적용
- [x] X2: 백엔드 날짜 포매터 en_US_POSIX + Asia/Seoul
- [x] I5: Game Equatable + 변경 시에만 할당, 날씨 10분 네거티브 캐시
- [x] I7: 선택 업데이트 알림 24h 디바운스
- [x] 폰/워치 스킴 xcodebuild BUILD SUCCEEDED

## Android (apps)

- [x] A1: 워치 폴러 종료 조건 + 라이프사이클 연동 + 백오프
- [x] A2: FCM 핸들러 GMS 1초 타임아웃 + 상태 캐시 폴백
- [x] A3: 내비게이션/pending 상태 rememberSaveable 이전
- [x] A4: FGS 비 LIVE 3회 관측 시 종료 + 6h 백스톱
- [x] X1: RewardedAdResult 4상태 enum + WeakReference Activity + isLoading 정리
- [x] A5: 스트리밍 루프 repeatOnLifecycle(STARTED) 래핑
- [x] A6: 이벤트 필터/그룹핑 remember 메모이제이션
- [x] A7: 워치 공유 ExoPlayer 1개로 통합
- [x] A8: proguard keep-all 제거 (양쪽 앱)
- [x] A9: cleartext debug 오버레이 이동
- [x] A10: 브로드캐스트 NOT_EXPORTED + setPackage
- [x] compileDebugKotlin·testDebugUnitTest·minifyReleaseWithR8 통과 (mobile+watch)

## 배포 체크리스트 (후속)

- [ ] Railway 백엔드에 `SUPABASE_JWT_SECRET` 환경변수 설정 (Supabase → Settings → API → JWT Secret)
- [ ] 백엔드/크롤러 staging 배포 후 라이브 경기 1회 관제 (재기동 로그·푸시 정상 확인)
- [ ] iOS/Android 앱 스토어 제출 빌드에 포함
- [ ] 미착수 잔여 항목: Crashlytics 도입(A-P3), os.Logger 마이그레이션(I-P3), 워치 WKBackgroundModes 재설계(I6), project.yml URL xcconfig 분리(I7 잔여), Android FCM data-push 경로(B9)
