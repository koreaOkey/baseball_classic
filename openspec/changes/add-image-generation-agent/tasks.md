## 1. 사전 준비 · 가드 규칙 · 스키마

- [ ] 1.1 `docs/safety.md` 저작권 가드 규칙 작성 (얼굴·로고 금지, 허용되는 사실 모티프 명시)
- [ ] 1.2 `manifest.json` 스키마 확정 (pack_id, mode, player, theme, events, cost, retries)
- [ ] 1.3 `tokens.json` 스키마 확정 (야구봄 기존 iOS/Android 디자인 토큰 구조 호환 확인)
- [ ] 1.4 이벤트별 `meta.json` 스키마 확정 (scene_description, focal_point, dominant_colors, mood_tags)
- [ ] 1.5 CLI 인자 스펙 고정 (`--mode`, `--player`, `--concept_hint`)
- [ ] 1.6 stdout JSON progress event 스펙 고정 (ts, node, status, 부가 필드)
- [ ] 1.7 비용 상한·재시도 정책 결정 (팩당 재시도 최대 3회, 이벤트 SKIP 정책)

## 2. Mock 데이터

- [ ] 2.1 `data/players.json` — KBO 10구단 핵심 선수 20~30명 mock (포지션·등번호·팀컬러·별명·최근 이슈 키워드)
- [ ] 2.2 `data/catalog.json` — 기존 야구봄 테마 12종 메타 (색상 축·무드 축·계절 축 분류)
- [ ] 2.3 `data/events.json` — 야구봄 이벤트 타입 정의 (HR/HIT/STEAL/WALK/STRIKEOUT/SCORE/ERROR 등, 각 이벤트별 감정 태그)
- [ ] 2.4 저작권 금지어 사전 (선수 실명 외 얼굴 관련 키워드 블랙리스트)

## 3. Python 패키지 · 의존성

- [ ] 3.1 `baseball_agent/` 폴더 생성 및 과제 제출 경로 `03-assignment/final-XX/` 심볼릭 링크 또는 복제 정책 결정
- [ ] 3.2 `requirements.txt` 고정 (langgraph, openai, pydantic, pillow, scikit-learn, python-dotenv, rich)
- [ ] 3.3 `.env.example` (OPENAI_API_KEY, TAVILY_API_KEY 선택)
- [ ] 3.4 Python 3.11+ 가상환경 셋업 가이드

## 4. State · Supervisor

- [ ] 4.1 `agent/state.py` — Pydantic `PackState` 11필드 정의
- [ ] 4.2 `agent/supervisor.py` — PLAN Node (3 모드 자율 판단)
- [ ] 4.3 Supervisor에 `needs_player` / `needs_catalog` 조건부 엣지 설정
- [ ] 4.4 DISPATCH fan-out (Theme + Event 병렬 시작)
- [ ] 4.5 Supervisor에서 LangGraph `StateGraph` 조립 및 `compile()` 반환

## 5. Research 체인

- [ ] 5.1 `agent/research.py` — `CHOOSE_PLAYER` Node (mode=auto일 때 자율 선정)
- [ ] 5.2 `RESEARCH_PLAYER` Node — player_database + web_search 호출
- [ ] 5.3 `EXTRACT_TRAITS` Node — LLM이 저작권 금지어 제거하고 시각 모티프만 추출
- [ ] 5.4 `agent/catalog.py` — `ANALYZE_CATALOG` Node (gap 매트릭스 산출)
- [ ] 5.5 `FETCH_TREND` Node (웹 검색 기반 계절·이슈 키워드)
- [ ] 5.6 `IDENTIFY_GAP` Node (커버리지 공백 축 결정)

## 6. Tool 구현

- [ ] 6.1 `tools/player_database.py` — `data/players.json` 로더, 이름·팀·포지션 조회 인터페이스
- [ ] 6.2 `tools/web_search.py` — Tavily 또는 OpenAI 검색 래퍼 (쿼리 결과 요약 반환)
- [ ] 6.3 `tools/catalog_analyzer.py` — 카탈로그 JSON에서 축별 커버리지 행렬 계산
- [ ] 6.4 `tools/dalle_generate.py` — DALL-E 3 호출 래퍼 (재시도·타임아웃·비용 추적)
- [ ] 6.5 `tools/face_logo_detector.py` — GPT-4o Vision으로 얼굴·로고 감지 (Structured Output)
- [ ] 6.6 `tools/color_extractor.py` — Pillow + sklearn K-means로 팔레트 추출 후 디자인 토큰 매핑
- [ ] 6.7 `tools/pack_writer_tool.py` — 디렉토리 생성·이미지 저장·manifest 직렬화

## 7. Theme 서브그래프

- [ ] 7.1 `agent/theme_subgraph.py` — `BUILD_SPLASH_PROMPT` Node
- [ ] 7.2 `GENERATE_SPLASH` + `VERIFY_SPLASH` (대비·얼굴 감지) + REFINE 루프
- [ ] 7.3 `BUILD_HOME_BG_PROMPT` / `GENERATE_HOME_BG` / `VERIFY_HOME_BG` + REFINE
- [ ] 7.4 `BUILD_LOCK_PROMPT` / `GENERATE_LOCK` / `VERIFY_LOCK` + REFINE
- [ ] 7.5 `EXTRACT_COLORS` Node — 생성된 3장에서 대표 팔레트 추출
- [ ] 7.6 `COMPILE_THEME` Node — `tokens.json` 구조로 직렬화

## 8. Event 서브그래프

- [ ] 8.1 `agent/event_subgraph.py` — 이벤트 목록 루프 진입 로직
- [ ] 8.2 이벤트별 `BUILD_PROMPT` Node (이벤트 감정 태그 + 선수 시각 모티프 결합)
- [ ] 8.3 `GENERATE` + `VERIFY` (얼굴·로고 감지 엄격)
- [ ] 8.4 실패 시 `ABSTRACT_PROMPT` 상향 루프 (최대 3회)
- [ ] 8.5 3회 실패 시 해당 이벤트 `status=failed_after_retry`로 기록하고 SKIP
- [ ] 8.6 `SAVE_HERO` — 이미지 + `meta.json` (영상화 후속 힌트) 저장
- [ ] 8.7 `COMPILE_EVENT_SET` — generated_events dict 완성

## 9. Pack Writer · MERGE

- [ ] 9.1 `agent/pack_writer.py` — Theme + Event 결과 병합
- [ ] 9.2 `pack_id` 생성 규칙 (`pack_YYYYMMDD_<seq>_<label>`)
- [ ] 9.3 graceful degradation — 일부 실패해도 성공분만으로 팩 저장
- [ ] 9.4 비용·재시도 카운트 집계하여 manifest에 기록
- [ ] 9.5 stdout에 `PACK_WRITER done` 이벤트 방출 (pack_path 포함)

## 10. 엔트리포인트 · CLI

- [ ] 10.1 `main.py` — argparse 인자 파싱
- [ ] 10.2 Supervisor 컴파일 후 초기 State로 `invoke()`
- [ ] 10.3 각 Node 완료 시 stdout JSON progress event 방출 (LangGraph callback 활용)
- [ ] 10.4 종료 코드 매핑 (0=성공, 1=부분 실패, 2=치명적 실패)
- [ ] 10.5 `python main.py` 인자 없이 실행 시 `--mode auto` 기본값 적용

## 11. 데모 시나리오 · 검증

- [ ] 11.1 시나리오 A — `python main.py --mode auto` 실행 및 로그 캡처
- [ ] 11.2 시나리오 B — `python main.py --mode player_pack --player 김혜성` 실행
- [ ] 11.3 시나리오 C — `python main.py --mode theme_pack --concept_hint "봄 파스텔"` 실행
- [ ] 11.4 각 시나리오에서 팩 디렉토리 정합성 검증 (manifest·파일 존재·스키마)
- [ ] 11.5 저작권 가드 실패 시 추상화 루프 작동 확인 (의도적 선수명 프롬프트 주입 테스트)
- [ ] 11.6 이벤트 3회 실패 후 SKIP 확인 (의도적으로 실패 유도 케이스)
- [ ] 11.7 총 비용이 팩당 $0.50 이하인지 계측

## 12. 문서

- [ ] 12.1 `README.md` — 프로젝트 개요, 실행 방법, 환경 변수, 입출력 예시, Agent flow 다이어그램
- [ ] 12.2 `docs/architecture.md` — 상세 State Machine 다이어그램, Tool 매트릭스, 분기 축 해설
- [ ] 12.3 `docs/safety.md` — 저작권 가드 규칙, 금지·허용 매트릭스, 실패 케이스 처리 방침
- [ ] 12.4 `baseball_agent/DESIGN.md` — 설계서 최신화 유지

## 13. OpenClaw 연동 준비 (인터페이스만)

- [ ] 13.1 CLI 인자·stdout 이벤트 스펙이 OpenClaw Custom Tool에서 파싱 가능한지 JSON 예시로 검증
- [ ] 13.2 향후 OpenClaw subprocess 호출용 wrapper 함수 시그니처 초안
- [ ] 13.3 실제 OpenClaw 통합은 **본 차수 범위 외**임을 README에 명시

## 14. 발표 · 제출 (4/29 마감, 4/30 발표)

- [ ] 14.1 제출 폴더 구조 `03-assignment/final-XX/` 확정
- [ ] 14.2 발표 슬라이드 (PPT) 초안 — 문제·Agent 소개·데모·기술 포인트·회고
- [ ] 14.3 발표 데모 녹화본 백업 (실시간 실패 대비)
- [ ] 14.4 발표 자료 제출 (yurim_11@naver.com, 파일명 `AX개발리더-00조-{프로젝트명}.pptx`)
- [ ] 14.5 GitHub에 최종 제출 코드 push
