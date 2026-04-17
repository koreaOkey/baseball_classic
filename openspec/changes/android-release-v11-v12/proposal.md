## Why
Play Console에 새 릴리스 번들 업로드를 위해 Android mobile/watch versionCode 업데이트 필요.
AdMob 배너 광고를 테스트 ID에서 프로덕션 ID로 전환하여 실제 수익화 시작.

## What Changes
- **mobile** `apps/mobile/app/build.gradle.kts`: versionCode 9→11
- **watch** `apps/watch/app/build.gradle.kts`: versionCode 10→12
- **AndroidManifest.xml**: AdMob APPLICATION_ID 테스트→프로덕션 (`ca-app-pub-7935544989894266~1737773050`)
- **BannerAd.kt**: 배너 광고 단위 ID 테스트→프로덕션 (`ca-app-pub-7935544989894266/1331426409`)

## Non-Goals
- versionName 변경 (1.0.1 유지)
- iOS 관련 변경

## Verification
- `./gradlew bundleRelease`로 mobile/watch .aab 빌드 성공 확인
- Play Console 업로드 시 versionCode 충돌 없음 확인
