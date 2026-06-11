## Why

Android 모바일 앱은 iOS와 동일한 정보 구조와 토큰을 따르지만, 시스템 바, 하단 내비게이션, 홈 카드 밀도, 상세 화면 고정 치수 때문에 실제 화면 인상이 더 무겁고 덜 정돈되어 보인다. 현재 Android가 운영 중인 주 플랫폼이므로, 기능 변경 없이 시각 완성도와 플랫폼 마감을 먼저 끌어올린다.

## What Changes

- Android 모바일 앱의 다크 시스템 바와 edge-to-edge 처리를 정리한다.
- Android 하단 내비게이션의 높이, elevation, label typography, inactive color를 디자인 토큰 기준으로 다듬는다.
- Android 홈 경기 카드의 강조, 액션 row, 텍스트 밀도, 구분선을 더 조용하고 균형 있게 조정한다.
- Android 라이브 상세 점수판의 고정 치수와 텍스트 밀도를 줄여 좁은 화면에서도 답답하지 않게 보이도록 조정한다.
- Android Play Store 스크린샷 렌더러의 홈 화면을 앱 화면과 같은 방향으로 보정한다.

## Capabilities

### New Capabilities
- `android-visual-polish`: Android 모바일 화면이 iOS와 같은 디자인 언어를 유지하면서 Android 시스템 크롬과 화면 밀도까지 완성도 있게 표시되는 기준을 정의한다.

### Modified Capabilities
- 없음

## Impact

- Android 모바일 Compose theme, MainActivity bottom navigation, HomeScreen, LiveGameScreen.
- Android 리소스 theme.
- Android Play Store screenshot React renderer.
- iOS 모바일/Apple Watch/Wear OS 기능 흐름은 변경하지 않는다.
- 백엔드 API, DB, 크롤러, 워치 동기화 데이터 계약은 변경하지 않는다.
