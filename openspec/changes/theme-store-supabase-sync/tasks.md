## 1. DB 스키마 및 데이터

- [x] 1.1 themes 테이블에 platform 컬럼 추가 (watch/phone/both)
- [x] 1.2 레거시 팀별 테마 10개 삭제 (theme_doosan_base_v1 등)
- [x] 1.3 베이직 테마 12개 seed (default, baseball_love, midnight_indigo 등)

## 2. 야구가 좋아 테마 추가

- [x] 2.1 워치/모바일 Assets.xcassets에 theme_baseball_love imageset 추가
- [x] 2.2 ThemeData.allThemes에 baseball_love 등록 (adReward, 기본형 바로 다음)
- [x] 2.3 WatchTeamTheme에 baseballLove 정의 및 theme(forStoreId:) 매핑
- [x] 2.4 WatchLiveGameScreen에 야구가 좋아 프리뷰 추가

## 3. 테마 상점 UI 정리

- [x] 3.1 기본 + 광고 시청 섹션을 "베이직 테마"로 통합
- [x] 3.2 프리미엄 섹션 숨김 처리
- [x] 3.3 MiniWatchPreview 팀명 "팀 1"/"팀 2"로 변경
- [x] 3.4 워치 프리뷰 팀명 "팀 1"/"팀 2"로 통일

## 4. 테마 적용 범위 제한

- [x] 4.1 HomeScreen, LiveGameScreen에 activeTheme: nil 전달 (폰 앱 색상 미변경)

## 5. Supabase 테마 동기화

- [x] 5.1 ThemeRepository.swift 생성 (fetchUnlockedThemeIds, fetchActiveThemeId, saveUnlock, saveActiveTheme)
- [x] 5.2 onUnlockTheme에서 ThemeRepository.saveUnlock + saveActiveTheme 호출
- [x] 5.3 onApplyTheme에서 ThemeRepository.saveActiveTheme 호출
- [x] 5.4 onPurchaseTheme에서 ThemeRepository.saveUnlock + saveActiveTheme 호출
- [x] 5.5 로그인 시 restoreThemesFromServer() 호출 (onChange authState)
- [x] 5.6 RLS 정책 확인 (기존 정책으로 충분)
- [x] 5.7 Xcode 프로젝트에 ThemeRepository.swift 등록

## 6. 응원팀 Supabase 동기화

- [x] 6.1 user_theme_settings → user_settings 테이블 리네임
- [x] 6.2 user_settings에 selected_team 컬럼 추가
- [x] 6.3 ThemeRepository에 saveSelectedTeam, fetchUserSettings 추가
- [x] 6.4 팀 변경/온보딩 시 서버에 응원팀 저장
- [x] 6.5 로그인 시 서버에서 응원팀 복원
- [x] 6.6 서버에 데이터 없으면 로컬→서버 업로드 (기존 유저 대응)
