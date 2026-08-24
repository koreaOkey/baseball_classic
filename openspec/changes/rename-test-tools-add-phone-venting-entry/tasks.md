# Tasks — rename-test-tools-add-phone-venting-entry

## 1. 명칭 변경
- [x] 1.1 iOS·Android 화면 제목 "워치 테스트" → "기능 테스트"
- [x] 1.2 iOS·Android 섹션 "워치 빠따존 테스트" → "빠따존 분풀이 테스트" + 설명 일반화

## 2. 휴대폰 빠따존 진입 버튼
- [x] 2.1 iOS `phoneVentingButton` + `phoneVentingMockContext` (#if DEBUG, fullScreenCover로 VentingFlowCoordinator 표시, entrySource "test_tool")
- [x] 2.2 Android 버튼 + `debugPhoneVentingContext` (BuildConfig.DEBUG, VentingFlowController.open 재사용)

## 3. 검증
- [x] 3.1 Android `:app:compileDebugKotlin` 통과
- [x] 3.2 iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과
- [ ] 3.3 실기기(DEBUG): 휴대폰 빠따존 열기 → 대상 선택 → 룸 → 완파 플로우 + "테스트" 뒤로가기 라벨 확인, 릴리즈 빌드 미노출 확인
