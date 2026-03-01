## 2026-03-01 (일요일)

- 원격 저장소 `origin/main` 최신 커밋으로 로컬 `main` 브랜치 fast-forward 업데이트 완료
- 로컬 불필요 변경 정리
  - `apps/GITREAD.md` 원복
  - `.gitignore`에 `Real-time Baseball Broadcast/` 추가하여 미추적 파일 표시 숨김
- 워치 홈런 애니메이션 작업
  - 원본 영상 `apps/watch/animate/homerun_minion.mp4`에서 `2초~6초` 구간(4초) 클립 생성
  - 클립 파일을 `apps/watch/app/src/main/res/raw/homerun_minion_clip.mp4`로 배치
  - `MainActivity.kt`에서 홈런 전환 화면을 Media3 ExoPlayer 기반 영상 재생으로 교체
  - `HOMERUN_SCREEN_DURATION_MS`를 4000ms로 조정
  - `apps/watch/app/build.gradle.kts`에 Media3 의존성 추가
- 워치 배터리 최적화를 위해 영상 리인코딩
  - 960x960 / 24fps / 1.6MB -> 450x450 / 20fps / 263KB
