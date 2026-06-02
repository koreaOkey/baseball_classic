## Why

라이브 경기 상세의 `InningTabs` 섹션(1~9회 탭) 이 시각만 있고 탭이 동작하지 않아, 사용자가 지나간 회의 이벤트를 다시 볼 수 없다. `project_live_detail_followups.md`(2026-05-22 백로그) #1 항목 — 가장 가벼운 운영 무중단 작업.

이벤트별 inning 메타데이터가 백엔드 응답에 없어서 모바일이 회 단위로 필터링할 근거가 없다. 본 변경은 백엔드 `game_events.inning` 컬럼 + `GameEventOut.inning` 옵셔널 필드를 추가하고 양 플랫폼에 이닝 필터 동작을 도입한다.

## What Changes

- 백엔드 `game_events` 테이블에 nullable `inning VARCHAR(32)` 컬럼 추가(`models.py:GameEvent`, `db.py:_ensure_game_event_columns` ALTER 자동 적용 — 무중단).
- 크롤러 페이로드(`CrawlerEventIn`) 에 옵셔널 `inning` 필드 추가. INSERT 시 `inning = incoming_inning or game.inning` 으로 채움.
- `GameEventOut`(`schemas.py`) 에 옵셔널 `inning: str | None` 추가, `_event_out_count`/`build_event_out` 헬퍼 갱신.
- iOS·Android `LiveEvent` 모델·파서에 `inning` 필드 추가.
- iOS `InningTabs` / Android `InningTabs` 에 `selectedInning` 상태 + tap → 콜백. 이벤트 목록을 선택 이닝으로 필터링.
- 마이그레이션 이전 행(`inning IS NULL`) 은 어느 회 탭에서도 표시되지 않으며 "이전 회 데이터 없음" 빈 상태 처리.
- `half` / `offenseTeam` 은 inning 문자열에서 derive(별도 컬럼 추가 X).

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `game-state`: 이벤트 응답에 inning 메타데이터를 포함해야 한다.
- `crawling`: (이미 부분 지원) 크롤러 페이로드에 inning 메타데이터 옵셔널.
- `mobile-ios`: 이닝 탭을 통해 회 단위로 이벤트를 필터링할 수 있어야 한다.
- `mobile-android`: 위 iOS 와 동일.

## Impact

- 백엔드: `models.py`, `schemas.py`, `services.py`, `db.py`(마이그레이션), `tests/test_api.py`. nullable ADD COLUMN — 운영 무중단.
- iOS Mobile: `BackendGamesRepository.swift` `LiveEvent` 파싱 + `LiveGameScreen.swift` `InningTabs` 동작화 + 이벤트 목록 필터.
- Android Mobile: `BackendGamesRepository.kt` + `LiveGameScreen.kt` 동일.
- Watch: 변경 없음.
- 페이로드: 이벤트 1건당 ~12바이트 증가. 영향 미미.
- DB Migration: nullable ADD COLUMN 1개. 기존 행은 NULL → 빈 상태 처리.
