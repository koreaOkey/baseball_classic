## Tasks
- [x] `ios/mobile/BaseHaptic/Screens/ThemeStoreScreen.swift` 작성 (Android 포팅, 디자인 토큰 사용)
- [x] `BaseHapticApp.swift` switch에 `.store` 케이스 추가 + 구매 핸들러 연결
- [x] 하단 탭바에 "상점" 버튼 추가 (`bag.fill`, 홈/설정 사이)
- [x] `xcodegen generate`로 pbxproj 재생성
- [x] `xcodebuild` 빌드 성공 확인 (iPhone 17 simulator)
- [ ] 실기/시뮬레이터에서 구매 → 적용 → 기본 복귀 플로우 수동 검증
- [ ] 구매 영속화(Supabase `user_theme_purchases` 연동)는 별도 change로 분리
