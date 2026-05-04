## Context

Week6 그룹 과제 "State Machine 기반 Agent MVP 구현" 트랙 중 ① Workflow(업무 요청 자동 처리)에 대응하는 구현. 야구봄 테마 상점의 콘텐츠 생산을 LLM Agent로 자동화하는 것이 목표다. 상세 설계는 `baseball_agent/DESIGN.md`에 정리돼 있으며, 본 문서는 주요 설계 결정과 대안·트레이드오프만 요약한다.

## Goals / Non-Goals

### Goals
- 과제 요구사항(State·Node·조건부 분기·Tool 2+)을 **현업 수준**으로 충족
- 입력 한 줄에서 팩 디렉토리(테마 + 이벤트)까지 **한 번에 완주**하는 Workflow
- 야구봄 기존 디자인 토큰·이벤트 타입과 **그대로 호환**되는 산출물
- OpenClaw 비서 레이어와 **느슨하게 결합**된 CLI + stdout 이벤트 인터페이스

### Non-Goals
- 영상화·애니메이션 자동화 (스틸 + 메타 힌트까지만)
- 야구봄 앱의 실제 통합 (별도 change로 분리)
- OpenClaw Custom Tool 실제 배포
- Supabase 연동
- 실시간 경기 데이터 기반 자동 트리거

## Decisions

### 결정 1: Supervisor + 전문 서브그래프 패턴 (단일 Agent·완전 분리 vs ③ 채택)

**선택**: Theme·Event 두 서브그래프로 분리하되, 상위 Supervisor에서 Research는 공유.

- **대안 A (단일 Agent + 출력 플래그)**: 프롬프트·검증 규칙이 뒤섞여 품질 저하.
- **대안 B (완전 독립 2 Agent)**: Research·DB·웹 검색이 중복 구현. `main.py` 단일 엔트리 구조가 깨짐.
- **채택 C**: Research 단계를 공유하고 downstream만 분리. LangGraph fan-out으로 병렬 실행 가능. 검증 규칙은 서브그래프별로 완전히 다름(캐릭터·이벤트 = 얼굴 감지 엄격 / 테마 배경 = 대비·구도).

### 결정 2: OpenClaw는 "의도 번역기"까지만, Agent가 "결정" 담당

**선택**: OpenClaw는 `{mode, player, concept_hint}` 파라미터까지만 생성. 팔레트·프롬프트·씬 구성은 Agent State Machine 내부 자율 판단.

- **이유**: 과제 규정상 "단순 LLM 호출만인 경우 미제출 간주". OpenClaw가 프롬프트까지 짜서 넘기면 Agent의 State·Tool·분기 대부분이 무력화돼 규정 위반 리스크.
- **부수 이득**: Python Agent가 독립 실행 가능 → 채점자가 OpenClaw 없이 `python main.py`만으로 검증 가능.

### 결정 3: 영상화는 후속 작업으로 분리, Agent는 스틸 + 메타 힌트까지만

**선택**: 이벤트별 `meta.json`에 `scene_description`, `focal_point`, `dominant_colors`, `mood_tags`를 남기고 영상 생성은 Agent 범위 외.

- **대안 A (Sora 2 비디오 자동 생성)**: 장당 $1~3, 예산 $65 대비 과다. API 안정성 변수.
- **대안 B (키프레임 3장 + 트랜지션)**: 비용 3배 증가, 품질 통제 어려움.
- **채택 C**: 스틸 1장 + 메타 JSON. 후속 영상 편집자(또는 별도 openspec change)가 이 힌트를 참고해 Lottie·CoreAnimation·MotionLayout으로 재생.

### 결정 4: 저작권 가드는 "사실 모티프 추상화" 원칙

**선택**: 실제 얼굴·구단 로고는 금지. 등번호·팀 공식 컬러·포지션 실루엣·플레이 모션·별명은 사실 정보로 허용.

- **번역 예**: "이정후의 얼굴" → "11번·샌프 오렌지·빠른 스윙·외야 글러브의 추상 캐릭터".
- **구현**: `EXTRACT_TRAITS` Node가 금지어를 필터링하고 시각 모티프만 downstream으로 전달. `face_logo_detector` Tool이 생성 결과에서 얼굴·로고를 검출하면 추상화 레벨을 상향해 재생성.
- **제한**: 본 차수는 MVP 가드레벨만. 실제 야구봄 앱 배포 전에는 외부 법무 검토를 별도 change로 진행.

### 결정 5: 재시도 상한 3회, 이벤트별 graceful degradation

**선택**: 이벤트 하나가 3회 실패하면 SKIP하고 다음 이벤트 계속. 팩은 성공분만으로 저장.

- **이유**: 비용 폭주 차단, 발표 데모 시간 단축, "부분 성공도 가치 있음"을 제품 관점에서 명시.
- **구현**: `manifest.json`에 `status: "failed_after_retry"` 기록, stdout 이벤트에도 실패 정보 방출.

### 결정 6: 팩 디렉토리 스키마는 야구봄 기존 구조와 호환

**선택**: `tokens.json`은 기존 디자인 토큰(2026-04-13 archive) 필드명·구조 그대로, 이벤트 디렉토리는 현재 이벤트 타입 이름(HR/HIT/STEAL 등) 그대로 사용.

- **부수 이득**: 향후 앱 통합 시 마이그레이션 불필요. 바로 주입 가능.
- **제약**: 기존 토큰 스키마가 변경되면 Agent 쪽 `color_extractor` 매핑도 동시 변경 필요.

## Risks / Trade-offs

### 위험 1: DALL-E 3 이미지 품질 편차
- 추상 캐릭터·씬 스타일 프롬프트는 재현성이 낮아 동일 입력에도 품질 분산이 큼.
- 완화: 프롬프트 Few-shot + 검증 루프(최대 3회). 품질 최종 판정은 사람 눈(발표용 선별은 수동).

### 위험 2: 저작권 경계 케이스
- 추상 캐릭터가 특정 선수로 식별 가능하면 초상권·퍼블리시티권 분쟁 소지.
- 완화: MVP는 등번호·팀컬러·실루엣까지만. 얼굴 윤곽이 감지되면 무조건 리젝트. 앱 실제 배포 전 법무 검토 별도.

### 위험 3: OpenAI 비용 초과
- 팩당 $0.40 × 재시도 배수 → 최악 $1.20/팩 가능.
- 완화: 재시도 상한 3회 고정, 이벤트 SKIP 정책, 개발/데모 팩 수 제한. 실시간 비용 추적 로그.

### 위험 4: LangGraph 학습 곡선
- 조원 일부가 LangGraph 미경험일 경우 Supervisor 패턴·fan-out 조립이 병목.
- 완화: 설계·스켈레톤을 조기에 확정(Week6 첫 2일 내). 학습은 Supervisor/Research 구간에 집중.

### 트레이드오프: 자율성 vs 통제성
- 완전 자율 모드(`--mode auto`)는 발표 임팩트가 크지만 결과 예측이 어려움.
- 대상 지정 모드(`--mode player_pack --player XXX`)는 결과 안정적이지만 "스스로 판단" 서사가 약함.
- 해결: 발표 때 두 모드 모두 시연. 평소 운영에는 대상 지정 모드 위주로 사용.

## Migration Plan

본 차수는 새 코드 추가만 있고 기존 코드 변경이 없어 마이그레이션 단계가 없다. 단, 향후 야구봄 앱에 실제 주입할 때 필요한 절차는 아래와 같이 후속 change로 분리:

1. **(후속 차수 1)** 야구봄 앱에 "선수 팩" 카테고리 추가 — 테마 상점 UI 확장, 팩 manifest 로더, 디자인 토큰 동적 주입
2. **(후속 차수 2)** 이벤트 히어로 스틸 로더 — 기존 안타 영상 파이프라인에 이미지 슬롯 추가
3. **(후속 차수 3)** OpenClaw 통합 배포 — Custom Tool 등록, Slack/iMessage 연결, Cron 스케줄
4. **(후속 차수 4)** 영상화 파이프라인 — `meta.json` 힌트 기반 Lottie·CoreAnimation 생성 또는 Sora 2 연동

## Open Questions

- KBO 10구단 공식 컬러를 `players.json`에 하드코딩할지, 외부 소스에서 fetch할지? (MVP는 하드코딩 권장)
- 웹 검색 공급자 Tavily vs SerpAPI vs OpenAI Responses 검색 중 선택? (비용·안정성·과제 예산 비교 필요)
- 발표 데모 시 실제 OpenAI 호출 vs 사전 녹화 화면 중 어느 쪽? (네트워크 장애·비용 리스크 고려)
- 이벤트 타입 목록을 어디까지 포함할지? (현재 앱 햅틱 매핑 기준 HR/HIT/STEAL/WALK/STRIKEOUT 5종이 기본, SCORE/ERROR 확장 여부)
