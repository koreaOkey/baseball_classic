# Tasks — rename-venting-to-bbattazone

## 1. iOS 폰
- [x] 1.1 SettingsScreen 토글 제목 "빠따존"
- [x] 1.2 HomeScreen 피처 가이드 스텝 제목 "빠따존"
- [x] 1.3 VentingLiveEntry 팝업 "빠따존에 입장하시겠습니까?" + 버튼 "빠따존 가기"
- [x] 1.4 VentingHomeCard "빠따존 한번 가볼까요?" + CTA "빠따존 가기"
- [x] 1.5 VentingRoomScreen 배지 "💢 빠따존"
- [x] 1.6 VentingTargetSelectionScreen 제목 "💢 빠따존" + "빠따존 입장하기" + "워치로 빠따존 열기"
- [x] 1.7 VentingFlowCoordinator 핸드오프 "워치에서 빠따존에 입장하세요" + "빠따존이 열렸어요"
- [x] 1.8 ReleaseNotes What's New 제목 "💢 빠따존이 생겼어요"
- [x] 1.9 WatchTestScreen 테스트 도구 문구

## 2. Android 폰
- [x] 2.1 SettingsScreen·HomeScreen·VentingLiveEntry·VentingHomeCard (1.1~1.4 동일)
- [x] 2.2 VentingRoomScreen·VentingTargetSelectionScreen·VentingWatchHandoffScreen (1.5~1.7 동일)
- [x] 2.3 ReleaseNotes·WatchTestScreen (1.8~1.9 동일)

## 3. 워치·백엔드
- [x] 3.1 watchOS WatchVentingScreen "💢 빠따존"
- [x] 3.2 Wear OS WatchVentingSelectionScreen "💢 빠따존"
- [x] 3.3 백엔드 패배 유도 푸시 body "빠따존에서 오늘 스트레스 풀고 가세요."

## 4. 문서
- [x] 4.1 split-install-vs-update-onboarding 가이드 스텝 카피 인용 갱신
- [x] 4.2 use-real-game-for-venting-home-card 홈카드 카피 인용 갱신

## 5. 검증
- [x] 5.1 문자열 grep: 사용자 노출 잔존 "분풀이"가 의도한 2종("분풀이 완료!", "직접 지목한 분풀이 대상")뿐인지 확인
- [x] 5.2 backend py_compile 통과
- [x] 5.3 Android :app:compileDebugKotlin (mobile·watch) 통과
- [x] 5.4 iOS BaseHaptic 스킴 시뮬레이터 빌드 통과
- [ ] 5.5 실기기: 홈카드·대상선택·룸·워치 페이지·What's New에서 새 명칭 확인
