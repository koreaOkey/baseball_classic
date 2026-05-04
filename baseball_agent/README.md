# 야구봄 이미지 생성 Agent

> **Week6 State Machine 기반 Agent MVP 과제** · Track ① Workflow
> 팩 생성 요청을 **검증(저작권) → 분류(mode PLAN) → 실행(생성·검증·저장)** 흐름으로 끝까지 처리하는 LangGraph Supervisor Agent.

한 명령으로 야구봄 테마 상점에 투입 가능한 콘텐츠 팩을 자동 생성한다.
- 앱 **스플래시·홈 배경·잠금 화면** 3종 + 디자인 토큰 JSON
- 야구 **이벤트 히어로 스틸** (HR/HIT/STEAL/WALK/STRIKEOUT) + 메타 힌트

---

## 1. 빠른 실행

```bash
# 1) 가상환경 + 의존성
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 2) (선택) OpenAI API 키
cp .env.example .env    # 파일 열어 OPENAI_API_KEY 입력
# 키가 없어도 모든 경로가 stub 폴백으로 끝까지 완주한다 (과제 채점 호환)

# 3) 실행
python main.py                                            # auto 모드
python main.py --mode player_pack --player 이정후         # 대상 지정
python main.py --mode theme_pack --concept_hint "봄 파스텔"  # 테마만
python main.py --mode event_pack --player 문동주          # 이벤트만
```

`python main.py`만 쳐도 팩 한 개가 `packs/` 하위에 생성된다.

---

## 2. 환경 변수

| 변수 | 필수 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | 선택 | 있으면 LLM·이미지·Vision 실경로. 없으면 전부 stub 폴백. |
| `OPENAI_LLM_MODEL` | 선택 | 기본 `gpt-4o-mini` |
| `OPENAI_IMAGE_MODEL` | 선택 | 기본 `gpt-image-1-mini` (대안: `gpt-image-2`, `gpt-image-1`, `dall-e-3`) |
| `OPENAI_IMAGE_QUALITY` | 선택 | `low`/`medium`/`high`/`auto`. 기본 `medium` (gpt-image-* 계열만 유효) |
| `OPENAI_VISION_MODEL` | 선택 | 기본 `gpt-4o-mini` |
| `STUB_FAIL_RATE` | 선택 | 0.0~1.0. stub 모드에서 저작권 재시도 루프 연습용 실패 유도 비율 |

---

## 3. 입출력 예시

### 입력
```bash
python main.py --mode auto
```

### stdout (JSON progress event; OpenClaw 등 외부 래퍼가 구독)
```json
{"ts":"13:55:24","node":"PLAN","status":"done","decided_mode":"player_pack","reason":"..."}
{"ts":"13:55:24","node":"CHOOSE_PLAYER","status":"done","player":"이정후","team":"샌프란시스코 자이언츠","jersey":11,"source":"llm_autonomous"}
{"ts":"13:55:24","node":"RESEARCH_PLAYER","status":"done","query":"이정후 ...","hits":3,"is_stub":false,"source":"openai_responses_web_search"}
{"ts":"13:55:24","node":"EXTRACT_TRAITS","status":"done","colors":["#FD5A1E","#000000","#AE8F6F"],"n_motifs":5,"source":"llm"}
{"ts":"13:55:24","node":"THEME/SPLASH","status":"done","step":"complete","attempt":1}
{"ts":"13:55:24","node":"EVENT/HR","status":"done","step":"complete","attempt":1}
...
{"ts":"13:55:32","node":"PACK_WRITER","status":"done","pack_id":"pack_20260423_01_leejungho","summary":{"theme_ok":3,"theme_total":3,"events_ok":5,"events_total":5,"events_failed":0},"total_cost":0.36}
```

### 산출물
```
packs/pack_20260423_01_leejungho/
├── manifest.json
├── theme/
│   ├── splash.png
│   ├── home_bg.png
│   ├── lock.png
│   └── tokens.json        # 야구봄 디자인 토큰 스키마 호환
└── events/
    ├── HR/{hero.png, meta.json}
    ├── HIT/{hero.png, meta.json}
    ├── STEAL/{hero.png, meta.json}
    ├── WALK/{hero.png, meta.json}
    └── STRIKEOUT/{hero.png, meta.json}
```

### 종료 코드
- `0` 전체 성공
- `1` 부분 실패 (일부 asset이 SKIP, 팩은 저장됨)
- `2` 치명적 실패

---

## 4. Agent 구조 (Flow)

```
START
  ↓
PLAN                    (auto 모드면 Supervisor가 자율 결정)
  ↓ (cond: needs_player?)
CHOOSE_PLAYER → RESEARCH_PLAYER → EXTRACT_TRAITS
  ↓ (cond: needs_catalog?)
ANALYZE_CATALOG → FETCH_TREND → IDENTIFY_GAP
  ↓
DISPATCH                (fan-out)
  ├─→ THEME_SUBGRAPH              ─┐
  │     for asset in splash/home/lock:
  │       BUILD_PROMPT → GENERATE → VERIFY → (refine loop x3)
  │     EXTRACT_COLORS → COMPILE_TOKENS
  │
  └─→ EVENT_SUBGRAPH              ─┤
        for event in HR/HIT/STEAL/WALK/STRIKEOUT:
          BUILD_PROMPT → GENERATE → VERIFY → (refine loop x3)
          (실패 이벤트는 SKIP, 다음 이벤트 계속)
                                    ▼
                             PACK_WRITER
                                    ↓
                                   END
```

### 자율성 5대 포인트
1. **PLAN** — auto 모드에서 player/theme/event 중 무엇을 만들지 LLM이 판단
2. **CHOOSE_PLAYER** — 웹 검색으로 "이슈 있는 선수" 자율 선정
3. **IDENTIFY_GAP** — 카탈로그 공백 + 트렌드를 종합해 컨셉 재작성
4. **추상화 루프** — 얼굴 감지 시 프롬프트 추상화 레벨 자율 상향
5. **Graceful degradation** — 이벤트 일부 실패해도 성공분만으로 팩 저장

---

## 5. 사용한 Tool 7종

| Tool | 역할 | 경로 |
|---|---|---|
| `player_database` | KBO/MLB 28명 mock DB 조회 | Static Storage |
| `web_search` | 최근 이슈·트렌드 (OpenAI Responses API `web_search_preview` → Chat fallback → stub) | 웹 검색 |
| `catalog_analyzer` | 기존 테마 12종 gap 분석 | Static Storage |
| `image_generate` | 이미지 생성 (기본 `gpt-image-1-mini` medium, ~$0.009/img) | OpenAI Image API |
| `face_logo_detector` | GPT-4o Vision으로 얼굴·로고 감지 ($0.008/img) | OpenAI Vision |
| `color_extractor` | Pillow + K-means 팔레트 추출 → 디자인 토큰 | 로컬 |
| `pack_writer_tool` | 팩 디렉토리 생성·에셋 이동·manifest 직렬화 | 로컬 |

모든 Tool은 **dual-path** (실 API / stub fallback) 설계라 키 유무와 관계없이 파이프라인이 완주한다.

---

## 6. 조건부 분기 9축 (과제 요구사항 충족)

1. PLAN 3-way (mode 자율 결정)
2. needs_player 조건부 엣지
3. needs_catalog 조건부 엣지
4~6. Theme splash/home/lock 검증 실패 → REFINE 루프
7. Event 얼굴 감지 → 추상화 레벨 상향 루프
8. Event 3회 실패 → SKIP (다음 이벤트 계속)
9. MERGE graceful degradation (일부 실패해도 성공분 저장)

---

## 7. 저작권 가드

상세는 [docs/safety.md](docs/safety.md).

- ❌ 실제 선수 얼굴·구단 공식 로고 재현 금지
- ✅ 등번호·팀컬러·포지션 실루엣·플레이 모션·별명 모티프 허용
- 프롬프트에 안전 템플릿 4줄 자동 append
- LLM 출력·생성 이미지 2중 필터 (EXTRACT_TRAITS 어휘 필터 + face_logo_detector Vision 판정)

---

## 8. 폴더 구조

```
baseball_agent/
├── main.py                    # 엔트리포인트
├── requirements.txt
├── README.md
├── DESIGN.md                  # 상세 설계 문서
├── IDEA.md                    # 공유용 아이디어 요약
├── .env.example
│
├── agent/
│   ├── state.py              # PackState (Pydantic + 4 reducer)
│   ├── config.py             # OpenAI 클라이언트 팩토리
│   ├── events.py             # stdout JSON progress emit
│   ├── supervisor.py         # PLAN + build_graph + 조건부 엣지
│   ├── research.py           # CHOOSE/RESEARCH/EXTRACT 체인
│   ├── catalog.py            # ANALYZE/TREND/IDENTIFY 체인
│   ├── theme_subgraph.py     # splash·home·lock 생성
│   ├── event_subgraph.py     # 이벤트별 히어로 스틸 생성
│   └── pack_writer.py        # 최종 디렉토리·manifest 저장
│
├── tools/                     # 7종 Tool
├── data/                      # 3종 mock JSON
├── docs/
│   └── safety.md              # 저작권 가드 규칙
└── packs/                     # 실행 결과 (gitignore 권장)
```

---

## 9. 데모 시나리오 3종

### A. 완전 자율
```bash
python main.py --mode auto
```
→ LLM이 선수 선정 → Research → theme 3 + events 5 병렬 → 팩 완성

### B. 대상 지정
```bash
python main.py --mode player_pack --player 김도영
```
→ CHOOSE_PLAYER 스킵 → 김도영(KIA) Research → 팩 완성

### C. 컨셉 테마만
```bash
python main.py --mode theme_pack --concept_hint "봄 파스텔"
```
→ Research 스킵 → 카탈로그 gap + 트렌드 반영 → theme 3만 생성

### D. 재시도 루프 검증 (stub 모드)
```bash
STUB_FAIL_RATE=0.7 python main.py --mode event_pack --player 류현진
```
→ face_logo_detector가 확률 실패 → 프롬프트 추상화 레벨 상향 루프 → 3회 실패 시 SKIP

---

## 10. 비용 (API 키 설정 시)

기본값 `gpt-image-1-mini` + `medium` 품질 기준:

- 이미지 장당 ~$0.009 (1,056 tokens × $8/1M)
- Vision 검증 장당 ~$0.008
- **팩 1개당 평균 ~$0.17** (이미지 10장 + Vision 10회, 재시도 포함)
- **35팩 총 ~$6** (그룹 예산 $65의 9%)

모델 스위치(환경변수):
| 조합 | 장당 | 35팩 총 |
|---|---|---|
| `gpt-image-1-mini` + `low` | $0.0025 | ~$3.8 |
| **`gpt-image-1-mini` + `medium`** | **$0.0088** | **~$6.0** (기본) |
| `gpt-image-2` + `low` | $0.0066 | ~$5.2 |
| `gpt-image-2` + `medium` | $0.024 | ~$11.3 |
| `dall-e-3` standard (레거시) | $0.04 | ~$16.8 |

`STUB_FAIL_RATE`·재시도 상한 3회·이벤트 SKIP으로 비용 폭주 차단.

---

## 11. 범위 외 (Non-Goals)

- 영상·애니메이션 자동화 (`meta.json`에 후속 작업용 힌트만 제공)
- 야구봄 앱 실제 통합 (별도 openspec change)
- OpenClaw 비서 레이어 실배포 (CLI + stdout 인터페이스만 확보)
- Supabase 연동 (로컬 파일 시스템만)
- 실시간 경기 데이터 자동 트리거

---

## 12. 관련 문서

- [DESIGN.md](DESIGN.md) — 전체 설계서
- [IDEA.md](IDEA.md) — 공유용 아이디어 요약
- [docs/safety.md](docs/safety.md) — 저작권 가드 규칙
- `openspec/changes/add-image-generation-agent/` — 본 프로젝트의 openspec change
