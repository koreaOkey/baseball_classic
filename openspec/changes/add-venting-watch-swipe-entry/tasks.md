# Tasks

## 1. 후보 축적 (WatchRegretTracker)

- [x] 1.1 Wear `WatchRegretTracker` — 이벤트→이닝 레이블 변환, 심각도 가중, 경기당 초기화, prefs JSON 저장
- [x] 1.2 Wear `DataLayerListenerService` 기록 훅 (game_data 인라인 이벤트 + /haptic) + 공수 판정(recordRegretEvent)
- [x] 1.3 watchOS `WatchRegretTracker` 미러 (UserDefaults Codable)
- [x] 1.4 watchOS `WatchConnectivityManager` 기록 훅 3곳 (game_data 인라인 + haptic_event + 직접 push)

## 2. 선택 화면 + 스와이프 페이저

- [x] 2.1 Wear `WatchVentingSelectionScreen` (2모드 제목, 후보 칩 + 감독 고정, 탭 즉시 룸)
- [x] 2.2 Wear `MainActivity` HorizontalPager 2페이지 (gameData 있을 때만)
- [x] 2.3 watchOS `WatchVentingSelectionView` + `WatchContentView` TabView(.page)

## 3. Verification

- [x] 3.1 4개 타깃 빌드 통과 (:watch/:mobile assembleDebug, iOS/watchOS 스킴)
- [ ] 3.2 실기기(Galaxy Watch): 라이브 중 스와이프 → 후보 축적 확인 → 탭 → 룸 진입
- [ ] 3.3 실기기: 패배 확정 후 심각도 정렬 확인
- [ ] 3.4 실기기(Apple Watch): 동일 플로우
