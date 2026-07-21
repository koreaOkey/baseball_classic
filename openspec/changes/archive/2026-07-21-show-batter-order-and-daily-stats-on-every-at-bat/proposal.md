# Show Batter Order and Daily Stats on Every At-Bat Card

## Why

실시간 중계의 타석 카드는 relay `batterRecord`(타순·타율·오늘 성적)가 있을 때만 타자 헤더 + 오늘 성적 그리드를 보여주는데, 프로덕션 데이터 검증(2026-07-19 KT:LG, 이벤트 541건/타석 그룹 99개) 결과 네이버 relay는 **각 이닝 선두타자에게만** `batterRecord`를 첨부한다(레코드 보유 그룹 19개, 그중 18개가 HALF_INNING_CHANGE). 그래서 사용자에게는 "회가 바뀔 때만 타순·성적이 보이는" 현상으로 나타난다. 크롤러·백엔드 파이프라인 문제가 아니라 소스 데이터 한계.

## What Changes

- **iOS 타순 폴백**: 타석 그룹 안의 소개 이벤트 텍스트("3번타자 안현민")에서 `(\d+)번타자` 정규식으로 타순 추출 — 타석 시점 기준이라 가장 정확.
- **iOS 오늘 성적 폴백**: 이미 존재하는 박스스코어 API(`/games/{id}/boxscore`)의 타자 라인(타수·안타·득점·타점·홈런·볼넷·삼진)을 이름+공격팀(초=어웨이, 말=홈)으로 조인해 `batterRecord` 없는 카드에 합성 레코드 제공. relay `batterRecord`가 있으면 그것을 우선.
- **iOS 박스스코어 로딩 승격**: 기존엔 박스스코어 탭에서만 fetch → 화면 수준 `.task`로 이동해 중계 탭에서도 사용. 기존 주기(LIVE 30초, 종료 경기 1회) 유지, state 미수신 시 대기하도록 가드 보강.
- **백엔드**: `BoxscoreBatterOut.plateAppearances` 노출 (DB `game_batter_stats.plate_appearances`는 이미 존재·수집 중이었음). iOS는 구버전 백엔드 대응으로 미제공 시 `ab+bb`로 근사.
- 시즌 타율은 relay `batterRecord`에만 있으므로 폴백 카드에서는 생략 (타순·오늘 성적만).

## Capabilities

### live-game-detail

- 모든 타석 카드가 타자 이름 옆에 "N번타자"를, 아래에 오늘 성적 그리드를 표시한다 (선두타자 여부 무관).
- 박스스코어 데이터가 없으면(구버전 백엔드·fetch 실패) 기존 동작으로 자연 강등된다.
- 동명이인 오조인 방지: 초/말로 공격팀 타자 목록만 검색.

## Impact

- `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — AtBatCard 폴백 레코드, boxscoreBatterLine 조인, 박스스코어 task 승격.
- `ios/mobile/BaseHaptic/Data/BackendGamesRepository.swift` — `BoxscoreBatterLine.plateAppearances` 추가.
- `backend/api/app/schemas.py`, `services.py` — `plateAppearances` 노출 (additive, 배포 전에도 iOS 폴백 동작).
- Android 동등성은 별도 후속 PR.

## Verification

- iOS 시뮬레이터 빌드 통과.
- 백엔드 pytest 97건 전부 통과.
