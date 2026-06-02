## Context

`enrich-baseball-field-with-bg-image`(2026-06-02) 가 필드 카드 UI 인프라(배경 + 좌표 + 라벨 렌더링)를 양 플랫폼에 마련했고, `FieldLineup` 옵셔널 prop 으로 production lineup 도입을 받아들일 준비가 돼 있다.

데이터 흐름 현황(좌→우):

```
KBO/Naver preview → crawler.py(_preview_to_relay_entry) → backend POST /crawler/...
                                                              ↓
                                           GameLineupSlot 테이블 (player_name, position_name, ...)
                                                              ↓
                                           build_game_state → GameStateOut → 모바일
                                                              ↑
                                                    ❌ 이 구간 누락
```

본 변경은 마지막 구간(`build_game_state` → `GameStateOut` → 모바일 파싱) 만 채운다. 크롤러·DB 변경 없음.

## Goals / Non-Goals

**Goals**

- 백엔드 `GameStateOut` 에 홈·어웨이 lineup 필드 노출.
- 모바일이 lineup 을 받아 8개 수비 슬롯에 자동 매핑.
- 수비팀 판정: 이닝 초/말로 home / away 결정. 모바일은 수비팀 라벨만 표시.
- 한글 `positionName` → 영문 슬롯 매핑 양 플랫폼 동일.
- 백엔드 부재(과거 경기 / KBO preview 미수집 게임) 시 8개 슬롯이 깔끔히 숨겨짐(현재 동작 유지).

**Non-Goals**

- 주자 이름(`firstRunner`/`secondRunner`/`thirdRunner`) 채우기 — 이벤트 타임라인에서 진루 추적이 필요한 별개 문제.
- 수비 위치 동적 추적(이닝 중 수비 시프트, 대수비 교체) — `is_active` 만 사용. 시프트는 후속 작업.
- 라인업 미보유 경기에 대한 추정/예측.
- 워치 화면 라인업 표시.
- DB 스키마 변경.

## Decisions

1. **응답 모델은 `LineupSlotOut` 단순 슬롯 9개** — 별도 `Lineup` 래퍼 안 만들고 `list[LineupSlotOut]` 만 노출.
   - 이유: API consumer 가 단순 list 만 받으면 됨. 메타데이터(예: 라인업 수정 시간) 필요 시 future evolution.
   - 모델 필드: `battingOrder`, `playerName`, `positionCode`, `positionName`, `isStarter`, `isActive` — 6개.

2. **`is_active = true` 인 슬롯만 응답에 포함**.
   - 이유: 수비 교체 시 백엔드는 이전 슬롯 `is_active = false` + 새 슬롯 추가하는 패턴. 모바일은 현재 시점 수비만 알면 됨.
   - 대안: 전체 + flag 노출. 데이터 양 2배 + 모바일 필터 로직 필요. 본 케이스는 백엔드 필터링이 적합.

3. **한글 positionName → 영문 슬롯 매핑 헬퍼는 양 플랫폼 코드에 동일하게 직접 정의**.
   - 이유: 매핑 9개라 공유 JSON 도입은 과한 추상화. 백엔드가 영문 코드 추가 발급 시점에 통합 검토.
   - iOS: `FieldLineup.from(slots:teamSide:)` static 헬퍼. Android: `FieldLineup.from(slots, teamSide)` companion 헬퍼.
   - 매핑 테이블 (한글 → 슬롯):
     - 좌익수 → leftFielder
     - 중견수 → centerFielder
     - 우익수 → rightFielder
     - 유격수 → shortstop
     - 1루수 → firstBaseman
     - 2루수 → secondBaseman
     - 3루수 → thirdBaseman
     - 포수 → catcher
     - 투수 → (P 필드와 중복 — 무시. P 는 `state.pitcher` 우선)
   - 매핑 미스 시 슬롯 nil — 라벨 자동 숨김.

4. **수비팀 판정은 이닝 표기 파싱**.
   - 이유: `state.inning` 이 한글 `"7회초"` / `"7회말"` 형식으로 이미 들어옴. "초" = 어웨이 공격 = 홈팀 수비. "말" = 반대.
   - iOS/Android 동일 헬퍼: `isHomeDefending(inning:)` → Bool. 모바일은 결과로 `homeLineup` 또는 `awayLineup` 선택.
   - 엣지케이스: "경기 종료", "경기 전" 등 — 이닝 정보 없으면 nil lineup → 라벨 안 보임.

5. **DEBUG 더미는 production lineup 가드 뒤로 빠짐**.
   - 흐름: `gameId == "debug-watch-sync-test"` → DEBUG 더미 lineup. 그 외 → `state` 의 production lineup → 매핑.
   - 이유: DEBUG 더미는 좌표 검증용. production 흐름이 도입되면 실제 경기에서 자연 검증되고 더미는 보조 도구.

6. **응답 페이로드 크기**.
   - 슬롯 9개 × 2팀 × 평균 80바이트 = ~1.4KB 추가.
   - 영향: 모바일 fetchGameState 응답이 폴링 간격(현재 ~수 초)마다 ~1.4KB 추가. WebSocket update 도 동일.
   - 최적화: 라인업이 변하지 않으면 update payload 에서 생략(`null` 또는 omit). 현재 변경 범위 외 — 향후 최적화.

## Risks / Trade-offs

- **[Risk]** KBO/Naver preview 가 라인업을 늦게 공개하거나 미공개. 일부 경기에서 라벨이 보이지 않음.
  → 본 변경에선 수용. 정책상 라인업 부재 시 8개 슬롯 빈 채로 OK (P/B 2개는 여전히 보임).
- **[Risk]** 수비 교체 시점 — 백엔드가 `is_active` 갱신을 늦게 받으면 잠시 옛 수비수 노출.
  → 백엔드의 `lineupSlots` 페이로드 hash 비교 (`services.py:1017`) 가 변경 감지 즉시 갱신. 지연 ~수 초 수준.
- **[Risk]** "초/말" 파싱 실패 → 수비팀 판정 nil → 라벨 안 보임.
  → 폴백: pitcher/batter 는 별도 필드라 영향 없음. 외야·내야 라벨만 영향. 후속 change 에서 이닝 메타데이터(`half: "TOP"/"BOTTOM"`) API 도입 검토.
- **[Risk]** 동명이인 — 같은 팀에 같은 이름 선수.
  → 현 데이터로는 구분 불가. 라벨 표기는 이름만이라 시각 충돌은 없음. 통계 영역의 문제(별도).
- **[Risk]** 응답 크기 증가 — 모바일 폴링·실시간 트래픽.
  → 1.4KB 는 무시 가능 수준. 라인업 changed only 최적화는 후속.

## Migration Plan

1. **백엔드 스키마**: `schemas.py` 에 `LineupSlotOut` + `GameStateOut.homeLineup` / `awayLineup` 추가.
2. **백엔드 서비스**: `services.py build_game_state` 에 `GameLineupSlot` 쿼리 + 한글 positionName 그대로 매핑(가공 없음).
3. **백엔드 테스트**: 더미 lineup 시드 → API 응답 검증.
4. **iOS 모델**: `BackendGamesRepository.LiveGameState` 에 lineup 필드 추가 + `LineupSlot` struct.
5. **iOS 파싱**: `toLiveGameState()` JSON 파서 확장.
6. **iOS 매핑**: `FieldLineup.from(state:)` static 헬퍼 — 수비팀 자동 판정.
7. **iOS `LiveGameScreen.currentLineup`**: production 흐름 = `FieldLineup.from(state: gameState)`. DEBUG 더미는 fallback.
8. **Android 모델·파싱·매핑·LiveGameScreen 갱신**: 위 iOS 와 동일 패턴.
9. **빌드 검증 + 실기기 검증** — 실제 LIVE 경기 또는 백엔드 mock 으로 9명 라벨 자동 노출 확인.

Rollback: 백엔드 응답에서 lineup 필드 빼면 모바일은 nil 처리 → 8개 슬롯 자동 숨김(production 진입 전과 동일). 모바일 코드 revert 없이도 가능.

## Open Questions

- 백엔드가 영문 `positionCode` 표준(예: "LF") 을 발급할 의향 — 후속 작업에서 가능하면 매핑 단순화.
- 이닝 메타데이터 (`half: "TOP"|"BOTTOM"`) API 확장 — 한글 파싱 불안정 회피.
- 라인업 changed-only payload — WebSocket 트래픽 최적화.
- 주자 이름 추적 (별도 change) 의 데이터 소스: 이벤트 타임라인 또는 라인업 + 진루 추적 알고리즘.
