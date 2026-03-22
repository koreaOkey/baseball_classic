# Android Watch Animation Converter

MP4 영상을 Android Wear OS 워치 앱에 적용합니다.

## 입력

사용자가 다음 정보를 제공해야 합니다:
- **MP4 파일 경로**: 원본 영상 파일
- **리소스 이름** (선택): 기본값 `homerun_minion_clip`, `res/raw/` 에 저장될 파일명 (확장자 제외)
- **최대 크기** (선택): 기본값 450x450
- **최대 용량** (선택): 기본값 300KB

## 변환 절차

1. **원본 영상 분석**: ffprobe로 해상도, fps, 재생시간, 용량 확인
   ```
   ffprobe -v quiet -print_format json -show_streams <input.mp4>
   ```

2. **영상 압축/리사이즈** (필요 시): 용량이 최대 용량을 초과하면 ffmpeg으로 압축
   ```
   ffmpeg -y -i <input.mp4> \
     -vf "scale=<width>:<height>:flags=lanczos" \
     -c:v libx264 -preset slow -crf 28 \
     -an \
     -movflags +faststart \
     <output.mp4>
   ```
   - `-an`: 오디오 제거 (워치에서 무음 재생)
   - `-movflags +faststart`: 빠른 로딩
   - CRF 값을 조절하여 용량 목표 달성 (낮을수록 고품질, 18~32 범위)

3. **리소스 배치**: 압축된 MP4를 Android 프로젝트에 복사
   - 경로: `apps/watch/app/src/main/res/raw/<리소스이름>.mp4`
   - 원본 보관: `apps/watch/animate/` 디렉토리에 원본 유지

4. **코드 확인**: `HomeRunTransitionScreen` 컴포저블에서 리소스 참조 확인
   - 파일: `apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt`
   - 리소스 참조: `R.raw.<리소스이름>`
   - 재생 시간: `HOMERUN_SCREEN_DURATION_MS` 상수가 영상 길이와 맞는지 확인
   - 필요 시 코드 업데이트

5. **결과 리포트**: 원본 vs 변환 후 용량, 해상도, fps, 재생시간 비교 출력

## 주의사항

- ffmpeg이 설치되어 있어야 합니다 (`brew install ffmpeg`)
- Android Wear OS는 ExoPlayer(media3)로 MP4를 하드웨어 디코딩하므로 MP4 그대로 사용
- 워치 APK 크기 제한을 고려하여 영상 용량을 300KB 이하로 유지 권장
- `res/raw/` 파일명은 소문자, 숫자, 언더스코어만 사용 가능
- 볼륨은 코드에서 0f로 설정되므로 오디오 트랙 제거하여 용량 절약

## ExoPlayer 설정 참고 (기존 코드)

```kotlin
ExoPlayer.Builder(context).build().apply {
    setMediaItem(MediaItem.fromUri(clipUri))
    repeatMode = Player.REPEAT_MODE_OFF
    volume = 0f
    playWhenReady = true
    prepare()
}
```

## 사용 예시

```
/watch-animation-android
MP4: apps/watch/animate/score_celebration.mp4
리소스이름: score_celebration_clip
최대용량: 250KB
```
