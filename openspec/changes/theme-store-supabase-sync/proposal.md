## Why

테마 잠금해제/적용 이력이 기기 로컬(UserDefaults)에만 저장되어, 다른 기기에서 로그인하면 테마가 초기화되는 문제. 계정 기반으로 Supabase에 동기화하여 기기 간 테마 상태를 유지한다. 동시에 새 테마 "야구가 좋아"를 추가하고 테마 상점 구조를 정리한다.

## What Changes

- "야구가 좋아" 워치 배경 테마 추가 (베이직/광고 시청 카테고리)
- 테마 상점 섹션 통합: "기본" + "무료 (광고 시청)" → "베이직 테마" 단일 섹션
- 프리미엄 섹션 숨김 처리
- 테마 적용 시 폰 앱 색상 미변경 (워치만 반영, 폰 앱 테마는 추후 별도 설정)
- ThemeRepository 신규: 잠금해제/적용 테마를 Supabase user_theme_purchases/user_theme_settings에 저장
- 로그인 시 서버에서 테마 구매 이력 + 적용 테마 fetch → 앱 상태 복원
- Supabase themes 테이블에 platform 컬럼 추가 (watch/phone/both)
- 레거시 팀별 테마 10개 삭제, 베이직 테마 12개 seed
- 워치 프리뷰 팀명 "팀 1"/"팀 2"로 통일

## Capabilities

### New Capabilities

- `theme-sync`: 테마 잠금해제/적용 이력을 Supabase에 저장하고 로그인 시 복원하는 계정 기반 동기화

### Modified Capabilities

- `themes`: 테마 상점 섹션 구조 변경 (베이직/프리미엄 분리), 새 테마 추가, platform 구분

## Impact

- iOS 앱: ThemeRepository.swift 신규, BaseHapticApp.swift 연동, ThemeData/ThemeStoreScreen/WatchTeamTheme/WatchLiveGameScreen 수정
- Supabase DB: themes 테이블 platform 컬럼 추가, 레거시 데이터 정리, 베이직 테마 seed
- RLS: 기존 정책으로 충분 (user_theme_purchases, user_theme_settings 모두 본인 데이터만 접근)
