## Why

Android 앱의 테마 상점이 목업 데이터(가짜 포인트 시스템)로만 구성되어 iOS와 기능 격차가 컸다. 리워드 광고, Supabase 동기화, 실제 테마 카테고리를 구현하여 iOS와 동일한 수익화 경로와 사용자 경험을 제공한다.

## What Changes

- 리워드 광고(AdMob)로 테마 잠금 해제 기능 추가 (기존 목업 포인트 시스템 제거)
- Supabase Postgrest 연동: 테마 구매 이력, 활성 테마, 응원팀을 서버에 동기화
- iOS와 동일한 12개 스토어 테마 적용 (Free 1 + Ad-Reward 11, Premium은 주석 처리)
- ThemeStoreScreen 재설계: Watch/Phone 탭, 원형 워치 스크린샷 프리뷰
- Watch 테마와 Phone 테마 분리 (워치 전용 스토어 테마, 폰 UI는 팀 색상 유지)
- 워치 스토어 테마 동기화: Data Layer로 store_theme_id 전송 → 워치 내장 테마 매칭
- 워치 배경 이미지 지원 (야구가 좋아 테마 등)
- 선셋 오렌지/파이어 레드 이닝 텍스트 가독성 개선 (흰색)
- 홈 플레이트 색상 Gray800 통일 (Android/iOS 워치 양쪽)

## Capabilities

### New Capabilities
- `android-rewarded-ads`: AdMob 리워드 광고로 테마 잠금 해제
- `android-theme-supabase-sync`: Supabase Postgrest를 통한 테마/설정 서버 동기화
- `android-theme-store-redesign`: iOS 동일 테마 카테고리, 원형 프리뷰, Watch/Phone 탭
- `watch-store-theme-sync`: 모바일→워치 스토어 테마 동기화 (Data Layer + 내장 테마 + 배경 이미지)

### Modified Capabilities
_(없음)_

## Impact

- **Android Mobile**: ThemeData, ThemeStoreScreen, MainActivity, SettingsScreen, HomeScreen, LiveGameScreen, CommunityScreen, SupabaseClient, WearThemeSyncManager
- **Android Wear OS**: DataLayerListenerService, MainActivity, TeamTheme, Theme, LiveGameScreen
- **iOS Watch**: WatchLiveGameScreen (홈플레이트 색상 통일)
- **Dependencies**: `postgrest-kt` 추가 (Supabase BOM)
- **Assets**: 테마 프리뷰 스크린샷 12개, 워치 배경 이미지 1개
