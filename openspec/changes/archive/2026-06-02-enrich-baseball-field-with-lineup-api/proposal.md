## Why

`enrich-baseball-field-with-bg-image`(2026-06-02) 로 라이브 경기 상세의 필드 카드에 야구장 배경 + 10개 포지션 좌표 시스템이 도입됐고, **현재 데이터로 가능한 P/B 2명만 표시**되고 있다. 외야 3 + 내야 4 + 포수 8개 라벨은 비어 있는 상태다.

크롤러는 이미 KBO/Naver preview 의 `homeTeamLineUp.fullLineUp` 에서 선발 9명을 수집하고 백엔드 DB `GameLineupSlot` 에 저장한다(`crawler.py:60-90`, `models.py:114`, `services.py:1017`). 단 `build_game_state` → `GameStateOut` 응답에 lineup 필드가 포함되지 않아 모바일까지 도달하지 못하고 있다.

본 변경은 그 마지막 한 단계 — **백엔드 API 응답에 라인업 노출 + iOS·Android 파싱 + 한글 positionName → 8개 슬롯 매핑** — 을 마무리해서 실제 LIVE 경기에서 9명 이름이 자동 표시되게 한다.

## What Changes

- 백엔드 `GameStateOut` 에 `homeLineup` / `awayLineup` 필드 추가 — 각각 9개 슬롯(`battingOrder`, `playerName`, `positionCode`, `positionName`).
- 백엔드 `build_game_state` 가 `GameLineupSlot` 을 조회해서 위 필드를 채운다. `is_active` 가 true 인 슬롯만 포함(수비 교체 반영).
- 한글 `positionName` 매핑 헬퍼 (양 플랫폼 동일):
  - 좌익수 / LF, 중견수 / CF, 우익수 / RF
  - 유격수 / SS
  - 1루수 / 1B, 2루수 / 2B, 3루수 / 3B
  - 포수 / C
  - 투수 / P (P 는 별도 `pitcher` 필드 우선 사용)
- 수비팀 판정: 이닝 표기(`"7회초"` / `"7회말"`) 에 따라 공격팀과 수비팀을 분리. 모바일은 **수비 팀의 lineup 만** 필드 카드에 표시.
- iOS `LiveGameState` 에 `homeLineup` / `awayLineup` 필드 추가, JSON 파싱 보강. `FieldLineup` 변환 헬퍼.
- Android `BackendGamesRepository.LiveGameState` 에 동일 추가.
- `LiveGameScreen` 의 `currentLineup` 계산을 DEBUG 가드에서 **production lineup → 매핑 → FieldLineup** 로 교체. DEBUG 더미는 fallback 으로 유지.
- 주자 이름(`firstRunner` / `secondRunner` / `thirdRunner`) 은 본 변경 범위 외 — 별도 change 에서 진행(이벤트 추적 + 라인업 매칭이 필요한 별개 문제).

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `game-state`: `GameStateOut` 응답에 라인업 필드가 포함되어야 한다.
- `crawling`: (이미 구현됨) preview lineup 수집·저장 흐름 명세 정리.
- `mobile-ios`: 필드 카드의 8개 수비 라벨이 백엔드 lineup 으로 채워져야 한다.
- `mobile-android`: 위 iOS 와 동일.

## Impact

- 백엔드: `schemas.py GameStateOut` 확장, `services.py build_game_state` 에 lineup 조회 추가. 응답 크기 +18 슬롯 (홈 9 + 어웨이 9). 페이로드 ~1~2KB 증가.
- iOS Mobile: `BackendGamesRepository.swift` `LiveGameState` 파싱 확장 + 매핑 헬퍼.
- Android Mobile: `BackendGamesRepository.kt` 동일.
- Watch: 변경 없음.
- DB Migration: 필요 없음(테이블·필드 이미 존재).
- 후속 작업: 주자 이름(`firstRunner` 등) 은 별도 change.
