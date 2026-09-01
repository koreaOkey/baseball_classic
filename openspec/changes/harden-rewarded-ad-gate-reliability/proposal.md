# harden-rewarded-ad-gate-reliability — Rewarded 광고 콜백 신뢰성·중도 이탈 피드백

## Why

배포 전 점검(2026-08-25)에서 Rewarded 광고 공용 매니저에 세 가지 결함이 확인됐다.

1. **iOS 이중 콜백 크래시 가능성**: `AdDelegate`에 1회 발화 가드가 없어
   present 실패와 dismiss가 겹쳐 들어오면 종단 콜백이 두 번 흐른다.
   `RewardedAdGate`/`VentingRetryGate`는 continuation을 resume하므로 두 번째
   호출은 `SWIFT TASK CONTINUATION MISUSE` 하드 크래시다.
2. **스피너/오버레이 영구 대기 경로**: iOS는 `isLoading`을 present 전에 해제해
   광고 표시 중 busy 가드가 무력했고, 그 사이 두 번째 요청이
   `presentingAdDelegate`를 덮어쓰면 첫 요청의 콜백이 영영 오지 않는다
   (완파 화면 "광고 보고 재도전하기" 스피너 영구 대기). Android는 `_isLoading`
   싱글턴이 AdMob 콜백에서만 해제되는데 워치독이 없어, 콜백이 유실되면 전역
   `RewardedAdLoadingOverlay`가 프로세스 수명 내내 남고 이후 모든 게이트가
   BUSY→거부로 죽는다. Android `complete`는 멱등이 아니어서 중복 콜백 시
   테마 스토어 등 다른 게이트에서 이중 지급 여지도 있었다.
3. **광고 중도 이탈 시 무반응**: 보상 전에 광고를 닫으면(denied) 스피너만
   사라지고 아무 피드백이 없다 — 심사에서 "버튼이 동작하지 않음"으로
   걸리는 전형적 패턴.

## What Changes

- **iOS `RewardedAdManager`**:
  - `AdDelegate`에 `hasFired` 1회 발화 가드 — 종단 콜백은 정확히 한 번만 전달.
  - `isPresenting` 플래그 신설 — 광고 표시 중 새 요청은 busy 처리해
    delegate 덮어쓰기(콜백 유실)를 차단. `isLoading`(로드 오버레이용)의
    published 의미는 유지.
  - 로드 워치독(60초): 로드 콜백 유실 시 loadFailed로 마감, 세대 카운터로
    늦게 도착한 로드는 폐기(표시하지 않음).
- **Android `RewardedAdManager`**:
  - `complete` 멱등화(`settled` 플래그) — 모든 게이트 호출부의 이중 지급 차단.
  - 로드 워치독(60초, main handler): 로드 콜백 유실 시 LOAD_FAILED로 마감해
    `_isLoading`·전역 오버레이 영구 잔존 방지. `markLoadFinished`로 워치독
    선마감 시 늦게 온 광고는 표시하지 않음.
- **denied 사용자 피드백 (양 플랫폼)**: 완파 화면에서 광고 중도 이탈·중복
  요청 시 "광고를 끝까지 보면 재도전할 수 있어요" 안내 — iOS는 버튼 위
  캡션(재요청 시 자동 해제), Android는 Toast.

정책 변화 없음: 보상 획득→허용, 로드 실패→무료 허용, 중도 이탈→거부는 기존
add-venting-rewarded-retry-gate 동결 정책 그대로다. 워치독 마감은 로드 실패와
동일 취급(사용자 귀책 아님)이다.

## Impact

- **iOS 2파일**: `Components/RewardedAdManager.swift`,
  `VentingMode/Screens/VentingDestroyedScreen.swift`
- **Android 2파일**: `ui/components/RewardedAdManager.kt`,
  `venting/ui/VentingDestroyedScreen.kt`
- 백엔드·지표 스키마 변경 없음. 테마 스토어·워치 동기화·라이브 게이트는
  공용 매니저 경유이므로 신뢰성 수정의 수혜를 함께 받는다.
