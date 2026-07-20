# Design — add-venting-mode (Phase 1: iOS 목업 프로토타입)

## Context

기획 인터뷰(interview_20260720_010304)에서 분풀이 모드의 전체 그림(백엔드 규칙 기반 TOP5, 경기당 무료 1회 + Rewarded 단일 게이트, 지표 4종)이 동결되었다. Phase 1은 그중 iOS 클라이언트 UX만 목업 데이터로 떼어내 검증한다. 디자인 기준은 hermes designer의 open-design 산출물(`DESIGN_SPEC.md` + 화면 PNG 4장, spark:~/venting_ui_output/)이며, 스프라이트 자산(펭귄 인형 4단계 + 황금 배트)도 동일 경로에서 수급한다.

## Goals / Non-Goals

**Goals:**
- 지목 → 파괴 → 해소 핵심 루프를 시뮬레이터에서 체험 가능하게 만든다.
- Phase 2에서 백엔드·광고를 끼울 경계(프로토콜)를 미리 잘라 둔다.
- 파괴 연출·햅틱의 체감 파라미터(게이지 상수, 단계 임계값)를 실기기에서 튜닝 가능한 구조로 둔다.

**Non-Goals:**
- 백엔드 호출·DB 변경·광고 SDK 연결·지표 서버 전송·Android/워치 구현(proposal Non-Goals 참조).

## Decisions

1. **데이터 경계 = 프로토콜 2개.** `RegretCandidateProviding`(TOP5 + 감독 반환)과 `VentingGateProviding`(재도전 허용 판정). Phase 1은 `MockRegretProvider`(번들 JSON: 패배 스코어 + 후보 5명 이름·사건 문구 + 감독)와 `AlwaysAllowGate`(광고 없이 재도전 허용)를 주입. Phase 2에서 실제 API/RewardedAdManager 구현체로 교체 — 화면 코드는 무수정. 대안(뷰모델에 목업 하드코딩)은 Phase 2 재작업이 커서 기각.
2. **피처 플래그.** `#if DEBUG` + UserDefaults 토글(`venting_mode_enabled`)의 이중 게이트. 릴리즈 빌드에는 코드가 포함되지 않아 심사·프로덕션 영향 0.
3. **파괴 상태는 단일 상태머신.** `gauge: 0…1`, 단계 임계값 {0.33 균열, 0.66 터짐, 1.0 완파}를 상수 struct로 분리(튜닝 포인트). 탭당 증가량은 목업에서 1/40(약 40탭 완파, 데모 편의) — 실서비스 값(120~200탭)은 Phase 2에서 원격 설정 검토.
4. **햅틱은 기존 엔진 재사용.** 탭마다 경햅틱, 단계 전환 시 중햅틱, 완파 시 성공 패턴. `live_haptic_enabled` 마스터 토글과 무관하게 동작(라이브 이벤트 햅틱이 아닌 직접 조작 피드백이므로) — 단, 시스템 저전력/무음 정책은 따른다.
5. **스프라이트 5종은 Asset Catalog 단일 세트(공용 유니폼 1종).** 팀별 10종은 Phase 2+. 투명 배경 미지원 산출물이면 로컬에서 배경 제거 후 편입.
6. **오픈 조건 시뮬레이션.** 목업 JSON에 경기 날짜·결과 포함, 판정 로직(마이팀 패배 + 당일 KST)은 실제 코드로 구현하고 입력만 목업 — Phase 2에서 판정 로직 재사용.
7. **경기당 무료 1회 상태는 UserDefaults**(`venting_free_used_<gameId>`), 인터뷰 결정(로컬 pref, 재설치 리셋 허용)과 동일.

## Risks / Trade-offs

- [스프라이트 자산 품질/투명도 미달] → 배경 제거 후처리, 최악엔 SF Symbol + 도형 기반 플레이스홀더로 개발 진행하고 자산만 후교체.
- [40탭 데모 상수가 실서비스 체감과 다름] → 상수 분리로 튜닝 1곳, 실기기 검증 시 조정.
- [연타 시 햅틱 과부하(배터리·발열)] → 탭 햅틱은 스로틀(최소 간격), 연출 프레임은 3단계 이미지 교체로 GPU 부담 최소.
- [DESIGN_SPEC 수치와 기존 디자인 토큰 충돌] → 기존 토큰 우선, SPEC은 신규 값만 차용해 토큰에 추가.

## Migration Plan

Phase 1은 DEBUG 전용이라 배포 없음. Phase 2(백엔드+광고)에서 프로토콜 구현체 교체 + 플래그 해제로 이행. 롤백 = 플래그 OFF.

## Open Questions

- 파괴 연출을 이미지 3단 교체로 갈지 스프라이트 시트 애니메이션으로 갈지 — 자산 수급 후 결정 (기본: 이미지 교체 + 흔들림/파티클은 코드).
