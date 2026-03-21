## 1. 영상 에셋 준비
- [x] 1.1 원본 영상에서 2초~6초 구간(4초) 클립 추출
- [x] 1.2 배터리 최적화를 위한 리인코딩 (450x450, 20fps, 263KB)
- [x] 1.3 클립 파일을 res/raw/에 배치

## 2. ExoPlayer 통합
- [x] 2.1 build.gradle.kts에 Media3 의존성 추가
- [x] 2.2 MainActivity.kt에서 홈런 전환 화면을 ExoPlayer 기반으로 교체
- [x] 2.3 HOMERUN_SCREEN_DURATION_MS를 4000ms로 조정

## 3. 검증
- [x] 3.1 워치 에뮬레이터에서 홈런 영상 재생 확인
- [x] 3.2 배터리 소모량 확인
