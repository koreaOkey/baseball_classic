# Bump Android target SDK to 36 (Android 16)

## Why

Google Play 정책: 2026년 8월 31일부터 최신 Android 출시(Android 16)로부터 1년 이내의
대상 API 수준(targetSdk 36)을 타겟팅하지 않는 앱은 업데이트를 게시할 수 없다.
현재 두 Android 앱 모두 targetSdk 35에 머물러 있어 마감 전에 상향이 필요하다.

Play Console 경고 원문:
> 앱이 Android 16(API 수준 36) 이상을 타겟팅해야 함 … 2026년 8월 31일부터 최신
> Android 출시로부터 1년 이내의 대상 API 수준을 타겟팅하지 않는 경우 앱을 업데이트할 수 없습니다.

## What Changes

- **모바일** (`apps/mobile/app/build.gradle.kts`): `targetSdk = 35 → 36`
  (compileSdk는 이미 36).
- **워치 / Wear OS** (`apps/watch/app/build.gradle.kts`): `compileSdk = 35 → 36`,
  `targetSdk = 35 → 36`. targetSdk는 compileSdk를 초과할 수 없으므로 둘 다 상향.
- minSdk는 변경 없음 (모바일 26, 워치 30).
- 빌드 툴 상향 불필요: 이미 AGP 8.13.2 + Gradle 8.13 (compileSdk 36 지원). SDK
  platform android-36도 로컬에 설치 확인됨.

## Impact

- **빌드 검증**: 모바일·워치 두 모듈 `:app:assembleDebug` 클린 빌드 통과(둘 다 exit 0,
  APK 생성 확인).
- **릴리즈**: 다음 배포 시 versionCode 상향 필요(모바일 29, 워치 30 현재값 기준).
- 백엔드/서버 영향 없음.

## Android 16 동작 변경 소스 감사 결과 (2026-08-09)

targetSdk 36 상향 시 새로 적용되는 동작 변경을 소스에서 전수 점검. **하드 블로커 없음.**

- **16KB 페이지(Play 필수)**: 두 APK 모두 네이티브 `.so` 포함
  (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`). ELF LOAD 세그먼트
  `p_align` 전부 `0x4000`(16384) = **16KB 정렬 ✅ 준수**. AGP 8.13.2 + 최신 AndroidX가
  자동 처리. 조치 불필요.
- **화면 방향 잠금**: `screenOrientation`/`resizeableActivity` 선언 0건 → 대화면
  방향 강제 해제 변경 **영향 없음**.
- **Predictive back**: 레거시 `onBackPressed()` 오버라이드 없음,
  `enableOnBackInvokedCallback=false` 없음. Compose `BackHandler`만 사용(predictive-back
  호환) → **안전**. 애니메이션만 기본 노출.
- **Edge-to-edge**: 모바일이 `statusBarColor`/`navigationBarColor` 설정하나 이미
  targetSdk 35에서 deprecated·무시되던 값(신규 회귀 아님). 앱은 이미 edge-to-edge 렌더링.
  콘텐츠가 시스템 바 뒤로 가리는지 시각 확인만 권장.
- **포그라운드 서비스**: 양쪽 `dataSync` FGS 사용. 6h/24h 런타임 쿼터는 Android 15(35)에서
  이미 도입됨(신규 아님). `onTimeout()` 미구현이나 단일 경기(~3–4h) < 6h라 통상 안전.
  하루 다수 경기 장시간 사용 시 상한 도달 가능 → 후속 하드닝 후보(`onTimeout()` 추가).
- **알림 권한**: `POST_NOTIFICATIONS` 런타임 요청 이미 구현.
- **Job/Alarm**: WorkManager/JobScheduler/AlarmManager/ExactAlarm 미사용 → 쿼터 변경 무관.

**결론**: SDK 버전 상향 외 추가 코드 변경 불필요. 배포 안전. (선택적 하드닝: dataSync
서비스에 `onTimeout()` 추가.)
