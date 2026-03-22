# Watch Animation Converter

MP4 영상을 iOS watchOS용 이미지 시퀀스 애니메이션으로 변환합니다.

## 입력

사용자가 다음 정보를 제공해야 합니다:
- **MP4 파일 경로**: 변환할 원본 영상 파일
- **에셋 이름 prefix** (선택): 기본값 `hr_frame`, 예: `hr_frame`, `score_frame` 등
- **FPS** (선택): 기본값 20
- **해상도** (선택): 기본값 398x398 (Apple Watch 화면 크기)
- **X offset** (선택): 기본값 0, 음수=왼쪽, 양수=오른쪽

## 변환 절차

1. **기존 프레임 정리**: Assets.xcassets에서 동일 prefix의 기존 imageset 삭제
2. **프레임 추출**: ffmpeg으로 MP4 → JPEG 시퀀스 변환
   ```
   ffmpeg -y -i <input.mp4> \
     -vf "fps=<FPS>,scale=<width>:<height>:flags=lanczos" \
     -q:v 3 \
     <output_dir>/frame_%03d.jpg
   ```
3. **에셋 카탈로그 생성**: 각 프레임을 `.imageset` 폴더에 배치, Contents.json 생성
   - 경로: `ios/watch/BaseHapticWatch/Assets.xcassets/<prefix>_NNN.imageset/`
   - Contents.json 형식:
     ```json
     {
       "images": [{ "filename": "frame_NNN.jpg", "idiom": "universal", "scale": "2x" }],
       "info": { "version": 1, "author": "xcode" }
     }
     ```
4. **HomeRunTransitionScreen.swift 업데이트** (필요 시):
   - `frameCount`를 추출된 프레임 수에 맞게 변경
   - `frameInterval`을 FPS에 맞게 변경 (1초 / FPS * 1_000_000_000 나노초)
   - `frameNames` prefix 업데이트
   - `offset(x:)` 값 적용
   - 파일 경로: `ios/watch/BaseHapticWatch/Screens/HomeRunTransitionScreen.swift`
5. **Xcode 프로젝트 재생성**:
   ```
   cd ios && xcodegen generate
   ```
6. **결과 리포트**: 총 프레임 수, 총 용량, 예상 재생 시간 출력

## 주의사항

- ffmpeg이 설치되어 있어야 합니다 (`brew install ffmpeg`)
- watchOS에서 HEIC는 에셋 카탈로그에서 로드 안 됨 → 반드시 JPEG 사용
- watchOS 앱 번들 크기 제한(75MB)을 고려하여 해상도/품질 조절
- 변환 후 반드시 `xcodegen generate` 실행하여 프로젝트에 반영

## 사용 예시

```
/watch-animation
MP4: apps/watch/animate/homerun_minion.mp4
prefix: hr_frame
```
