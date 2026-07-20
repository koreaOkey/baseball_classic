# add-venting-mode — 분풀이 모드 Phase 1 (iOS 목업 프로토타입)

## Why

패배일은 이탈일이다 — 마이팀이 진 날 팬은 앱을 닫고 그날 다시 돌아오지 않는다. 패배의 감정을 앱 안에서 해소하는 "분풀이 모드"로 패배일에 앱을 열 이유를 만든다. 기획 인터뷰(2026-07-20, interview_20260720_010304)로 결정이 동결되었고, 백엔드·과금을 붙이기 전에 iOS 목업 프로토타입으로 핵심 UX(지목 → 파괴 → 해소)를 먼저 검증한다.

## What Changes

- iOS 앱에 분풀이 모드 4개 화면 추가: 홈카드 진입 → 아쉬운 순간 TOP5 선택 → 분풀이 룸(펭귄 인형 탭 연타 파괴) → 완파.
- 파괴 메커니즘: 데미지 게이지 100% 완파, 3단계 파괴 연출(균열→터짐→완파), 단계 전환·탭마다 햅틱 피드백.
- TOP5 데이터는 클라이언트 목업 제공자로 주입 — **백엔드는 수정하지 않는다**.
- 재도전 버튼은 광고 게이트 자리만 프로토콜로 추상화하고 Phase 1에서는 광고 없이 동작 — **광고 SDK를 연결하지 않는다**.
- 오픈 조건(마이팀 패배 경기 당일 KST 자정까지, 무승부·취소 미오픈)은 목업 데이터의 경기 결과·날짜로 시뮬레이션.
- 인형/황금 배트 스프라이트 자산 추가(hermes designer 산출물), UI는 open-design 산출물(DESIGN_SPEC.md + 화면 PNG) 기준.
- DEBUG 빌드 전용 피처 플래그로 게이트 — 프로덕션 동작에 영향 없음.

### Non-Goals (Phase 2+ 후속 change)

- 백엔드 regret-top5 산정 endpoint, 감독명 테이블·지표 이벤트 테이블 DB 마이그레이션.
- AdMob Rewarded 광고 게이트 연결(경기당 첫 완파 무료 → 재파괴 광고 정책의 실동작).
- 지표 4종(room_enter/destroy_complete/retry_prompt_shown/retry_ad_start) 서버 수집. Firebase 등 분석 SDK 도입은 영구 non-goal.
- Android/Wear OS 구현, 워치 분풀이 룸(watchOS는 알림만 — Phase 2).
- 실명 선수·실제 외형 기반 인형(영구 non-goal — 인형은 익명 펭귄만).

## Capabilities

### New Capabilities
- `venting-mode`: 분풀이 모드 전체 플로우 — 오픈 조건, TOP5 선택(감독 고정 6번째, 면책 문구), 분풀이 룸 파괴 메커니즘(게이지·3단계 연출·햅틱), 완파·재도전, 경기당 무료 1회 상태.

### Modified Capabilities
- `mobile-ios`: 홈 화면에 패배 당일 "오늘의 아쉬운 순간" 카드 노출 요구사항 추가.

## Impact

- **iOS 전용**: `ios/BaseHaptic.xcodeproj` — 신규 화면 3개 + 홈카드 1개, Asset Catalog에 펭귄 스프라이트 5종 추가.
- **백엔드/DB/Android/워치 영향 없음** (Phase 1 범위 제외 명시).
- 기존 햅틱 엔진·마이팀 설정·홈카드 레이아웃을 재사용. 광고는 기존 RewardedAdManager와 후속 연결을 전제로 프로토콜 경계만 맞춰 둔다.
- 인터뷰 동결 결정 대비 차이: 백엔드 산정·광고·지표 수집이 목업/보류로 대체됨 — Phase 2에서 동결 결정 원안대로 복원 예정.
