# Tasks — add-update-onboarding-slides

## 1. 이미지 에셋
- [x] 1.1 분풀이 룸 스크린샷 crop (뱃지~인형 구간, 1061×944 JPEG)
- [x] 1.2 잠금 화면 노티 카드 스크린샷 (1296×550 JPEG)
- [x] 1.3 iOS `WhatsNewVenting`/`WhatsNewLockScreen` imageset + Android `drawable-nodpi` 배치

## 2. 모델
- [x] 2.1 iOS `ReleaseNotes.swift` — `WhatsNewFeaturePage`(visual: image|lockScreen) + `ReleaseNote.featurePages`
- [x] 2.2 Android `ReleaseNotes.kt` — `WhatsNewVisual` sealed + `WhatsNewFeaturePage` + `featurePages`
- [x] 2.3 1.1.8 엔트리 (분풀이·잠금화면 2페이지 + 불릿 3개, 확정 카피 반영)

## 3. 슬라이드 레이아웃
- [x] 3.1 iOS `WhatsNewSheet` — TabView(.page) + 고정 높이 + 닷 + 다음/확인 버튼, featurePages 없으면 기존 레이아웃
- [x] 3.2 Android `WhatsNewDialog` — HorizontalPager 동등 구현
- [x] 3.3 잠금 화면 프레임(날짜 동적 + 9:41 시계 + 노티 카드 이미지) 양 플랫폼
- [x] 3.4 액센트를 `controlAccent`로 통일 (다크 팀컬러 가시성)

## 4. 검증
- [x] 4.1 Android `:mobile:compileDebugKotlin` 통과
- [x] 4.2 iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과
- [ ] 4.3 실기기: 버전 범프 후 첫 실행에서 3페이지 슬라이드·닷·버튼·배경 탭 닫기 확인
- [ ] 4.4 설정 "업데이트 안내"에서 슬라이드형 재열람 확인
- [ ] 4.5 릴리즈 전: Android용 잠금 화면 실기기 스크린샷으로 교체 검토
