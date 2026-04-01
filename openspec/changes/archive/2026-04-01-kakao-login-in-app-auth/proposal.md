## Why
Apple 앱 심사 Guideline 4 - Design 리젝: 카카오 로그인 시 기본 Safari 브라우저로 이동되어 사용자 경험이 나쁨.
앱 내에서 로그인을 처리하도록 수정 필요.

## What Changes
- 카카오 OAuth 로그인을 `UIApplication.shared.open()` (외부 Safari) 에서 `ASWebAuthenticationSession` (앱 내 인증 시트)으로 변경
- `WebAuthContextProvider` 추가하여 인증 세션의 presentation anchor 제공
- 콜백 URL을 `ASWebAuthenticationSession`이 직접 반환하므로 인증 플로우가 단일 함수 내에서 완결

## Capabilities
### Modified Capabilities
- `mobile-app`: 카카오 로그인이 앱 내 Safari View Controller에서 처리됨
- `mobile-app`: 로그인 콜백이 ASWebAuthenticationSession 내부에서 자동 처리

### New Capabilities
- `mobile-app`: WebAuthContextProvider — ASWebAuthenticationSession용 presentation context 제공

## Impact
- ios/mobile/BaseHaptic/Data/AuthManager.swift — signInWithKakao() 메서드 변경, WebAuthContextProvider 클래스 추가
