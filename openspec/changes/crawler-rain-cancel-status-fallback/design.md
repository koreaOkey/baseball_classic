## Context

크롤러/디스패처는 네이버 Sports API의 `statusCode` 필드로 경기 상태를 판별한다. 그러나 우천 취소 시 `statusCode`가 변경되지 않고 `statusInfo`만 "우천취소"로 바뀌는 경우가 있어, 취소된 경기가 SCHEDULED로 남는 버그가 발생했다.

## Goals / Non-Goals

**Goals:**
- `statusCode`가 매핑되지 않을 때 `statusInfo` 텍스트로 취소/연기를 폴백 감지
- 크롤러(`backend_sender.py`)와 디스패처(`live_wbc_dispatcher.py`) 양쪽에 적용

**Non-Goals:**
- 앱(iOS/Android) UI 변경 — 이미 "경기 취소" 표시 구현됨
- 백엔드 API 스키마 변경
- `statusInfo`를 사용자에게 직접 노출 (예: "우천취소" vs "경기 취소" 구분)

## Decisions

1. **`statusInfo` 폴백은 `statusCode` 매핑 실패 시에만 동작**
   - `statusCode`가 정상 매핑되면 `statusInfo`는 무시 → 기존 동작 100% 호환
   - 이유: `statusCode`가 권위 있는 필드이고, `statusInfo`는 표시용 텍스트라 변동 가능성 있음

2. **한국어 + 영어 키워드 양쪽 매칭**
   - 한국어: "우천취소", "우천 취소", "경기취소", "경기 취소", "노게임", "경기연기", "경기 연기"
   - 영어: "CANCEL", "RAIN", "NO_GAME", "POSTPONE", "DELAY", "SUSPEND"
   - 이유: 네이버 API 응답 형식이 시즌/상황에 따라 다를 수 있음

3. **선택적 파라미터(`status_info: str | None = None`)로 추가**
   - 기존 호출부 시그니처 호환 유지, 점진적 적용 가능

## Risks / Trade-offs

- **[오탐]** `statusInfo`에 "RAIN" 키워드가 다른 맥락으로 포함될 경우 → `statusCode` 우선 매핑으로 위험 최소화. `statusCode`가 LIVE/FINISHED 등으로 매핑되면 `statusInfo`는 무시됨
- **[미탐]** 네이버가 예상 외 한국어 표현 사용 시 (예: "기상악화 취소") → 발견 시 키워드 추가로 대응
