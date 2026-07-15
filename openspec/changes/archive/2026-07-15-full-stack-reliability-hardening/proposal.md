## Why

전 영역(백엔드 API·크롤러·iOS·Android) 코드 검토에서 50건의 이슈가 발견되었다. 핵심 문제:

- **보안**: 백엔드가 JWT 서명을 검증하지 않아 위조 토큰으로 타인 계정 삭제·응원 이벤트 조작이 가능했다.
- **중계 신뢰성**: 크롤러 프로세스가 죽으면 해당 경기 중계가 영구 중단되고, 자정 스케줄 임포트 실패 시 최대 6시간 경기 감지가 불가능했다.
- **라이브 품질**: iOS WebSocket 세션에 5초 타임아웃이 걸려 있어 실시간 스트림이 사실상 5초 주기 재접속 폴링으로 동작했다.
- **배터리/비용**: 워치 폴러 무한 폴링(Android), 백그라운드 소켓 유지, 네이버 요청량 폴당 9회+, 죽은 푸시 토큰 무한 축적.
- **수익화 무결성**: 광고 게이트가 보상 획득 여부와 무관하게 기능을 해제했다(비행기 모드/더블탭 우회 가능).

## What Changes

### Backend (backend/api)
- **B1 (보안)**: `_extract_user_id_from_token` 이 Supabase JWT secret 으로 HS256 서명·audience 를 검증한다. secret 미설정 시 fail-closed(401). `SUPABASE_JWT_SECRET` env 필요.
- **B2**: 체크인 검증 워커가 `FOR UPDATE SKIP LOCKED` + 원자적 upsert(`count = count + 1`)로 동시성 이중 카운트를 제거.
- **B3**: APNs 410/BadDeviceToken·FCM Unregistered 응답 시 해당 토큰 즉시 삭제 + 일일 퍼지 태스크(라이브 토큰/세션 7일, 디바이스 토큰 90일 미갱신).
- **B4**: 날씨 네트워크 호출을 DB 세션 밖으로 이동(풀 고갈 방지).
- **B5**: 스냅샷/HTTP 캐시 락 딕셔너리를 512 상한 LRU 로 캡핑, 이벤트 캐시 락은 game_id 단위로 통합.
- **B6**: FCM 발송을 토큰당 스레드에서 `messaging.send_each()` 500건 배치로 전환.
- **B7**: `/debug/relay-stats` X-API-Key 게이트, 계정 삭제 시 백엔드 데이터(cheer_events·체크인 집계·세션) 동반 삭제, `/cheer-events/me` limit/offset, 주간 랭킹 SQL GROUP BY 전환, 추적 중이던 .db 파일 git 제거.
- **X2**: `/games/{id}/weather`·`/cheer-signals`·`/rankings/teams` 의 기본 날짜를 UTC → KST 로 수정.

### Crawler (crawler)
- **C1**: 크롤러 비정상 종료 시 지수 백오프(60s→15m, 최대 10회)로 자동 재기동. crawler 워치 루프는 연속 10회 실패 시 exit 1 로 디스패처 재시작 유도.
- **C2**: 오늘 날짜 임포트 성공 시에만 일일 임포트 완료 마킹 — 실패 시 30분 재시도 게이트 활성.
- **C3**: `_safe_json_get` 실패를 호스트당 60초 rate-limit WARNING 으로 로깅, 경기별 연속 실패 5회 시 "네이버 응답 실패 지속" ERROR.
- **C4**: 자정 윈도우 갱신 시 미종료 이전 날짜 윈도우를 48시간까지 유지(우천 순연 대응).
- **C5**: 완료 이닝 릴레이 캐싱 — 폴당 네이버 GET 9+회 → 2회.
- **C6**: 경기 시작 전 gameCenterUrl 만으로 크롤러 기동하던 조건 제거(LIVE 상태 게이트), 시작 전 폴링 60초로 감속.
- **C7**: 파일 로깅을 `--enable-file-log` 옵트인으로 전환(컨테이너 기본 stdout), 파일 사용 시 TimedRotatingFileHandler(7일).

### iOS (ios)
- **I1**: WebSocket 전용 URLSession 분리(요청 60s/리소스 7일) — 5초 타임아웃으로 인한 스트림 강제 종료 해소.
- **I2**: 워치 폴러가 네트워크 실패와 빈 스케줄을 구분, 백오프 재시도 + 20분 주기 스케줄 재조회.
- **I3**: 직접 APNs 경로에서도 이벤트 필터 가드를 영상 트리거보다 먼저 적용.
- **I4**: WCSession 미활성 시 워치 APNs 토큰을 보관했다가 활성화 콜백에서 재전송.
- **I5**: `Game` Equatable 화 + 변경 시에만 `todayGames` 할당(5초 폴 리렌더 제거), 날씨 실패 10분 네거티브 캐시.
- **I7(일부)**: 선택 업데이트 알림을 버전당 24시간 1회로 디바운스(강제 업데이트 경로 불변).
- **X2**: 백엔드 날짜 포매터에 `en_US_POSIX` + `Asia/Seoul` 고정.

### Android (apps)
- **A1**: 워치 폴러 종료 조건(전 경기 종료/날짜 전환) + ON_START/ON_STOP 라이프사이클 연동 + 실패 백오프.
- **A2**: FCM 핸들러의 GMS 조회를 1초 타임아웃 + SharedPreferences 캐시 폴백으로 전환.
- **A3**: 내비게이션·pending 프롬프트 상태를 `rememberSaveable` 로 이전(회전/분할화면/프로세스 재생성 시 홈 튕김 해소).
- **A4**: FGS 가 비 LIVE 상태 3회 연속 관측 시 자체 종료 + 6시간 최대 런타임 백스톱.
- **A5**: 홈/라이브 스트리밍 루프를 `repeatOnLifecycle(STARTED)` 로 래핑(백그라운드 소켓 종료).
- **A6**: 라이브 이벤트 필터링/그룹핑 `remember` 메모이제이션.
- **A7**: 워치 ExoPlayer 5개 상시 준비 → 공유 플레이어 1개.
- **A8**: proguard Compose/OkHttp/data-model keep-all 제거(양쪽 앱, R8 릴리즈 빌드 검증 완료).
- **A9**: `usesCleartextTraffic` 를 debug 매니페스트 오버레이로 이동(릴리즈 제거).
- **A10**: 내부 브로드캐스트 `RECEIVER_NOT_EXPORTED` + 송신 8곳 `setPackage()`.

### 공통 (X1) — 광고 게이트 정책 명문화
결과를 4상태 enum(REWARD_EARNED / LOAD_FAILED / DISMISSED_WITHOUT_REWARD / BUSY)으로 구분:
- 보상 획득 또는 광고 로드 실패(no-fill/네트워크) → 허용 (로드 실패는 원장 미기록)
- 광고 중도 이탈 → 거부
- 더블탭/busy → no-op (pending 상태 유지)
- 테마 스토어(영구 보상)는 REWARD_EARNED 만 허용

## Capabilities

### Modified Capabilities
- `backend-api`: 토큰 인증 엔드포인트는 서명 검증된 JWT 만 수용해야 한다. 죽은 푸시 토큰은 발송 실패 시·주기 퍼지로 정리되어야 한다. 날짜 기본값은 KST 기준.
- `crawler`: 크롤러 프로세스 사망·임포트 실패·네이버 장애는 자동 복구되거나 운영 로그로 식별 가능해야 한다.
- `mobile-ios` / `mobile-android`: 라이브 스트림은 백그라운드에서 소켓을 정리하고, 광고 게이트는 명문화된 4상태 정책을 따라야 한다.
- `watch-ios` / `watch-android`: 워치 폴링은 일시 장애에 견디고 경기 종료 후 중단되어야 한다. 이벤트 필터는 모든 수신 경로(WC/직접 푸시)에 적용된다.

## Impact

- Backend: `app/main.py`, `app/config.py`, `app/apns.py`, `app/fcm.py`, `app/workers/cheer_validator.py`, tests(+9, 총 88 passed)
- Crawler: `live_wbc_dispatcher.py`, `crawler.py`, `start.sh`, tests(+15, 총 56 passed)
- iOS: `BackendGamesRepository.swift`, `BaseHapticApp.swift`, `RewardedAdManager.swift`, `Game.swift`, `CheerSignalsLoader.swift`, `HomeScreen.swift`, 워치 `WatchGamePoller.swift`·`WatchConnectivityManager.swift` — 폰/워치 스킴 빌드 성공
- Android: 모바일 `MainActivity.kt`·`RewardedAdManager.kt`·`GameSyncForegroundService.kt`·`BaseHapticMessagingService.kt`·`HomeScreen.kt`·`LiveGameScreen.kt`·proguard·매니페스트, 워치 `WatchGamePoller.kt`·`MainActivity.kt`·`DataLayerListenerService.kt`·proguard — 양쪽 compileDebug/testDebugUnitTest/minifyReleaseWithR8 통과
- **배포 전제**: Railway 백엔드 서비스에 `SUPABASE_JWT_SECRET` 설정 필수 (미설정 시 계정 삭제·응원 API 전체 401 — 의도된 fail-closed)
- DB Migration: 불필요 (기존 컬럼만 사용)
