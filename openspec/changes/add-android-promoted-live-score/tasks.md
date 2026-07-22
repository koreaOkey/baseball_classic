# Tasks

## 1. 빌드 기반

- [x] 1.1 compileSdk 36 bump (targetSdk 35 유지)
- [x] 1.2 core-ktx 1.17.0 upgrade
- [x] 1.3 POST_PROMOTED_NOTIFICATIONS 권한 추가

## 2. Promoted 노티

- [x] 2.1 LiveScorePromotedIconRenderer 신규 (COMPOSITE / DIAMOND_ONLY 2모드)
- [x] 2.2 LiveScoreNotificationManager.post() API 36 분기 (BigText + largeIcon + shortCriticalText + requestPromotedOngoing)
- [x] 2.3 아이콘 모드 전환 스위치 (SharedPreferences promoted_icon_mode)
- [x] 2.4 API 35 이하 기존 커스텀 카드 경로 보존

## 3. 검증

- [x] 3.1 :app:assembleDebug 빌드 통과
- [x] 3.2 :app:testDebugUnitTest 통과
- [ ] 3.3 Android 16 실기기/에뮬레이터에서 promoted 고정·칩 표시 확인
- [ ] 3.4 COMPOSITE 아이콘 BSO 점 실기기 가독성 판정 → 모드 확정 (미달 시 diamond_only 전환)
- [ ] 3.5 삼성 One UI 8 기기 Now Bar 노출 확인
- [ ] 3.6 Android 15 이하 기기 회귀 확인 (기존 카드 동일 동작)
