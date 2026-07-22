# Tasks

## 1. 빌드 기반

- [x] 1.1 compileSdk 36 bump (targetSdk 35 유지)
- [x] 1.2 core-ktx 1.17.0 upgrade
- [x] 1.3 POST_PROMOTED_NOTIFICATIONS 권한 추가

## 2. Promoted 노티

- [x] 2.1 LiveScorePromotedIconRenderer 신규 (다이아몬드 아이콘, 투명 배경)
- [x] 2.2 LiveScoreNotificationManager.post() API 36 분기 (BigText 2줄 + largeIcon + shortCriticalText + requestPromotedOngoing)
- [x] 2.3 BSO 본문 별도 줄 이모지 표시
- [x] 2.4 API 35 이하 기존 커스텀 카드 경로 보존
- [x] 2.5 워치 테스트 화면 "잠금화면 고정 (Android 16+)" 지원 여부 안내 + 설정 "테스트 도구" 진입점(워치 무관)

## 3. 검증

- [x] 3.1 :app:assembleDebug 빌드 통과
- [x] 3.2 :app:testDebugUnitTest 통과
- [x] 3.3 promoted 승격 검증 — 순정 Android 17 에뮬레이터(Pixel_8)에서 FLAG_PROMOTED_ONGOING + 잠금화면 고정 다중줄 카드 + 상태바 칩 확인 (appop POST_PROMOTED_NOTIFICATIONS allow 필요)
- [x] 3.4 아이콘 가독성 실기기 판정 → 다이아몬드 전용 + BSO 텍스트 줄로 확정, 모드 스위치 제거
- [x] 3.5 삼성 Z Fold6(One UI 8.0) 판정 — `ui_rich_ongoing` 렌더링 플래그 부재로 서드파티 승격 미지원(일반 카드 폴백 정상). One UI 8.5/9 개방 대기, 앱 수정 불필요
- [ ] 3.6 Android 15 이하 기기 회귀 확인 (기존 카드 동일 동작)
