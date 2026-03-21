# Project Structure Specification

## Purpose
BaseHaptic 모노레포의 폴더 구조와 각 모듈의 역할을 정의한다.

## Requirements

### Requirement: 모노레포 최상위 구조
프로젝트는 아래 최상위 디렉토리로 구성되어야 한다(MUST).

```
baseball_classic/
├── apps/                    # 클라이언트 앱 (모바일/워치)
│   ├── mobile/              # Android 모바일 앱
│   │   ├── app/             # 앱 소스코드
│   │   └── team_image/      # 팀 로고 이미지 에셋
│   └── watch/               # Wear OS 워치 앱
│       ├── app/             # 앱 소스코드
│       ├── animate/         # 원본 애니메이션 에셋
│       └── figma-handoff/   # Figma 디자인 핸드오프
│
├── backend/                 # 백엔드 서버
│   └── api/                 # FastAPI 서비스
│       ├── app/             # 애플리케이션 코드
│       ├── examples/        # API 요청 예시
│       ├── scripts/         # 백엔드 유틸리티 스크립트
│       └── tests/           # 백엔드 테스트
│
├── crawler/                 # 데이터 수집 서비스
│
├── db/                      # 데이터베이스
│   └── migrations/          # SQL 마이그레이션 (12개)
│
├── Real-time Baseball Broadcast/  # 웹 프로토타입 (React/TypeScript)
│   └── src/
│       ├── assets/          # 이미지/에셋
│       ├── components/      # React 컴포넌트 + shadcn/ui
│       ├── guidelines/      # 디자인 가이드라인
│       └── styles/          # CSS/Tailwind 스타일
│
├── openspec/                # OpenSpec 명세 관리
│   ├── config.yaml          # 프로젝트 컨텍스트 설정
│   ├── specs/               # 도메인별 현재 동작 명세
│   └── changes/             # 변경 이력 (archive 포함)
│
├── data/                    # 샘플/목 데이터 및 런타임 로그
├── docs/                    # 프로젝트 문서
├── scripts/                 # 개발 유틸리티 스크립트
│   └── dev/                 # PowerShell 스크립트
├── infra/                   # 인프라 설정
│   └── cloudflared/         # Cloudflare 터널 설정
├── Daily/                   # 일일 개발 로그
├── Test/                    # 테스트 결과/검증 데이터
├── 한화_files/              # Figma 디자인 캐시 (한화 팀)
│
├── build.gradle.kts         # Gradle 루트 빌드 설정
├── settings.gradle.kts      # Gradle 모듈 설정 (:mobile, :watch)
├── gradle.properties        # Gradle 속성
├── gradlew / gradlew.bat    # Gradle 래퍼
├── README.md                # 프로젝트 README
├── AGENTS.md                # AI 에이전트 가이드
├── basehaptic.db            # 로컬 개발용 SQLite DB
├── .gitignore
├── .editorconfig
└── .claude/                 # Claude Code + OpenSpec 설정
    ├── commands/opsx/        # OpenSpec 명령어
    └── skills/               # OpenSpec 스킬
```

### Requirement: Android 모바일 앱 구조
모바일 앱은 Jetpack Compose 기반 단일 모듈로 구성되어야 한다(MUST).

```
apps/mobile/app/src/main/java/com/basehaptic/mobile/
├── MainActivity.kt                    # 엔트리 포인트 + 내비게이션
├── BackendGamesRepository.kt          # 백엔드 API 통신
│
├── [Screens]
│   ├── HomeScreen.kt                  # 당일 경기 목록
│   ├── LiveGameScreen.kt              # 실시간 경기 화면
│   ├── SettingsScreen.kt              # 설정
│   ├── ThemeStoreScreen.kt            # 테마 스토어
│   ├── CommunityScreen.kt             # 커뮤니티
│   ├── OnboardingScreen.kt            # 온보딩
│   └── WatchTestScreen.kt             # 워치 연결 테스트
│
├── [Data Models]
│   ├── Game.kt                        # 경기 모델
│   ├── GameEvent.kt                   # 경기 이벤트 모델
│   ├── Team.kt                        # 팀 모델
│   └── ThemeData.kt                   # 테마 데이터 모델
│
├── [Theme]
│   ├── Color.kt                       # 컬러 팔레트
│   ├── Theme.kt                       # Material 테마 구성
│   ├── TeamTheme.kt                   # 10개 구단 컬러 프리셋
│   └── Type.kt                        # 타이포그래피
│
├── [Wear OS 연동]
│   ├── WearGameSyncManager.kt         # 경기 데이터 워치 동기화
│   ├── WearThemeSyncManager.kt        # 테마 워치 동기화
│   ├── WearWatchSyncBridge.kt         # 동기화 응답 브릿지
│   ├── MobileDataLayerListenerService.kt  # 워치 메시지 수신 서비스
│   └── TeamLogo.kt                    # 팀 로고 컴포넌트
```

### Requirement: Wear OS 워치 앱 구조
워치 앱은 Wear OS Jetpack Compose 기반 단일 모듈로 구성되어야 한다(MUST).

```
apps/watch/app/src/main/java/com/basehaptic/watch/
├── MainActivity.kt                    # 엔트리 포인트 + 경기 로직
│
├── [UI Components]
│   └── ui/components/
│       ├── LiveGameScreen.kt          # 라이브 스코어보드
│       └── NoGameScreen.kt            # 경기 없음 화면
│
├── [Theme]
│   └── ui/theme/
│       ├── Color.kt                   # 컬러 팔레트
│       ├── Theme.kt                   # 다이나믹 팀 테마
│       ├── TeamTheme.kt              # 10개 구단 컬러 프리셋
│       ├── Type.kt                    # 타이포그래피
│       └── WatchUiProfile.kt          # 반응형 프로파일 (3단계)
│
├── [Data]
│   └── GameData.kt                    # 경기 데이터 모델
│
├── [Services]
│   ├── DataLayerListenerService.kt    # 모바일 메시지 수신
│   └── WatchSyncResponseSender.kt     # 동기화 응답 전송
```

### Requirement: 백엔드 API 구조
백엔드는 FastAPI 단일 서비스로 구성되어야 한다(MUST).

```
backend/api/app/
├── __init__.py
├── main.py           # FastAPI 앱 엔트리 + 라우트 정의
├── models.py         # SQLAlchemy ORM 모델
├── schemas.py        # Pydantic 요청/응답 DTO
├── services.py       # 비즈니스 로직 (상태 정규화, upsert, 변환)
├── config.py         # 환경 설정 (DB URL, API 키 등)
├── db.py             # DB 세션/초기화
├── event_bus.py      # 인메모리 WebSocket 이벤트 브로드캐스트
└── redis_bus.py      # Redis Pub/Sub 이벤트 큐
```

### Requirement: 크롤러 구조
크롤러는 독립 Python 스크립트 집합으로 구성되어야 한다(MUST).

```
crawler/
├── crawler.py                      # 메인 크롤러 (릴레이 데이터 수집)
├── backend_sender.py               # 릴레이 JSON → 백엔드 페이로드 변환
├── live_baseball_dispatcher.py     # KBO 일일 스케줄 + 라이브 디스패처
├── live_wbc_dispatcher.py          # WBC 대회 디스패처
├── live_baseball_server.py         # 라이브 모니터 서버
├── mock_baseball_relay_server.py   # 목 릴레이 테스트 서버
├── build_baseball_sample_data.py   # 샘플 데이터 생성
├── test_crawler_preview_lineup.py  # 프리뷰 라인업 테스트
├── test_backend_sender.py          # 백엔드 전송 테스트
└── test_live_wbc_dispatcher.py     # WBC 디스패처 테스트
```

### Requirement: DB 마이그레이션 구조
마이그레이션은 날짜_순번_설명 형식으로 관리되어야 한다(MUST).

```
db/migrations/
├── 20260217_001_init_basehaptic_core_schema.sql        # 코어 스키마 초기화
├── 20260217_002_harden_rls_and_indexes.sql             # RLS + 인덱스 강화
├── 20260218_003_expand_game_events_event_type_check.sql # 이벤트 타입 확장
├── 20260218_004_add_walk_event_type_check.sql          # WALK 타입 추가
├── 20260302_001_expand_game_summary_and_boxscore.sql   # 게임 요약/박스스코어 확장
├── 20260302_002_drop_legacy_game_lineups.sql           # 레거시 라인업 제거
├── 20260302_003_fix_stats_uniqueness_and_outs.sql      # 통계 유니크 + 아웃 수정
├── 20260302_004_add_double_triple_play_and_backfill.sql # 더블/트리플 플레이
├── 20260308_005_add_pitcher_change_event_type_check.sql # 투수 교체 타입
├── 20260308_006_add_half_inning_change_event_type.sql   # 이닝 전환 타입
├── 20260313_007_add_player_team_and_game_context.sql    # 박스스코어 컨텍스트
└── 20260314_008_add_team_record_table.sql               # 팀 순위 테이블
```

### Requirement: 문서 구조

```
docs/
├── README.md               # 문서 인덱스
├── craw.md                 # 크롤러 운영 가이드
├── ENCODING.md             # 인코딩 이슈 가이드
├── cloudflare_tunnel.md    # Cloudflare 터널 설정
└── local_watchdog.md       # 로컬 감시 스크립트 가이드

Daily/
├── 2026_02_18.md           # 2월 18일 개발 일지
└── 2026_03_21.md           # 3월 21일 개발 일지
```
