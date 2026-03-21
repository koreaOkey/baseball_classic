## Why
워치에서 홈런 이벤트 발생 시 시각적 임팩트를 높여 사용자 몰입감을 강화하기 위함.
기존 텍스트/진동 기반 알림만으로는 홈런의 특별함을 전달하기 어려움.

## What Changes
- 워치 홈런 전환 화면을 Media3 ExoPlayer 기반 영상 재생으로 교체
- 배터리 최적화를 위한 영상 리인코딩 (960x960/24fps/1.6MB → 450x450/20fps/263KB)

## Capabilities
### Modified Capabilities
- `watch-app`: 홈런 이벤트 시 4초간 애니메이션 영상 재생 추가
- `themes`: 홈런 애니메이션 에셋 및 재생 시간 정의

## Impact
- apps/watch/app/build.gradle.kts — Media3 의존성 추가
- apps/watch/app/src/main/java/.../MainActivity.kt — ExoPlayer 기반 전환 화면
- apps/watch/app/src/main/res/raw/ — 최적화된 영상 파일
