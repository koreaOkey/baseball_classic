# fix-wear-round-screen-clipping — Wear 원형 화면 잘림 수정 (Play 심사 거절 대응)

## Why

2026-09-01 Google Play Wear 앱 심사 거절: "Wear 앱 품질 가이드라인 — 시계 모양"
(콘텐츠가 실제 디스플레이 영역 안에 들어가야 하고, 텍스트·컨트롤이 화면
가장자리에서 잘리면 안 됨). 심사 스크린샷은 빠따존 대상 선택 화면에서 마지막
칩이 하단에서 잘린 모습.

원인 2건:

1. **대상 선택 화면**: 일반 `Column` + `verticalScroll` + 고정 dp 패딩(상하
   26dp)이라, 원형 워치에서 마지막 "감독" 칩이 베젤 원호에 걸린다. 스크롤
   인디케이터도 없어 심사자에게 "잘린 화면"으로 보였다.
2. **분풀이 룸 진행 중 ✕ 버튼**: `TopStart` + `start=10dp, top=22dp` 고정
   좌표는 192dp 원형 화면에서 원 바깥 영역 — 버튼 좌측이 실제로 잘린다.

## What Changes

- **`WatchVentingSelectionScreen`**: `ScalingLazyColumn`(autoCentering) 재구성
  — 첫/마지막 항목이 항상 베젤 안쪽까지 스크롤 보장, 가장자리 항목은
  축소/페이드(잘림으로 안 보임). `PositionIndicator` 추가로 스크롤 가능성
  명시. 좌우 여백은 고정 dp 대신 화면 폭 5.2% 비율.
- **`WatchVentingScreen` ✕ 버튼**: 원형 화면이면 내접 사각형 모서리
  (화면 폭 14.6%)까지 들여쓰기, 사각 화면은 기존 10dp 유지. 완파 후 "닫기"
  버튼(BottomCenter, 10dp)은 기하 계산·에뮬레이터 검증상 원 안에 들어와 무변경.
- **Wear Compose 1.4.0 → 1.4.1**: 1.4.0의 `ScalingLazyColumn`은 targetSdk 35+
  에서 `reduce_motion` 설정 읽기 `SecurityException` 하드 크래시(알려진 버그,
  1.4.1에서 수정). targetSdk 36인 본 앱은 1.4.1 필수.

## Impact

- **Wear 2파일 + 빌드 1파일**: `venting/WatchVentingSelectionScreen.kt`,
  `venting/WatchVentingScreen.kt`, `apps/watch/app/build.gradle.kts`
- 폰(Android/iOS)·watchOS·백엔드 무변경. 다른 워치 화면은 가장자리 정렬
  요소 부재 확인(grep) — 수정 불요.
- Wear OS Small Round 에뮬레이터(384×384, 192dp — 최소 원형)에서 가짜 경기
  주입으로 4개 상태(선택 상단/하단·룸·완파) 스크린샷 검증 완료.
