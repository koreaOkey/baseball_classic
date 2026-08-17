# add-venting-fielder-error-regret — 수비 실책 fielder regret 후보 (실명 해소)

## Why

`add-venting-error-crawler-detection`으로 크롤러가 수비 실책을 이벤트 metadata
(`isError`/`errorPosition`/`errorPositionCode`)로 수집하기 시작했지만, regret-top5 산정
(`venting._select_candidates`)은 아직 이를 소비하지 않는다. 그래서 "9회 유격수 실책으로
결승점 헌납" 같은 가장 분풀이 대상이 될 순간이 후보에 안 뜬다. 이 change가 그 metadata를
소비해 **수비수(fielder) 후보**를 만들고, 기존 익명성/실명 해소 파이프라인에 태운다.

## What Changes

- `venting._select_candidates`에 **수비 실책 분기** 추가: 이벤트 payload `isError` 이고 수비팀이
  패배팀이면 fielder 후보 생성.
  - 실책이 SCORE(실점)로 분류됐어도 fielder 로 **먼저** 귀속(투수 귀속보다 우선) — 비자책
    실점의 원흉은 실책 담당이므로.
  - 심각도 가중치 `_DEFENSE_ERROR_WEIGHT = 2.5`(병살 2.0 < 실책 2.5 < 실점 3.0) + WPA.
- `_fielder_order_label`: 라인업(`game_lineup_slots`)에서 그 팀·그 포지션(한글 `position_name`
  매칭) 슬롯의 **타순**을 해소. 교체로 복수 슬롯이면 실책 이벤트 cursor 시점 활성 슬롯 우선.
  포지션 미상이면 역할 레이블 "수비수" + 타순 없음(익명 유지).
- **실명 해소는 클라이언트 무변경으로 자동 동작**: fielder 후보를 `kind="fielder"` +
  `batting_order`(라인업에서 해소)로 실으면, iOS/Android 기존 `resolvePlayerName`이 pitcher 가
  아닌 항목을 batting_order 로 박스스코어 조인 → 선택 화면 TargetRow 에 "유격수 이OO" 표기.
  룸·완파는 `role_label`("유격수")만 읽어 **익명 방화벽 유지**.
- `_fallback_reason`에 `ERROR → "실책"`, LLM 시스템 프롬프트 허용 역할 레이블에 "유격수" 추가.

## Impact

- **백엔드 전용**: `backend/api/app/venting.py` — 헬퍼 1개 + 후보 분기 1개 + 라벨/프롬프트 2곳.
  캐시 item 구조(kind/team_side/batting_order/role_label/event_type/reason)는 **기존 그대로**라
  스키마/엔드포인트 변경 없음.
- **클라이언트(iOS/Android) 코드 변경 없음** — `kind`는 자유 문자열, 실명 해소는 batting_order
  경로 재사용, 렌더는 `roleLabel(+playerName)` 그대로.
- **DB 변경 없음** — 기존 `game_lineup_slots`(position_name·batting_order) 읽기만.
- **운영 안전**: regret 산정은 out-of-band 백그라운드 + 예외 격리(실패 시 규칙 폴백), 플래그
  게이트(`venting_backend_enabled`) 뒤. 후보가 안 잡히면 기존 batter/pitcher 후보만 유지(회귀 없음).
- 테스트: `tests/test_venting.py` fielder 귀속 + 포지션 미상 익명 2건. 백엔드 113 passed.

## Non-Goals

- 크롤러 실책 감지 자체(선행 change `add-venting-error-crawler-detection`).
- 교체·수비 시프트로 같은 포지션에 복수 선수가 있을 때의 완벽한 시점 귀속 — cursor 기반
  근사까지만(엣지케이스는 role_label 로 안전 폴백, 이름만 드물게 어긋날 수 있음).
- 클라이언트 UI 변경(불필요 — 기존 렌더/조인 재사용).

## Capabilities

### Modified Capabilities
- `venting-regret-selection`: 후보 선별에 수비 실책(fielder) 종류를 추가하고, 포지션→타순
  해소로 실명 표시를 기존 batter 경로에 태운다(룸·완파 익명 유지).
