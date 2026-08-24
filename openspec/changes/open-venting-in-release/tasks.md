# Tasks — open-venting-in-release

## 1. iOS 컴파일 게이트 제거
- [x] 1.1 VentingMode 전 파일(24개) 파일 단위 `#if DEBUG`/`#endif` 제거
- [x] 1.2 `VentingDebugNavigator`·`VentingRoomDebugView`만 `#if DEBUG` 재래핑
- [x] 1.3 BaseHapticApp loss_push 딥링크 경로 un-gate (`ventingDeepLinkRequest` + onReceive + fullScreenCover)
- [x] 1.4 HomeScreen 홈카드·가이드 스텝, LiveGameScreen 라이브 진입 오버레이 un-gate
- [x] 1.5 WatchTestScreen 휴대폰 빠따존 버튼 un-gate

## 2. 피처 플래그 기본 ON
- [x] 2.1 iOS `VentingFeatureFlag.isEnabled` 저장값 없으면 true + init의 DEBUG 강제 setEnabled(true) 제거
- [x] 2.2 Android `VentingFeatureFlag` DEBUG 조기 반환 제거 (prefs 기본 true)
- [x] 2.3 설정 DEBUG 섹션 토글 부제 갱신 (양 플랫폼) + stale 주석 4곳 현행화

## 3. 검증
- [x] 3.1 iOS Debug + Release 구성 시뮬레이터 빌드 통과
- [x] 3.2 Android `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` 통과
- [x] 3.3 백엔드 프로덕션 env 확인 (`VENTING_BACKEND_ENABLED=true`, `VENTING_LOSS_PUSH_ENABLED=true`, min 1.1.7)
- [ ] 3.4 릴리즈 빌드 실기기: 패배 경기 홈카드·라이브 💢 진입·loss_push 딥링크·워치 핸드오프 확인, 디버그 도구 미노출 확인
