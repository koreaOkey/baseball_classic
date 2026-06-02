## Why

라이브 경기 상세의 2번째 섹션 `BaseballFieldCard` 는 현재 `Canvas`/`Path` 로 직접 그린 단순 다이아몬드 + 루상 점유 원 + 투수/타자 이름만 표시한다. 사용자가 제공한 reference("경기 진행 카드.png") 처럼 야구장형 배경 위에 선수 이름을 포지션별로 띄우는 시각이 정보 밀도와 몰입감 모두에서 훨씬 풍부하다.

본 변경은 1단계로 **배경 이미지 도입 + 현재 백엔드 데이터로 가능한 투수·타자 2명 이름만 포지션 좌표에 배치**한다. 외야 3명·내야 4명·포수 이름은 백엔드 라인업 API 가 없어서 미표시(placeholder 또는 hidden). 2단계(라인업 API + 9명 라벨 채움)는 별도 change 로 분리한다.

## What Changes

- `BaseballFieldCard` 를 외야 + 다이아몬드 일러스트 PNG 배경 카드로 재구성한다(iOS·Android 동시).
- 배경 이미지는 higgsfield CLI 로 생성한 정적 PNG 1장(다크 야간 톤)을 양 플랫폼 assets 에 번들한다.
  - iOS: `Assets.xcassets/BaseballFieldBackground.imageset/`
  - Android: `apps/mobile/app/src/main/res/drawable/baseball_field_background.png`
- 10개 포지션 좌표(LF / CF / RF / SS / 2B / 3B / 1B / P / C / B)를 정규화된(0.0~1.0) `x`, `y` 값으로 정의한다 — 이미지 사이즈에 비례 배치.
- 투수(P)와 타자(B) 위치에는 현재 응답의 `pitcher` / `batter` 이름을 작은 캡슐 라벨로 노출한다.
- 그 외 8개 포지션은 본 변경에서는 비어 있음(다음 change 에서 채움). placeholder 점·라인은 배경 이미지에 이미 포함되어 있어 코드 레벨 placeholder 없음.
- 기존 `BaseballFieldCanvas` 코드(Compose Canvas / SwiftUI Path 로 직접 그린 다이아몬드)는 제거한다.
- 기존 루상 점유 원 표시는 배경 이미지 위 오버레이로 유지한다 — 1·2·3루 좌표는 새 정규화 좌표 사용.

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `mobile-ios`: iOS 모바일 앱의 경기 상세 필드 프리뷰는 야구장형 배경 이미지 위에 포지션별 정보를 표시해야 한다.
- `mobile-android`: Android 모바일 앱이 iOS 와 동일한 시각 구조의 필드 프리뷰를 제공해야 한다.

## Impact

- iOS Mobile: `LiveGameScreen.swift` 의 `BaseballFieldCard` / `BaseballFieldCanvas` 재구성, 신규 image asset 추가.
- Android Mobile: `LiveGameScreen.kt` 의 `BaseballFieldCard` / `BaseballFieldCanvas` 재구성, 신규 drawable 추가.
- Backend: 변경 없음(현재 응답 필드만 사용).
- Watch: 변경 없음.
- DB Migration: 필요 없음.
- 라인업 API 부재로 8개 포지션 라벨은 본 변경에서 비어 있음 — 후속 change `enrich-baseball-field-with-lineup-names` 에서 백엔드 보강 + 라벨 채움.
