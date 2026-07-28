# Tasks

## 1. Notification manager

- [x] 1.1 `KEY_PROMOTED_STYLE_ENABLED` pref + `isPromotedStyleEnabled()` 추가 (기본 true)
- [x] 1.2 `isPromotedStyleSupportedOnDevice()` 추가 (API 36+ && 비삼성, 설정 노출 판단용)
- [x] 1.3 `post()`의 `canPromote`에 사용자 설정 조건 반영

## 2. Settings UI

- [x] 2.1 "잠금화면 라이브 스코어" 섹션 + "새 잠금화면 스코어" 스위치 추가
- [x] 2.2 promoted 미지원 기기에서 섹션 숨김

## 3. Verification

- [x] 3.1 `:app:compileDebugKotlin` 빌드 통과
- [ ] 3.2 실기기(API 36+): 토글 OFF → 다음 갱신에서 이전 리치 카드로 전환 확인
- [ ] 3.3 실기기(API 36+): 토글 ON 복귀 → promoted 스타일 복원 확인
- [ ] 3.4 API 35 이하 기기에서 설정 섹션 미노출 확인
