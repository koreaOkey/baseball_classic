# 야구봄 이미지 생성 Agent — 설계 문서

> Week6 State Machine 기반 Agent MVP 과제 설계서.
> 과제 본체(`python main.py`)는 Python + LangGraph로 구현하고, 운영 단계에서는 OpenClaw 비서 레이어가 트리거·알림·스케줄을 담당한다.

---

## 1. 목표

- **사용자 노출명 "야구봄"의 테마 상점 콘텐츠**(앱 스플래시·홈 배경·잠금 화면·이벤트 히어로 스틸)를 LLM Agent가 자율 생성
- 입력 한 줄(예: `"이정후 팩 만들어줘"`)에서 **선수 리서치 → 시각 모티프 추출 → 이미지 생성 → 저작권 검증 → 팩 저장**까지 State Machine으로 완주
- 과제 요구사항(State·Node·조건부 분기·Tool 2+)을 **현업 수준으로** 충족
- 향후 OpenClaw 비서를 통한 자연어 트리거·Cron 자동화·Slack 알림까지 확장 가능한 인터페이스 확보

## 2. 전체 아키텍처

```
┌────────────────────────────────────────────────────────────────┐
│                    [범위 외 — 추후 확장]                        │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                    OpenClaw (비서 레이어)                 │  │
│  │  Slack / iMessage / Telegram / Voice                     │  │
│  │   자연어 → {mode, player, hint, channel} 번역             │  │
│  │   subprocess 호출 → stdout 이벤트 구독 → 채널로 푸시       │  │
│  │   Cron 자동 스케줄 (예: 매일 06:00 오늘 경기 선수 팩)      │  │
│  └───────────────────────┬───────────────────────────────────┘  │
└──────────────────────────┼──────────────────────────────────────┘
                           │ CLI 인자 + stdout JSON
                           ▼
┌────────────────────────────────────────────────────────────────┐
│            ★ 과제 범위 (03-assignment/final-XX/) ★             │
│                                                                │
│  main.py  (argparse: --mode, --player, --concept_hint)         │
│                                                                │
│  ┌──────────────────── SUPERVISOR AGENT ───────────────────┐   │
│  │  PLAN  (자율 3-way)                                     │   │
│  │    ├─ player_pack   (테마 + 이벤트)                      │   │
│  │    ├─ theme_pack    (테마만)                             │   │
│  │    └─ event_pack    (이벤트만)                           │   │
│  │                                                         │   │
│  │  ┌── needs_player? ──┐   ┌── needs_catalog? ──┐         │   │
│  │  │ CHOOSE_PLAYER      │   │ ANALYZE_CATALOG    │         │   │
│  │  │ RESEARCH_PLAYER    │   │ FETCH_TREND        │         │   │
│  │  │ EXTRACT_TRAITS     │   │ IDENTIFY_GAP       │         │   │
│  │  └────────────────────┘   └────────────────────┘         │   │
│  │                                                         │   │
│  │  DISPATCH (fan-out)                                     │   │
│  └──┬──────────────────────────┬──────────────────────────┘   │
│     ▼                          ▼                               │
│  THEME_SUBGRAPH            EVENT_SUBGRAPH                      │
│  ┌──────────────────┐      ┌─────────────────────────┐         │
│  │ SPLASH           │      │ for event in events:    │         │
│  │  └ GEN → VERIFY  │      │   BUILD_PROMPT          │         │
│  │ HOME_BG          │      │   GENERATE              │         │
│  │  └ GEN → VERIFY  │      │   VERIFY (face/logo)    │         │
│  │ LOCK             │      │   SAVE_HERO + meta.json │         │
│  │  └ GEN → VERIFY  │      │ (실패 이벤트는 스킵)     │         │
│  │ EXTRACT_COLORS   │      │ COMPILE_EVENT_SET       │         │
│  │ COMPILE_THEME    │      └────────────┬────────────┘         │
│  └────────┬─────────┘                   │                      │
│           └────────────┬─────────────────┘                     │
│                        ▼                                       │
│                  PACK_WRITER → packs/pack_XXX/                 │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

## 3. 역할 경계

| 레이어 | 책임 | 과제 범위 |
|---|---|---|
| **OpenClaw** | 자연어 → `{mode, player, hint}` 번역, 진행 알림, 결과 딜리버리, Cron | 범위 외 (추후) |
| **Supervisor** | mode 확정, 리서치 범위 결정, 서브그래프 dispatch | ✅ |
| **Research 체인** | 선수 선정, 웹 검색, 시각 모티프 추출 | ✅ |
| **Theme 서브그래프** | 스플래시·홈·잠금 3종 + 디자인 토큰 JSON | ✅ |
| **Event 서브그래프** | HR/HIT/STEAL 등 이벤트별 히어로 스틸 + 메타 | ✅ |
| **Pack Writer** | 최종 manifest·디렉토리 직렬화 | ✅ |

**원칙**: OpenClaw는 "의도"까지, Python Agent는 "결정"부터. 팔레트·프롬프트·씬 구성 등 **결정의 영역은 OpenClaw에 위임하지 않는다**. 그렇지 않으면 단순 LLM 호출로 전락해 과제 요건(State Machine·Tool·분기)이 무너진다.

## 4. 산출물 — 팩 구조

```
packs/pack_YYYYMMDD_<seq>_<label>/
├── manifest.json                    (pack_id, player, generated events, 생성 비용)
├── theme/
│   ├── splash.png                   (앱 실행 스플래시, 9:19.5)
│   ├── home_bg.png                  (홈 배경, 9:16)
│   ├── lock.png                     (잠금 화면)
│   └── tokens.json                  (야구봄 design token 스키마 호환)
└── events/
    ├── HR/{hero.png, meta.json}
    ├── HIT/{hero.png, meta.json}
    ├── STEAL/{hero.png, meta.json}
    ├── WALK/{hero.png, meta.json}
    └── STRIKEOUT/{hero.png, meta.json}
```

### tokens.json 예시 (기존 야구봄 토큰과 호환)

```json
{
  "color.primary":   "#F26722",
  "color.accent":    "#1A1A1A",
  "color.surface":   "#FFFFFF",
  "color.onSurface": "#0D0D0D"
}
```

### events/<EVENT>/meta.json 예시 (영상화 후속 작업용 힌트)

```json
{
  "event": "HR",
  "scene_description": "공이 담장을 넘는 순간, 임팩트 섬광",
  "focal_point": [0.5, 0.3],
  "dominant_colors": ["#F26722", "#1A1A1A"],
  "mood_tags": ["impact", "flash", "slow-mo-friendly"]
}
```

**영상·애니메이션 자동화는 Agent 범위 외.** 히트 영상 파이프라인에 주입하는 후속 작업자가 이 메타를 참조.

## 5. State 정의 (Pydantic)

```python
class PackState(BaseModel):
    mode: Literal["auto", "player_pack", "theme_pack", "event_pack"]

    # Research 결과
    player: PlayerProfile | None
    catalog_gap: CatalogGap | None
    trend_context: str | None
    visual_traits: VisualTraits | None

    # Theme 결과
    theme_bundle: ThemeBundle | None

    # Event 결과
    events_to_generate: list[str]
    generated_events: dict[str, EventResult]

    # 집계
    pack_id: str
    total_cost_usd: float
    retry_counts: dict[str, int]
```

## 6. Tool 7종

| # | Tool | 단계 | 요구사항 매핑 |
|---|---|---|---|
| 1 | `player_database` | RESEARCH_PLAYER | Static Storage (mock JSON) |
| 2 | `web_search` | RESEARCH / FETCH_TREND | 웹 검색 (과제 명시) |
| 3 | `catalog_analyzer` | ANALYZE_CATALOG | Static Storage |
| 4 | `dalle_generate` | Theme·Event 생성 | OpenAI DALL-E 3 |
| 5 | `face_logo_detector` | 검증 (저작권) | OpenAI GPT-4o Vision |
| 6 | `color_extractor` | EXTRACT_COLORS | PIL + K-means |
| 7 | `pack_writer` | 최종 저장 | 파일 시스템 |

## 7. 조건부 분기 9축

1. **PLAN** 3-way — mode 자율 결정
2. **needs_player** 분기 — 선수 리서치 필요 여부
3. **needs_catalog** 분기 — 카탈로그 gap 분석 필요 여부
4. **Theme Splash** 검증 실패 → REFINE
5. **Theme Home** 검증 실패 → REFINE
6. **Theme Lock** 검증 실패 → REFINE
7. **Event** 얼굴/로고 감지 → 추상화 레벨 +1 루프
8. **Event** 3회 실패 → SKIP (다음 이벤트 계속)
9. **MERGE** graceful degradation — 일부 실패해도 성공분 저장

## 8. 저작권 가드 (핵심 원칙)

- ❌ 실제 선수 얼굴·초상 생성 금지
- ❌ 구단 로고 직접 재현 금지
- ❌ 타사 캐릭터·유명인 얼굴 금지
- ✅ 등번호·팀 공식 컬러·포지션 실루엣·플레이 모션·별명 모티프 OK (사실 정보)
- ✅ 추상 픽토그램·기하 패턴 기반 스타일 권장

**번역 원칙**: "이정후의 얼굴" → "11번·샌프 오렌지·빠른 스윙·외야 글러브의 추상 캐릭터". Research 단계에서 `EXTRACT_TRAITS`가 저작권 금지어를 제거한 **시각 모티프만** 다음 단계로 전달.

상세 규칙은 `docs/safety.md` 참조.

## 9. 입력 모드 3단계

| 모드 | OpenClaw 번역 결과 | Agent 내부 자율 범위 |
|---|---|---|
| **완전 자율** | `--mode auto` | 선수 선정 + 리서치 + 컨셉 + 프롬프트 + 검증 |
| **대상 지정** | `--mode player_pack --player 이정후` | 리서치 + 시각모티프 + 컨셉 + 프롬프트 + 검증 |
| **힌트 포함** | `--mode player_pack --player 이정후 --concept_hint "복귀전 기념"` | 시각모티프 + 컨셉 + 프롬프트 + 검증 |

세 모드 모두 **동일한 State Machine을 통과**. 입력이 구체적일수록 초반 Node 일부가 skip될 뿐, Research·프롬프트·검증 체인은 항상 완주한다.

## 10. 데모 시나리오 3종

### A. 완전 자율 (`--mode auto`)

```
[PLAN] auto → player_pack 선택 (이슈 있는 선수 기반)
[CHOOSE] 이정후 (복귀전 2안타, 웹 검색 기반)
[RESEARCH] CF / 11번 / 오렌지·검정
[EXTRACT] 시각 모티프: [스윙 실루엣, 바람, CF 글러브]
[DISPATCH] theme + events 병렬 시작
[THEME] splash·home·lock 3/3, 컬러 추출 완료
[EVENT] HR·HIT·STEAL·WALK 4/5 (STRIKEOUT 3회 실패 → SKIP)
[PACK_WRITER] packs/pack_20260423_01_leejungho/ 완성
✓ 9/10 에셋, $0.36, 총 8분
```

### B. 대상 지정 (`--mode player_pack --player 김혜성`)

```
[PLAN] player_pack
[CHOOSE_PLAYER] skip (지정됨)
[RESEARCH] 김혜성: 2루수, 도루왕, 파랑·검정
[DISPATCH] theme + events 병렬
...
```

### C. 컨셉 테마만 (`--mode theme_pack --concept_hint "봄 파스텔"`)

```
[PLAN] theme_pack
[CATALOG] 기존 12종 → 봄·파스텔 부재 확인
[TREND] 벚꽃·시범경기
[THEME_SUBGRAPH] splash·home·lock → PASS
[PACK_WRITER] packs/pack_20260423_02_spring_pastel/ ✓
```

## 11. 과제 규정 체크리스트

| 규정 | 충족 방법 |
|---|---|
| State 정의 | Pydantic 11필드 `PackState` |
| Node 단위 분리 | 15~18개 Node |
| 조건부 분기 | 9축 |
| Tool 2+ | 7개 |
| `python main.py` 실행 | 기본값 `--mode auto`로 바로 작동 |
| 단순 LLM 호출 아님 | Research·Verify·Retry 체인 |
| 상태 1회 응답 아님 | 풀 State Machine 순회 |
| Workflow 완성 | 팩 디렉토리까지 산출 |
| 데모 시나리오 1+ | 3종 제공 |

## 12. OpenClaw 인터페이스 스펙 (추후 확장)

Python Agent는 OpenClaw를 모르고, 다음 두 인터페이스만 제공한다.

### CLI 인자
```
python main.py \
  --mode {auto|player_pack|theme_pack|event_pack} \
  [--player 이정후] \
  [--concept_hint "봄 파스텔"]
```

### stdout JSON progress event

```json
{"ts": "19:02:01", "node": "RESEARCH_PLAYER", "status": "start"}
{"ts": "19:02:14", "node": "RESEARCH_PLAYER", "status": "done", "player": "이정후"}
{"ts": "19:05:30", "node": "PACK_WRITER", "status": "done", "pack_path": "packs/pack_20260423_01_leejungho"}
```

종료 코드: `0=성공`, `1=부분 실패 (팩은 저장됨)`, `2=치명적 실패`.

## 13. 비용 예산

기본 모델: `gpt-image-1-mini` + `medium` 품질 (OpenAI 공식 가격 2026-04 기준).

- 1 팩 = 테마 3장 + 이벤트 5장 = 8장, 재시도 평균 1.25배 → **10장/팩**
- 이미지 장당 ~$0.009 (1,056 tokens × $8/1M output)
- Vision 검증 장당 ~$0.008
- **팩당 약 $0.17**, **35팩 총 ~$6** (그룹 예산 $65의 9%)

환경변수로 모델 스위치 가능:
- `gpt-image-1-mini` low (최저가 $0.003/img)
- `gpt-image-2` low (최신 SOTA $0.007/img)
- `dall-e-3` (레거시 $0.04/img, 하위 호환용)

재시도 상한(3회) + 이벤트 SKIP + graceful degradation으로 폭주 차단.

## 14. 폴더 구조 (과제 제출)

```
03-assignment/
└── final-XX/
    ├── main.py
    ├── requirements.txt
    ├── README.md
    │
    ├── agent/
    │   ├── __init__.py
    │   ├── state.py                   # Pydantic State
    │   ├── supervisor.py              # PLAN + DISPATCH
    │   ├── research.py                # CHOOSE / RESEARCH / EXTRACT
    │   ├── catalog.py                 # ANALYZE / TREND / GAP
    │   ├── theme_subgraph.py
    │   ├── event_subgraph.py
    │   └── pack_writer.py
    │
    ├── tools/
    │   ├── player_database.py
    │   ├── web_search.py
    │   ├── catalog_analyzer.py
    │   ├── dalle_generate.py
    │   ├── face_logo_detector.py
    │   ├── color_extractor.py
    │   └── pack_writer_tool.py
    │
    ├── data/
    │   ├── players.json               # KBO 20~30명 mock
    │   ├── catalog.json               # 기존 테마 12종 메타
    │   └── events.json                # HR/HIT/STEAL 등 정의
    │
    ├── docs/
    │   ├── safety.md                  # 저작권 가드 규칙
    │   └── architecture.md            # 다이어그램
    │
    └── packs/                         # 실행 결과 (gitignore)
```

## 15. 작업 착수 순서

| 단계 | 작업 | 시간 |
|---|---|---|
| 1 | `docs/safety.md` 저작권 가드 규칙 | 30분 |
| 2 | `data/players.json` + `catalog.json` + `events.json` mock | 1시간 |
| 3 | `agent/state.py` Pydantic State 정의 | 30분 |
| 4 | Tool 7종 stub | 1시간 |
| 5 | LangGraph Supervisor + 3 모드 PLAN | 2시간 |
| 6 | Research 체인 + `web_search` 실연결 | 2시간 |
| 7 | Theme 서브그래프 | 3시간 |
| 8 | Event 서브그래프 | 3시간 |
| 9 | Pack writer + manifest | 1시간 |
| 10 | 데모 3종 실행·검증 | 2시간 |
| 11 | README + requirements.txt | 1시간 |
| **합계** | | **~17시간 / 팀** |

## 16. Non-Goals (이번 과제에서 제외)

- 영상화·애니메이션 자동화 (히트 영상 파이프라인 주입)
- 야구봄 앱 실제 통합 (토큰 주입·스플래시 교체)
- 실시간 경기 데이터 연결 (선수 DB는 mock)
- Supabase 실제 연동 (로컬 파일 시스템만)
- OpenClaw 통합 배포 (인터페이스만 확보, 실제 래핑은 제출 후)
- Cron 자동 스케줄
- 테마 상점 "선수 팩" 카테고리 신설 (앱 변경 필요)

---

## 부록: 발표 포인트 (4/30 발표용)

1. **문제 정의** — 야구봄 테마 상점은 색상 팔레트 중심, 선수·이벤트 연계 세계관 부재
2. **Agent 시연** — `python main.py --mode auto` 1회 실행 → 팩 완성
3. **자율성 증명** — Supervisor PLAN + Research + Score-driven 컨셉 선택
4. **기술 포인트** — Supervisor 패턴, 서브그래프 fan-out, 저작권 가드, graceful degradation
5. **프로덕트 연결** — 앱 스플래시·홈 배경·이벤트 히어로 스틸로 그대로 이어짐
6. **확장 비전** — OpenClaw 비서로 Slack 명령 → 자동 팩 생성 → 알림 (로드맵)
