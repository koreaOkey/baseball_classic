## Context

`redesign-live-game-detail-screen`(2026-05-22) 가 라이브 상세 화면을 6개 섹션 구조로 재정비하면서 4번째에 `InningTabs` (1~9회 + 득점 탭) 를 표시했다. 단 현재 회 강조 외에는 탭이 시각 장식이고 클릭 불가. design.md Decision 3에서 "이벤트 응답에 이닝 메타데이터가 없어 정확한 이닝별 필터링은 아직 불가능"로 명시됐다.

`project_live_detail_followups.md` 백로그 #1 항목으로 등록된 가장 가벼운 후속 작업. 백엔드 nullable ADD COLUMN + 옵셔널 필드 추가 + 클라이언트 lenient parser 라 운영 무중단.

## Goals / Non-Goals

**Goals**

- 이벤트 응답에 발생 이닝(`inning` 문자열, 예: `"7회초"`) 포함.
- 모바일에서 1~9회 탭을 클릭해 해당 회 이벤트만 필터링.
- 마이그레이션 이전 데이터(NULL inning) 에 대한 깨끗한 빈 상태.
- 운영 무중단.

**Non-Goals**

- `half`(초/말) / `offenseTeam` 을 별도 컬럼으로 도입 — inning 문자열에서 derive.
- 이닝별 통계 표시(스코어보드 줄별 합산 등).
- 워치 화면 이닝 필터.
- 백엔드 시계열 인덱스 도입 — 기존 `idx_game_events_game_cursor` 충분.
- DB 마이그레이션 이전 행의 inning backfill 시도(추정 불가능한 케이스 다수).

## Decisions

1. **inning 컬럼 타입은 `VARCHAR(32)`**.
   - 이유: 현재 `game.inning` 도 동일 형식의 자유 문자열(`"7회초"`, `"연장 12회말"` 등). 같은 표기 그대로 저장하면 derive 일관.
   - 대안: `inning_number INT` + `half ENUM`. 정규화는 우월하지만 derive 로직이 양쪽에 흩어지고 데이터 손실(연장 표기 등).

2. **INSERT 시 fallback 정책**.
   - 우선순위: `payload.inning > game.inning > NULL`.
   - 이유: 크롤러가 이벤트 단위로 inning 을 줄 수 있을 때(이상적) 그 값 사용. 누락 시 게임 현재 inning 으로 채움(거의 정확). 둘 다 없으면 NULL.
   - 마이그레이션 이전 데이터는 NULL — backfill 안 함(다음 INSERT 부터 자연 채움).

3. **이벤트 필터링은 정확 일치(`event.inning == selectedInning`)**.
   - 이유: 현재 동일 표기 사용. derive 없이 빠르게 동작.
   - 대안: 회 번호 추출 후 비교(`extractInningNumber(event.inning) == selectedNumber`). 미래의 "연장 10회초" 같은 표기에 견고. 단, 백엔드와 모바일이 모두 같은 정규화를 가져야 해서 복잡성↑. 1단계는 단순 일치로 가고 필요 시 후속에서 정규화.

4. **선택 탭 default 는 "현재 회"**.
   - 화면 진입 시 `selectedInning = state.inning` 으로 초기화.
   - state.inning 변경 시 자동 따라감 — 단 사용자가 다른 회 탭 누른 상태에서는 사용자 선택 유지(`hasManualSelection` flag).
   - 대안: 항상 현재 회 강제. 사용자가 "지나간 회 보다가 갑자기 점프"하는 경험이 안 좋음.

5. **빈 상태 카피**: "해당 회 이벤트가 없습니다".
   - 마이그레이션 이전 데이터 / 정말 이벤트 없는 회 모두 같은 카피.

6. **half/offenseTeam derive (모바일)**.
   - "초" 포함 → 어웨이 공격 / "말" 포함 → 홈 공격. 양 플랫폼 동일 헬퍼.
   - 본 change 에서는 derive 만 정의하고 UI 적극 활용은 없음(향후 색상 강조 등 후속 작업).

## Risks / Trade-offs

- **[Risk]** 크롤러가 일관되게 inning 을 주지 않으면 `game.inning` fallback 으로 채워서 동시 발생한 이벤트가 다른 회로 분류될 수 있음(이닝 전환 직후).
  → 영향 미미. 사용자가 1~2개 이벤트가 옆 회에 보여도 시각 임팩트 작음.
- **[Risk]** 마이그레이션 이전 데이터는 NULL → 사용자가 옛 경기 다시 봤을 때 모든 회 탭이 비어있음.
  → 명시적 빈 상태 카피로 안내. 사용자 혼란 최소.
- **[Risk]** `VARCHAR(32)` 자유 문자열 → 추후 정규화 작업 비용.
  → 1단계에선 단순. 필요 시 후속에서 `inning_number INT` + `half` 컬럼 추가하는 별도 change.
- **[Risk]** 클라이언트 lenient parser — 구버전 앱이 새 응답에 노출되어도 inning 필드 무시.
  → backward-compat 유지.

## Migration Plan

1. **백엔드 모델**: `models.py GameEvent.inning: Mapped[str | None]` 추가.
2. **백엔드 마이그레이션**: `db.py _ensure_game_event_columns()` 에 `inning VARCHAR(32)` ALTER TABLE 추가.
3. **백엔드 입력 스키마**: `CrawlerEventIn` 에 옵셔널 `inning: str | None` 추가.
4. **백엔드 출력 스키마**: `GameEventOut.inning: str | None` 추가.
5. **백엔드 서비스**: `services.py` INSERT 시 fallback 적용. `_event_out_count`/이벤트 응답 빌더 갱신.
6. **백엔드 테스트**: pytest 시드 + 응답 검증.
7. **iOS 모델·파서**: `LiveEvent` 에 `inning` 추가, `parseLiveEvent` 확장.
8. **iOS UI**: `InningTabs` 에 `selectedInning` 상태 + onTap + 이벤트 목록 필터.
9. **Android 모델·파서**: 동일.
10. **Android UI**: 동일.
11. **빌드 검증 + 실기기 검증**.

Rollback: 모바일은 코드 revert 시 lineup 처럼 자동 fallback(필터 미적용). 백엔드는 컬럼 두고 코드만 revert.

## Open Questions

- 이닝 정규화(`inning_number INT` + `half`) 도입 — 필요해지는 시점에 후속 change.
- "득점" 탭 별도 동작 — 본 change 범위 외. 현재는 클릭 무시.
- 워치 화면 이닝 필터 도입 — 별도.
