## 1. Supabase Postgrest 연동

- [x] 1.1 build.gradle.kts에 postgrest-kt 의존성 추가
- [x] 1.2 SupabaseClient에 Postgrest 설치
- [x] 1.3 ThemeRepository 생성 (fetchUnlockedThemeIds, fetchUserSettings, saveUnlock, saveActiveTheme, saveSelectedTeam)

## 2. 테마 데이터 모델 재설계

- [x] 2.1 ThemeData에 ThemeCategory(FREE/AD_REWARD/PREMIUM), previewImage 필드 추가
- [x] 2.2 iOS 동일 12개 스토어 테마 정의 (Free 1 + Ad-Reward 11, Premium 주석)
- [x] 2.3 테마 프리뷰 스크린샷 이미지 12개 drawable에 추가

## 3. 리워드 광고 연동

- [x] 3.1 RewardedAdManager 생성 (AdMob 리워드 광고 로드/표시/콜백)
- [x] 3.2 프로덕션/테스트 광고 단위 ID 설정

## 4. ThemeStoreScreen 재설계

- [x] 4.1 Watch/Phone 탭 구성 (Phone은 "준비 중")
- [x] 4.2 원형 워치 스크린샷 프리뷰로 테마 카드 변경
- [x] 4.3 상태별 버튼 통일 (광고 보고 받기 / 적용하기 / 적용 중) — 동일 높이 유지
- [x] 4.4 잠금/체크 배지 표시

## 5. MainActivity 통합

- [x] 5.1 SHOW_STORE_TAB 활성화
- [x] 5.2 unlockedThemeIds / activeTheme 로컬 영속화 (SharedPreferences)
- [x] 5.3 로그인 시 Supabase에서 테마/설정 자동 복원
- [x] 5.4 테마 적용/잠금 해제/팀 변경 시 Supabase 비동기 저장
- [x] 5.5 Watch 테마 ↔ Phone 테마 분리 (HomeScreen, LiveGameScreen, CommunityScreen에서 activeTheme 제거)

## 6. 워치 스토어 테마 동기화

- [x] 6.1 WearThemeSyncManager에 storeThemeId 전송 추가
- [x] 6.2 워치 DataLayerListenerService에서 store_theme_id 수신/저장
- [x] 6.3 워치 TeamTheme에 12개 스토어 테마 + getThemeForStoreId() 추가
- [x] 6.4 워치 Theme.kt에 storeThemeOverride 파라미터 추가
- [x] 6.5 워치 MainActivity에서 스토어 테마 상태 관리 + 브로드캐스트 수신
- [x] 6.6 워치 LiveGameScreen 배경 이미지/그라데이션 지원 (배경 이미지 → 45% 오버레이, 스토어 → 그라데이션, 기본 → Gray950)
- [x] 6.7 워치 배경 이미지 리소스 추가 (theme_baseball_love.png)

## 7. UI 세부 조정

- [x] 7.1 선셋 오렌지/파이어 레드 이닝 텍스트 흰색 처리
- [x] 7.2 홈 플레이트 색상 Gray800 통일 (Android 워치)
- [x] 7.3 홈 플레이트 색상 Gray800 통일 (iOS 워치)
- [x] 7.4 미드나이트 인디고/딥 네이비 그라데이션 밝기 조정
- [x] 7.5 SettingsScreen에서 미사용 파라미터 제거 (purchasedThemes, activeTheme, onSelectTheme)
