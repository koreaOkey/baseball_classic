## 1. 외부 서비스 설정
- [x] 1.1 Supabase Dashboard: Kakao OAuth provider 활성화
- [x] 1.2 Supabase Dashboard: Apple OAuth provider 활성화
- [x] 1.3 Supabase Dashboard: Redirect URL 등록 (app + mobile 스킴)
- [x] 1.4 Supabase Dashboard: Site URL을 com.basehaptic.app://login-callback 으로 설정
- [x] 1.5 Kakao Developers: watchbaseball 앱 카카오 로그인 활성화
- [x] 1.6 Kakao Developers: Redirect URI → Supabase callback URL 등록
- [x] 1.7 Kakao Developers: 동의항목 설정 (nickname, image, email)
- [x] 1.8 Apple Developer: Sign In with Apple capability 추가 (com.basehaptic.app)
- [x] 1.9 Apple Developer: Auth Key 생성 (K8842LV4JK)
- [x] 1.10 Supabase Dashboard: Apple provider에 Client Secret JWT 등록 (ES256, 6개월 유효, 2026-09-24 만료)

## 2. Android 인프라
- [x] 2.1 build.gradle.kts (root): serialization 플러그인 추가
- [x] 2.2 build.gradle.kts (app): Supabase BOM + auth-kt + ktor 의존성 추가
- [x] 2.3 build.gradle.kts (app): supabaseUrl/supabaseAnonKey BuildConfig 필드
- [x] 2.4 local.properties: Supabase URL + Anon Key 등록
- [x] 2.5 SupabaseClient.kt 생성
- [x] 2.6 AuthManager.kt 생성
- [x] 2.7 OAuthCallbackActivity.kt + AndroidManifest.xml 딥링크 등록

## 3. Android UI
- [x] 3.1 SettingsScreen.kt: authState 파라미터 추가, 카카오 로그인 버튼 + 로그아웃 UI
- [x] 3.2 MainActivity.kt: AuthManager 초기화, authState → SettingsScreen 전달

## 4. iOS 인프라
- [x] 4.1 Xcode: supabase-swift SPM 패키지 추가 (v2.43.0)
- [x] 4.2 pbxproj: Frameworks 빌드 페이즈 + Supabase 타겟 링크
- [x] 4.3 Info.plist: CFBundleURLTypes (OAuth scheme) + SUPABASE_URL + SUPABASE_ANON_KEY
- [x] 4.4 SupabaseClient.swift 생성
- [x] 4.5 AuthManager.swift 생성 (카카오 OAuth + Apple Sign In)

## 5. iOS UI
- [x] 5.1 SettingsScreen.swift: 카카오 + Apple 로그인 버튼, 로그인 상태/로그아웃 UI
- [x] 5.2 BaseHapticApp.swift: AuthManager 연결, .onOpenURL, ContentView에 전달

## 6. 확인된 이슈
- [x] 6.1 OAuth 완료 후 localhost로 리다이렉트되는 문제 → Supabase Site URL 설정으로 해결 필요
- [x] 6.2 비즈앱 미등록 시 account_email 동의항목 사용 불가 → 비즈앱 전환 또는 email 제외 필요

## 7. 향후 과제
- [x] 7.1 비즈앱 등록 후 이메일 필수 동의 활성화
- [x] 7.2 테마 상점 구매 시 auth.users.id 연동
- [x] 7.3 Android 실기기 카카오 로그인 테스트
- [x] 7.4 Apple Sign In 실기기 테스트
- [x] 7.5 세션 자동 복원 테스트 (앱 종료 후 재시작)
