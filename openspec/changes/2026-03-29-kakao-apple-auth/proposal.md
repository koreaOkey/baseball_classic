## Why
테마/컨텐츠 판매를 위해 사용자 계정이 필요. 기기 변경 시에도 구매 내역을 유지하려면 계정 기반 인증이 필수. DB에 이미 `user_theme_purchases`, `user_theme_settings` 테이블이 `auth.users(id)` FK로 준비되어 있음.

## What Changes
- Android: 카카오 로그인 (Supabase OAuth)
- iOS: 카카오 로그인 + Apple Sign In (App Store 정책상 필수)
- 로그인은 선택사항 — 설정 > 계정 섹션에서 로그인/로그아웃
- 온보딩(팀 선택)은 변경 없음

## Architecture
```
카카오/Apple 로그인
    ↓
Supabase Auth (OAuth / signInWithIdToken)
    ↓
auth.users 테이블 (사용자 생성)
    ↓
user_theme_purchases / user_theme_settings (uuid FK)
```

## External Service Configuration

### Supabase (Project: snrafqoqpmtoannnnwdq, Seoul)
- Authentication > Providers > **Kakao**: 활성화 완료
- Authentication > Providers > **Apple**: 활성화 완료
- Authentication > URL Configuration:
  - Site URL: `com.basehaptic.app://login-callback`
  - Redirect URLs: `com.basehaptic.app://login-callback`, `com.basehaptic.mobile://login-callback`
- Anon Key: `local.properties` (Android), `Info.plist` (iOS)에 등록 완료

### Kakao Developers (앱: watchbaseball)
- 카카오 로그인 활성화
- Redirect URI: `https://snrafqoqpmtoannnnwdq.supabase.co/auth/v1/callback`
- 동의항목:
  - `profile_nickname`: 이용중 동의
  - `profile_image`: 이용중 동의
  - `account_email`: 필수 동의 (비즈앱 등록 필요)

### Apple Developer (Team: YOUNGJE LEE, Team ID: 8G4LG3694Q)
- App ID `com.basehaptic.app`: Sign In with Apple capability 추가 완료
- Auth Key: `AuthKey_K8842LV4JK.p8` (Key ID: `K8842LV4JK`)
- Supabase Apple Provider에 등록한 Client Secret JWT:
  - Algorithm: ES256
  - kid: `K8842LV4JK`
  - iss (Team ID): `8G4LG3694Q`
  - sub (Services ID): `com.basehaptic.app`
  - aud: `https://appleid.apple.com`
  - exp: 2026-09-24 (발급일로부터 6개월, 만료 시 재발급 필요)

## Package Names / Bundle IDs
| Platform | ID | 용도 |
|----------|-----|------|
| Android | `com.basehaptic.mobile` | 패키지명 + OAuth redirect scheme |
| iOS | `com.basehaptic.app` | 번들 ID + OAuth redirect scheme |

## New Files

### Android (3개)
| 파일 | 용도 |
|------|------|
| `apps/mobile/app/src/main/java/com/basehaptic/mobile/auth/SupabaseClient.kt` | Supabase 클라이언트 싱글톤 (BuildConfig에서 URL/Key 로드) |
| `apps/mobile/app/src/main/java/com/basehaptic/mobile/auth/AuthManager.kt` | 인증 상태 관리, signInWithKakao(), signOut() |
| `apps/mobile/app/src/main/java/com/basehaptic/mobile/auth/OAuthCallbackActivity.kt` | OAuth 리다이렉트 딥링크 수신 Activity |

### iOS (2개)
| 파일 | 용도 |
|------|------|
| `ios/mobile/BaseHaptic/Data/SupabaseClient.swift` | Supabase 클라이언트 싱글톤 (Info.plist에서 URL/Key 로드) |
| `ios/mobile/BaseHaptic/Data/AuthManager.swift` | 인증 상태 관리, 카카오 OAuth + Apple Sign In + signOut() |

## Modified Files

### Android
| 파일 | 변경 |
|------|------|
| `apps/mobile/build.gradle.kts` | `kotlin.plugin.serialization` 플러그인 추가 |
| `apps/mobile/app/build.gradle.kts` | Supabase BOM + auth-kt + ktor-client-okhttp 의존성, supabaseUrl/supabaseAnonKey BuildConfig 필드 |
| `apps/mobile/app/src/main/AndroidManifest.xml` | OAuthCallbackActivity 딥링크 intent-filter |
| `apps/mobile/app/src/main/java/.../MainActivity.kt` | AuthManager 초기화, authState 수집, SettingsScreen에 전달 |
| `apps/mobile/app/src/main/java/.../SettingsScreen.kt` | 계정 섹션에 카카오 로그인 버튼 / 로그인 상태 / 로그아웃 |
| `apps/mobile/local.properties` | supabaseUrl, supabaseAnonKey 추가 |

### iOS
| 파일 | 변경 |
|------|------|
| `ios/mobile/BaseHaptic/Info.plist` | CFBundleURLTypes (OAuth scheme), SUPABASE_URL, SUPABASE_ANON_KEY |
| `ios/mobile/BaseHaptic/BaseHapticApp.swift` | AuthManager @StateObject, .onOpenURL 핸들러, .task로 초기화, ContentView에 전달 |
| `ios/mobile/BaseHaptic/Screens/SettingsScreen.swift` | 카카오 + Apple 로그인 버튼, 로그인 상태/로그아웃 UI |
| `ios/BaseHaptic.xcodeproj/project.pbxproj` | supabase-swift SPM 패키지 + Frameworks 빌드 페이즈 + Sign In with Apple capability |

## Dependencies Added

### Android
```
io.github.jan-tennert.supabase:bom:3.1.1
io.github.jan-tennert.supabase:auth-kt
io.ktor:ktor-client-okhttp:3.0.3
kotlin.plugin.serialization:2.0.21
```

### iOS
```
supabase-swift (SPM, v2.43.0 resolved)
AuthenticationServices (Apple native framework)
```
