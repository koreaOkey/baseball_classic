# add-venting-error-crawler-detection — 수비 실책 크롤러 감지 (분풀이 regret 정밀 판정용)

## Why

분풀이(venting) regret TOP5 산정(`add-venting-backend-phase2`, task 6.2)이 현재 커버하는
아쉬운 순간은 두 종류뿐이다:

- 공격 실패(타자): 병살/삼중살, 삼진, 고레버리지 아웃
- 수비 실점(투수): 실점/희생플라이/피홈런

여기서 **개인 수비 실책(에러)**이 빠져 있다. 가장 분풀이 대상이 될 만한 "9회 유격수 실책으로
결승점 헌납" 같은 순간을 후보로 잡을 수 없다. 원인은 크롤러다:

- `_classify_event_type`의 키워드 목록(홈런/병살/삼진/득점/안타/볼넷/도루…)에 **"실책"이 없어**,
  실책 타구가 무득점이면 `OTHER`, 실점으로 이어지면 `SCORE`로 뭉개져 "누구의(어느 포지션) 실책인지"가
  구조화되지 않는다.
- 크롤러가 실책을 아예 안 보는 건 아니다 — 라인스코어의 `homeError/awayError`(팀 누적 개수)는 이미
  추출하지만, 이닝·포지션·득점 연결 정보가 없어 regret 산정에 못 쓴다.

## What Changes

- `crawler/backend_sender.py`에 `_detect_fielding_error(text)` 추가: 중계 텍스트에서 수비 실책을
  감지하고 **실책을 범한 수비 포지션**을 '실책' 바로 앞의 가장 가까운 위치 토큰으로 판정(정밀 판정).
  - 오탐 제외: `실책성`(안타로 기록), `무실책`, `실책 없이`.
- 이벤트 빌드 루프에서 감지 결과를 **metadata 로만 부착**: `isError`, `errorPosition`(예: 유격수),
  `errorPositionCode`(예: SS). 실책 팀은 기존 `metadata["defenseTeam"]`(=수비팀)이 이미 가리킨다.
- **`event_type`은 절대 변경하지 않는다** — 햅틱/푸시/클라 렌더는 전부 `type` 기반이라 무영향.
- metadata 는 `POST /ingest` → `game_events.payload_json`으로 그대로 저장되어(services.py:617),
  후속 regret 산정(`venting._select_candidates`)이 실명 없이 소비할 준비를 마친다.

## Impact

- **코드**: `crawler/backend_sender.py` — 순수 함수 1개 추가 + 빌드 루프에 metadata 3키 부착.
- **테스트**: `crawler/test_backend_sender.py` — 감지 단위 6건 + 스냅샷 통합 1건. 전체 68 passed.
- **위험도**: **낮음(운영 무영향)** — event_type/haptic/push/클라 렌더 불변. 기존에 20여 개 optional
  키를 담는 metadata dict에 optional 키만 추가(자유 형식 JSON, 미지 키는 모든 소비자가 무시).
  신규 EventType 없음 → `GameEventOut.type` enum 검증 위험 없음.
- **범위**: 백엔드/DB/앱 코드 변경 없음. **크롤러 재배포만**으로 실책 데이터가 payload_json에 쌓이기 시작.

## Non-Goals

- venting `_select_candidates`가 실책 metadata를 소비해 "수비수(fielder)" regret 후보를 만드는 것 —
  별도 change(venting + LLM 프롬프트 + 클라 선택 화면 TargetRow의 fielder 렌더/실명 해소). 본 change는
  **크롤러 수집까지만**.
- 백엔드 EventType enum에 ERROR 추가 / 클라 햅틱·푸시 신설.
- 팀 누적 실책 개수(homeError/awayError) 관련 로직 변경.

## Capabilities

### Modified Capabilities
- `crawling`: 중계 이벤트 metadata에 수비 실책(포지션·코드) 감지 필드를 부착하는 요구사항 추가.
