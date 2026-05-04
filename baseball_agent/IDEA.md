# 🎨 야구봄 이미지 생성 Agent — 구현 아이디어 공유

> Week6 State Machine Agent 과제. **야구봄 앱의 테마·이벤트 콘텐츠를 LLM Agent가 자율 생성**하는 파이프라인 제안.

---

## 💡 한 줄 요약

> **"이정후 팩 만들어줘" 한 마디에서 → 선수 리서치 → 컨셉 기획 → 이미지 생성 → 저작권 검증 → 앱용 테마 팩(스플래시·홈 배경·이벤트 스틸)까지 자동 완성하는 State Machine Agent**

---

## 🎯 해결할 문제

- 현재 야구봄 테마 상점은 **색상 팔레트 UI 스킨**만 있음 (딥 네이비·체리로즈 등)
- 선수·경기 이벤트와 연계된 **세계관 콘텐츠 부재**
- 수작업 디자인은 단가·시간이 커서 팩을 늘릴 수 없음
- → **LLM Agent로 팩 생산을 자동화**

---

## 🏗️ 사용 기술 스택

| 구분 | 기술 | 왜 이 기술? |
|---|---|---|
| **Agent 프레임워크** | **LangGraph** | State Machine·조건부 엣지·서브그래프 fan-out을 선언적으로 구성 → 과제 요구 "상태 기반 Workflow" 정석 |
| **LLM** | **GPT-4o-mini** (과제 권장) | 의사결정·컨셉 기획·프롬프트 엔지니어링 전반, 비용 효율 |
| **Tool Calling** | **OpenAI Function Calling** | 7개 Tool을 LLM이 자율 선택·호출하는 과제 핵심 메커니즘 |
| **이미지 생성** | **DALL-E 3** | 스플래시·홈 배경·이벤트 히어로 스틸 생성 |
| **이미지 검증** | **GPT-4o Vision** | 생성 이미지에서 얼굴·로고 감지 (저작권 가드) |
| **웹 검색** | **Tavily** 또는 OpenAI 검색 | 선수 최근 이슈·계절 트렌드 자율 리서치 |
| **상태 모델링** | **Pydantic** | State 타입 안전성·검증 |
| **이미지 처리** | **Pillow + scikit-learn** | 팔레트 K-means 추출 → 디자인 토큰 JSON 생성 |
| **로깅** | **rich** | 발표용 예쁜 실행 로그 |
| **출력 포맷** | **JSON (manifest·tokens·meta)** | 야구봄 기존 디자인 토큰 스키마에 그대로 주입 가능 |

---

## 🧠 핵심 아이디어: Supervisor + 서브그래프

### 왜 이 구조?
- 단일 Agent는 프롬프트·검증이 뒤섞여 품질 저하
- 완전 분리 2 Agent는 Research가 중복 구현됨
- **✅ Supervisor + 서브그래프**: Research는 공유, 생성·검증은 분리, 병렬 실행 가능

### 구조도

```
        사용자 입력 ("이정후 팩 만들어줘" / "auto")
                        │
                        ▼
┌─────────────── SUPERVISOR AGENT ─────────────────┐
│  PLAN (자율 3-way)                                │
│    ├─ player_pack   (테마 + 이벤트)                │
│    ├─ theme_pack    (테마만)                       │
│    └─ event_pack    (이벤트만)                     │
│                                                   │
│  RESEARCH (공유)                                   │
│    선수 DB → 웹 검색 → 시각 모티프 추출             │
│    (얼굴·로고 키워드 자동 필터링)                   │
│                                                   │
│  DISPATCH (fan-out 병렬)                          │
└──┬────────────────────────┬──────────────────────┘
   ▼                        ▼
THEME_SUBGRAPH          EVENT_SUBGRAPH
(스플래시·홈·잠금        (HR·HIT·STEAL·WALK·
 + 디자인 토큰 JSON)      STRIKEOUT 히어로 스틸)
   │                        │
   └────────────┬───────────┘
                ▼
       packs/pack_XXX/
       ├── manifest.json
       ├── theme/{splash, home_bg, lock, tokens.json}
       └── events/{HR, HIT, STEAL, ...}/{hero.png, meta.json}
```

---

## 🔀 Agent의 "자율성" 포인트

> 과제 정의 *"Agent란 스스로 계획을 세우고 판단하여 행동하는 주체"*에 맞춰 5개 판단 지점을 Agent에 위임.

1. **PLAN 3-way** — `auto` 모드일 때 player/theme/event 중 무엇을 만들지 자율 결정
2. **CHOOSE_PLAYER** — 이슈 있는 선수를 웹 검색 기반으로 자율 선정
3. **Gap 분석** — 카탈로그 공백 축(계절·무드·팀) 자율 판단
4. **컨셉 스코어링** — LLM이 후보 컨셉 3~5개 생성 후 스스로 점수 매겨 최고안 선택
5. **실패 원인 판단** — 얼굴 감지 vs 대비 실패 구분해서 각각 다른 리파인 전략 실행

---

## 🔧 Tool 7종 (과제 요구 "2+" 초과 달성)

| # | Tool | 역할 |
|---|---|---|
| 1 | `player_database` | KBO 선수 mock DB 조회 (Static Storage) |
| 2 | `web_search` | 최근 이슈·트렌드 검색 (과제 명시 요구) |
| 3 | `catalog_analyzer` | 기존 테마 12종 gap 분석 |
| 4 | `dalle_generate` | DALL-E 3 이미지 생성 |
| 5 | `face_logo_detector` | GPT-4o Vision 저작권 가드 |
| 6 | `color_extractor` | K-means 팔레트 → 디자인 토큰 JSON |
| 7 | `pack_writer` | 팩 디렉토리·manifest 저장 |

---

## 🛡️ 저작권 가드 (핵심 차별점)

- ❌ 실제 선수 얼굴·초상 금지
- ❌ 구단 공식 로고 재현 금지
- ✅ 등번호·팀 공식 컬러·포지션 실루엣·플레이 모션·별명 모티프만 허용
- ✅ 검증 실패 시 **추상화 레벨 자동 상향** → 재생성 루프

예: "이정후의 얼굴" → **"11번·샌프 오렌지·빠른 스윙·외야 글러브의 추상 캐릭터"** 로 번역

---

## 🔀 조건부 분기 9축 (과제 요구 충족)

1. PLAN 3-way 모드 결정
2. needs_player 분기
3. needs_catalog 분기
4~6. Theme 3요소(splash·home·lock) 검증 실패 → REFINE 루프
7. Event 얼굴 감지 → 추상화 레벨 상향 루프
8. Event 3회 실패 → SKIP (다음 이벤트 계속)
9. MERGE graceful degradation (일부 실패해도 성공분 저장)

---

## 🎬 데모 시나리오

```bash
# 시나리오 A: 완전 자율
$ python main.py --mode auto
[PLAN] auto → player_pack 선택
[CHOOSE] 이정후 (웹 검색: "복귀전 2안타")
[RESEARCH] CF / 11번 / 오렌지·검정
[DISPATCH] theme + events 병렬 시작
[THEME] splash·home·lock → PASS
[EVENTS] HR·HIT·STEAL·WALK 성공, STRIKEOUT 3회 실패 → SKIP
[PACK] packs/pack_20260423_01_leejungho/ 완성
✓ 9/10 에셋, $0.36, 총 8분
```

---

## 🎁 추가 설계: 향후 비서 레이어 (OpenClaw, 과제 범위 외)

- Python Agent는 **단독 실행 가능** (`python main.py` 채점 보장)
- 별도로 **OpenClaw** 비서가 Slack/iMessage/음성 명령을 받아 Agent를 호출
- Cron으로 **매일 아침 06:00 자동 팩 생성** 가능
- **역할 경계**: OpenClaw는 "의도 번역"까지, Agent는 "결정·생성" 담당
  → *이 경계를 지켜야 과제 규정(State Machine·Tool·분기)이 무너지지 않음*

---

## ✅ 과제 규정 체크

| 요구사항 | 충족 |
|---|---|
| State 정의 | Pydantic 11필드 `PackState` |
| Node 단위 분리 | 15~18개 Node |
| 조건부 분기 | 9축 |
| Tool 2+ | **7개** |
| `python main.py` 실행 | `--mode auto` 기본값으로 단독 작동 |
| 단순 LLM 호출 아님 | Research·Verify·Retry 체인 |
| 상태 1회 응답 아님 | 풀 State Machine 순회 |
| Workflow 완성 | 팩 디렉토리까지 산출 |
| 데모 시나리오 1+ | **3종 제공** |

---

## 🚫 Non-Goals (이번 차수 제외)

- 영상·애니메이션 자동화 (스틸 + 메타 힌트까지만)
- 야구봄 앱 실제 통합 (후속 openspec change)
- OpenClaw 실제 배포
- Supabase 연동
- 실시간 경기 데이터 기반 자동 트리거

---

## 📌 결론

> **"LangGraph Supervisor + Theme/Event 서브그래프 + 저작권 가드"** 조합으로,
> 과제의 State Machine·조건부 분기·Tool 호출 요구를 전부 충족하면서
> **야구봄 실제 프로덕트의 콘텐츠 생산 파이프라인으로 발전 가능**한 Agent를 만든다.
