## Why

야구봄 테마 상점은 색상 팔레트 중심 UI 스킨으로만 구성돼 있어(딥 네이비·체리로즈·버건디 골드 등) 선수·이벤트와 연결된 세계관 콘텐츠가 없다. 팬은 자신이 응원하는 선수와 경기 순간을 상징하는 앱 분위기를 원하지만 수작업으로 테마·배경·이벤트 스틸을 디자인하기에는 단가·시간이 크다. 네이버·다음 댓글 폐지 이후 팬 커뮤니티가 파편화된 상황에서 야구봄이 "선수 세계관"을 공급할 수 있으면 테마 상점 ARPU와 체류시간을 동시에 끌어올릴 수 있다.

이번 차수는 **State Machine 기반 LangGraph Agent**로 팩 단위 콘텐츠(스플래시·홈 배경·잠금 화면·이벤트 히어로 스틸)를 자동 생성하는 파이프라인을 제안한다. 과제 트랙(Workflow)의 검증→분류→실행 흐름에 부합하며, 향후 OpenClaw 비서와 연동해 자연어 트리거·Cron 자동화까지 확장 가능한 기반이 된다.

## What Changes

- 신규 Python 프로젝트 `baseball_agent/` 추가 (과제 제출물 `03-assignment/final-XX/`와 소스 공유)
- **LangGraph Supervisor 패턴** 도입 — Theme 서브그래프 + Event 서브그래프 fan-out
- **3 모드 자율 판단** — `auto` / `player_pack` / `theme_pack` / `event_pack` PLAN
- **Research 체인** — 선수 DB 조회 → 웹 검색 → 시각 모티프 추출 (저작권 금지어 필터링 포함)
- **Tool 7종** — `player_database`, `web_search`, `catalog_analyzer`, `dalle_generate`, `face_logo_detector`, `color_extractor`, `pack_writer`
- **저작권 가드 파이프라인** — 얼굴·로고 감지 → 추상화 레벨 상향 → 재생성 루프 (최대 3회) → 실패 시 이벤트 SKIP
- **팩 산출물 스키마** — `packs/pack_YYYYMMDD_<seq>_<label>/` 하위에 `theme/`(splash·home_bg·lock·tokens.json) + `events/<EVENT>/`(hero.png·meta.json)
- **기존 디자인 토큰 스키마 호환** — `tokens.json`이 iOS/Android 토큰 구조(2026-04-13 archive) 그대로 주입 가능
- **영상화 후속 작업용 메타 힌트** 방출 — 이벤트별 `scene_description`, `focal_point`, `dominant_colors`, `mood_tags`
- **단독 실행 보장** — `python main.py --mode auto` 한 번으로 완결 (외부 비서 없이 과제 채점 통과)
- **OpenClaw 래핑 인터페이스 노출** — argparse CLI + stdout JSON progress event로 향후 비서 통합 용이

## Capabilities

### New Capabilities
- `image-generation-agent`: State Machine 기반 이미지 생성 Agent. Supervisor·Theme/Event 서브그래프·Research 체인·저작권 가드·팩 직렬화 전반을 담당. 야구봄 앱은 이 Agent가 만든 `packs/` 디렉토리를 소비한다.

### Modified Capabilities
- (없음 — 과제 범위는 Agent 본체 생성까지. 야구봄 앱 쪽 실제 통합(토큰 주입·이벤트 히어로 로더 등)은 후속 openspec change로 분리.)

## Impact

- **코드**: 신규 Python 패키지 1개(`baseball_agent/`), 엔트리포인트 1개(`main.py`), Agent 모듈 7개, Tool 모듈 7개, mock 데이터 3종 JSON, 문서 2종(`docs/safety.md`, `docs/architecture.md`). 기존 iOS/Android 코드 무변경.
- **의존성**: `langgraph`, `openai`, `pydantic`, `pillow`, `scikit-learn` (K-means), `python-dotenv`, 로깅용 `rich`. 선택적으로 웹 검색은 `tavily-python` 또는 OpenAI 제공 검색 기능 활용.
- **외부 API**: OpenAI API Key 1개 (그룹 배정, 최대 $65 한도). DALL-E 3·GPT-4o-mini·GPT-4o Vision 3종 사용.
- **데이터**: KBO 구단별 핵심 선수 20~30명 mock DB (시각 모티프 필드 포함), 기존 야구봄 테마 12종 카탈로그 메타, 이벤트 타입 정의.
- **법무**: 저작권 가드는 "실제 얼굴·구단 로고 생성 금지, 사실 정보(등번호·팀컬러·포지션 실루엣)만 추상 표현 허용" 규칙을 `docs/safety.md`로 고정. 실제 야구봄 앱 배포 전에는 외부 법무 검토 단계를 별도 openspec change로 분리.
- **운영**: 팩 생성 1회당 약 $0.40 (DALL-E 3 기준), 개발 30팩 + 데모 5팩 = $14 예산. 재시도 상한·이벤트 SKIP 로직으로 비용 폭주 차단.
- **향후 확장 (Non-Goals 이번 차수)**:
  - 영상화·애니메이션 자동화 (이벤트 히어로 스틸 + `meta.json` 힌트만 제공)
  - 야구봄 앱 실제 통합 (스플래시 교체·테마 상점 "선수 팩" 카테고리 신설)
  - OpenClaw 래핑 배포 (인터페이스만 제공, 실제 Slack/iMessage 통합은 후속 차수)
  - Supabase 연동 (로컬 파일 시스템 저장만)
  - 실시간 경기 데이터 기반 자동 트리거 (Cron 스케줄)
  - KBO 이외 리그 (MLB·NPB) 선수 팩
