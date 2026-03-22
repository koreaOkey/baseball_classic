## 2026-03-01 (일요일)

- 원격 저장소 `origin/main` 최신 커밋으로 로컬 `main` 브랜치 fast-forward 업데이트 완료
- 로컬 불필요 변경 정리
  - `apps/GITREAD.md` 원복
  - `.gitignore`에 `Real-time Baseball Broadcast/` 추가하여 미추적 파일 표시 숨김
- 워치 홈런 애니메이션 작업
  - 원본 영상 `apps/watch/animate/homerun_minion.mp4`에서 `2초~6초` 구간(4초) 클립 생성
  - 클립 파일을 `apps/watch/app/src/main/res/raw/homerun_minion_clip.mp4`로 배치
  - `MainActivity.kt`에서 홈런 전환 화면을 Media3 ExoPlayer 기반 영상 재생으로 교체
  - `HOMERUN_SCREEN_DURATION_MS`를 4000ms로 조정
  - `apps/watch/app/build.gradle.kts`에 Media3 의존성 추가
- 워치 배터리 최적화를 위해 영상 리인코딩
  - 960x960 / 24fps / 1.6MB -> 450x450 / 20fps / 263KB

## 2026-03-17 (화요일)

- 실시간 전달 아키텍처를 `하이브리드(Push + 복구 Pull)` 기준으로 확정
  - 평소: WebSocket push로 `state/events` 수신
  - 끊김 후 재연결: `lastCursor` 기준 `/games/{gameId}/events?after=` + `/games/{gameId}/state` 1회 보정
  - 운영 원칙: 끊김 자체를 0으로 가정하지 않고, 빠른 자동 복구를 기본으로 설계

- 워치 중심 구성 원칙
  - 가능하면 워치도 백엔드에 직접 WebSocket 연결(최소 상태 채널)
  - 모바일 경유 전용 구조는 휴대폰 절전/백그라운드 상태에 종속되므로 보조 경로로 유지

- 데이터 우선순위 분리
  - 최우선(즉시 반영): `score`, `inning`, `BSO(ball/strike/out)`, `bases`
  - 차선(지연 허용): `event_type`, 이벤트 설명, 부가 메타데이터
  - 워치 UI는 최우선 데이터 기준으로 즉시 갱신

- 연결 안정화 정책
  - heartbeat(ping/pong) 유지
  - 지수 백오프 재연결: `1s -> 2s -> 5s -> 10s` (상한 고정)
  - 재연결 직후 `state` 스냅샷 강제 동기화 + 누락 이벤트 보정

- 중복/순서 보장
  - 이벤트 기준 키: `cursor`
  - 클라이언트(워치/모바일)에서 `cursor` dedupe + 정렬 후 렌더링

- Redis 도입 방침 (Railway 백엔드 연동)
  - 단일 인스턴스에서는 기존 in-memory `event_bus`로 시작 가능
  - 멀티 인스턴스(레플리카/워커 증가) 전환 시 Redis Pub/Sub를 필수로 사용
  - 목표 흐름:
    - `Crawler -> Backend API -> DB(source of truth)`
    - `Backend API -> Redis Pub/Sub(event fanout)`
    - `Redis Pub/Sub -> All Backend instances -> WebSocket clients(App/Watch)`
  - Railway 구성:
    - 동일 프로젝트에 Redis 서비스 추가
    - 백엔드 서비스 환경변수에 Redis 연결 문자열(`REDIS_URL`) 연결
    - backend ingest 완료 후 Redis publish, 각 인스턴스는 startup 시 subscribe 루프 실행
    - subscribe 수신 이벤트를 로컬 `event_bus.broadcast()`로 전달

## 2026-03-21 (��)

- ��ġ ����ȭ ���� �帧�� `����� �˾� �߽�`���� `��ġ �˾� �߽�`���� Ȯ��
  - ������ ��� `LIVE` ��ȯ �� ������� ��ġ�� ����ȭ ���� �˾� ����
  - ��ġ���� `��/�ƴϿ�` ���� �� ����Ϸ� ���� ������
  - ������� ������ ������ `syncedGameId`�� �����ϰ� �ǽð� ����ȭ ����/����

- Data Layer ����� ��� �߰�
  - ����� -> ��ġ: `/watch/prompt/current`
  - ��ġ -> �����: `/watch/sync-response/<timestamp>`

- ����� �� ���� ����ȭ
  - `MobileDataLayerListenerService` �߰�
  - `WearWatchSyncBridge`�� pending ���� ����/consume ó��

- ��ġ UI ������ ü�� ����
  - `WatchUiProfile` ������� ȭ�� ũ��/���� ���ο� ���� ������/��Ʈ/�е�/��� ũ�� �ڵ� ����
  - ���� ȭ��: `LiveGameScreen`, `NoGameScreen`, ����ȭ �˾�, �̺�Ʈ ��������

- 3�� ��� ������/�˼� ü�� �߰�
  - `wearos_small_round`, `wearos_large_round`, `wearos_square`
  - QA üũ����Ʈ ���� `apps/watch/SCREEN_QA.md` �߰�

- ���� ����
  - `:mobile:compileDebugKotlin`, `:watch:compileDebugKotlin` ����
