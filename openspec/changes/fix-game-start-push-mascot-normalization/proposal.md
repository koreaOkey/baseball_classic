## Why

2026-05-10 사용자 제보: 응원팀 경기 시작 visible push 제목이 "랜더스 vs 두산"처럼 한쪽만 마스코트(SSG → 랜더스)로 표기되고 다른 쪽은 모기업명(두산)이 그대로 노출됨. 사용자 기대: 양 팀 모두 마스코트("랜더스 vs 베어스").

원인 분석:

- `backend/api/app/main.py:598-624` 의 `_TEAM_CODE_TO_MASCOT` 매핑이 **영문 코드만 키**로 가짐 (`"DOOSAN" → "베어스"`, `"SSG" → "랜더스"` …).
- `_team_display_name(raw)` (line 618-624) 은 `raw.strip().upper()` 후 lookup, 미스 시 원본 그대로 반환.
- 크롤러 (`crawler/crawler.py:441-442`) 는 네이버 KBO API 의 `homeTeamName`/`homeTeamShortName` 을 변환 없이 그대로 백엔드에 전달. 네이버 라벨은 영문 코드(`"SSG"`, `"LG"`, `"KT"`)와 한글 모기업(`"두산"`, `"롯데"`, `"삼성"`, `"한화"`, `"키움"`)이 혼재.
- `.upper()` 가 한글에는 무력 → 한글 라벨은 매핑 키와 불일치 → 변환 실패 → 모기업명 그대로 푸시 노출.

같은 결함이 `_normalize_my_team_for_watch()` (line 612-615) 와 `_team_codes_for_match()` (line 627-636) 에도 있어, 한글 라벨로 들어온 경기는 응원팀 구독 토큰 매칭이 깨질 가능성이 있음 (`my_team` 에 영문 코드가 저장된 사용자에게 한글 home/away 인 경기는 푸시 자체가 안 갈 수도).

## What Changes

- `_TEAM_CODE_TO_MASCOT` 옆에 **별칭 → 마스코트** 테이블 `_TEAM_ALIAS_TO_MASCOT` 추가. 한글 모기업 라벨 10개 + 마스코트 셀프 매핑 10개를 포함.
- `_resolve_mascot(raw)` 헬퍼 신설: `(영문 코드 / 한글 모기업 / 마스코트)` 어떤 형태로 들어와도 마스코트로 정규화. 매칭 실패 시 None.
- `_team_display_name()` 와 `_normalize_my_team_for_watch()` 를 `_resolve_mascot()` 위에 재구현 (실패 시 raw fallback 유지).
- `_team_codes_for_match()` 도 마스코트로 먼저 정규화한 뒤, 그 마스코트와 연결된 모든 영문 코드를 후보 집합에 추가하도록 보강 → 한글 home/away 경기에서도 응원팀 구독 매칭 정상 동작.

## Capabilities

### Modified Capabilities

- `ingest`: visible push (game-start) 의 title/body 가 어떤 라벨 형태로 들어오는 home/away 도 항상 마스코트 표기로 통일. `_team_codes_for_match` 가 한글 라벨에서도 영문 코드를 역추적해 응원팀 구독 매칭 깨지지 않음.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 한글 별칭 테이블이 누락된 라벨이 들어오면 여전히 원본 노출 | 현재 알려진 10팀 모두 cover. 신규 팀 진입 시 테이블 1개 추가로 대응 |
| 마스코트 셀프 매핑 추가로 입력이 이미 마스코트인 경우의 동작 변경 | 기존엔 미스 → 원본 그대로(=마스코트). 변경 후 hit → 마스코트 그대로. 결과 동일, no-op |
| `_team_codes_for_match` 가 후보를 더 넓게 잡아 의도치 않은 구독자 매칭 | 마스코트 → 코드는 1:1 매핑이라 false-positive 없음. 영문 코드 자체는 기존처럼 후보로 유지 |

## Status

- [x] 구현 완료
  - `backend/api/app/main.py:598-636` 별칭 테이블 + `_resolve_mascot` + `_team_codes_for_match` 보강
- [x] Python AST 파싱 OK (`python3 -c "import ast; ast.parse(...)"`)
- [ ] 백엔드 배포 후 다음 응원팀 경기 시작 시 푸시 제목 검증: "[원정마스코트] vs [홈마스코트]" 양쪽 모두 마스코트
- [ ] 한글 home/away 경기 (예: 두산 vs 롯데) 의 구독자 매칭 회귀 점검
