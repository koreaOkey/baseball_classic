# Focus Line Score Highlight on Active Half-Inning

## Why

iOS 경기 상세의 이닝별 라인스코어 카드에서 라이브 중 오렌지 하이라이트가 세 곳(현재 이닝 헤더 숫자, 공격팀의 현재 이닝 득점 셀, 공격팀 R 합계)에 동시에 적용되어 시선이 분산됨. 사용자 요청: 현재 진행 중인 초/말(공격팀의 현재 이닝 득점 셀)만 강조하고 나머지는 해제.

## What Changes

- iOS `LineScoreCard` 헤더의 현재 이닝 숫자 하이라이트 제거 — 항상 `gray500` / micro 폰트.
- iOS R 열의 공격팀 오렌지 하이라이트 제거 — 항상 white / microBold.
- 공격팀의 현재 진행 이닝 득점 셀 하이라이트(orange500 + bold)는 유지.
- Android는 원래부터 득점 셀만 하이라이트(헤더·R 무강조)라 변경 없음 — 이번 수정으로 양 플랫폼 동작 일치.

## Capabilities

### live-game-detail

- 라인스코어 카드는 LIVE 중 현재 공격팀의 진행 이닝 득점 셀 하나만 오렌지로 강조한다.
- 이닝 헤더 숫자와 R/H/E 합계 열은 경기 상태와 무관하게 무강조 색상을 유지한다.

## Impact

- `ios/mobile/BaseHaptic/Screens/LiveGameScreen.swift` — `LineScoreCard.headerRow`, `scoreRow(isHome:)` 색상 조건 단순화.
- 백엔드·Android·Watch 영향 없음.
