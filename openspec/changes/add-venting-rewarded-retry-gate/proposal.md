# add-venting-rewarded-retry-gate — 빠따존 재도전 Rewarded 광고 게이트 + 광고 퍼널 지표

## Why

빠따존 릴리즈 오픈(open-venting-in-release) 시점에 룸 재도전이 `AlwaysAllowGate`
(무제한 무료)였다. 2026-07-20 기획 동결안은 "경기당 첫 완파 무료(로컬 pref), 재파괴는
AdMob Rewarded 단일 게이트"였고, 운영에서 실제 광고 시청·전환을 확인할 수단도 필요했다.

## What Changes

- **재도전 광고 게이트 (iOS·Android)**: 진입·첫 완파는 무료(진입 게이트 없음, 동결안
  "IAP·진입 게이트 영구 제외" 준수). 완파 화면의 재도전은 매번 Rewarded 광고 1회
  시청 후 허용.
  - iOS: `RewardedAdGate`(신규, `VentingGateProviding` 구현) — 코디네이터 gate를
    `AlwaysAllowGate` → `RewardedAdGate`로 교체. 프로토콜 `requestRetry` 반환을
    `VentingRetryVerdict`(adRewarded/allowedFree/denied)로 확장. `AlwaysAllowGate`는
    디버그/프리뷰용으로 유지.
  - Android: `VentingRetryGate`(신규, suspend + `RewardedAdManager` 재사용) — 완파
    화면 재도전 클릭에 게이트 배선(기존에 게이트 부재).
  - 판정 정책(기존 테마 스토어·워치 동기화 게이트와 동일): 보상 획득 → 허용,
    로드 실패(no-fill 등) → 무료 허용(사용자 귀책 아님), 중도 이탈/중복 요청 → 거부.
  - 버튼 라벨 "재도전하기" → "광고 보고 재도전하기"(▶ 아이콘), 로드 중 스피너·중복 탭 가드.
- **광고 유닛**: 전용 유닛 발급 전까지 테마 스토어 Rewarded 유닛 재사용
  (iOS `ventingRetryAdUnitID`, Android `VENTING_RETRY_AD_UNIT` — 상수 1곳 교체로 전환).
  DEBUG 빌드는 기존 패턴대로 AdMob 테스트 유닛.
- **지표 추가**: `retry_ad_complete`(보상 획득) 이벤트 신설 — 클라이언트 보고 +
  백엔드 `VALID_VENTING_EVENT_TYPES` 등록. 기존 `retry_ad_start`는 광고 버튼 탭
  시점으로 유지. 로드 실패 무료 통과는 complete로 집계하지 않는다(광고 완료 수 정확성).
- **광고 퍼널 조회 API**: `GET /venting/ad-funnel?days=N` (X-API-Key, crawler 키) —
  retry_prompt_shown → retry_ad_start → retry_ad_complete 이벤트별 건수·순 사용자
  수(로그인 user_id DISTINCT) + completion_rate + KST 일별 추이.

## Impact

- **iOS 8파일**: RewardedAdGate(신규) + pbxproj 등록, VentingGateProviding,
  AlwaysAllowGate, RewardedAdManager, VentingRoomViewModel, VentingFlowCoordinator,
  VentingDestroyedScreen, VentingEventsReporter(주석).
- **Android 5파일**: VentingRetryGate(신규), RewardedAdManager, VentingDestroyedScreen,
  VentingEventReporter(주석).
- **백엔드 3파일**: venting.py(이벤트 타입 + `get_ad_funnel`), main.py(엔드포인트),
  models.py(주석). DB 스키마 변경 없음(기존 venting_event 테이블).
- **배포 순서**: 백엔드 먼저(staging push) — 배포 전 앱이 `retry_ad_complete`를 보내면
  400이지만 리포터가 best-effort라 사용자 영향 없음.
- 빌드 검증: iOS Debug+Release, Android compileDebug+ReleaseKotlin, 백엔드 py_compile 통과.

## Non-Goals

- 광고 라운드 강화 연출(황금 배트 등) — 동결안의 선택 요소, 후속.
- 전용 AdMob 유닛 발급(콘솔 작업) — 발급 후 상수 교체만 필요.
- Firebase 등 분석 SDK 도입(동결안 non-goal 유지).

## Capabilities

### Added Capabilities
- `venting-monetization`: 재도전 Rewarded 게이트 정책과 광고 퍼널 지표.
