# Tasks

## 1. Notification manager

- [x] 1.1 `KEY_PROMOTED_STYLE_ENABLED` pref + `isPromotedStyleEnabled()` 추가 (기본: 비삼성 ON / 삼성 OFF)
- [x] 1.2 `isPromotedStyleSupportedOnDevice()` 추가 (API 36+, 설정 노출 판단용)
- [x] 1.3 `post()`의 `canPromote`에 사용자 설정 조건 반영, 삼성 하드 블록 → 기본값 OFF로 완화
- [x] 1.4 `post(forceStyle)` 테스트 전용 강제 스타일 파라미터 추가

## 2. Settings UI

- [x] 2.1 "잠금화면 라이브 스코어" 섹션 + "잠금화면 고정 스코어 (promoted)" 스위치 추가
- [x] 2.2 API 36 미만 기기에서 섹션 숨김

## 2b. Test tools

- [x] 2b.1 WatchTestScreen 미리보기 카드에 "Promoted 버전"/"Ongoing 버전" 강제 버튼 추가

## 3. Verification

- [x] 3.1 `:app:compileDebugKotlin` 빌드 통과
- [ ] 3.2 실기기(API 36+): 토글 OFF → 다음 갱신에서 이전 리치 카드로 전환 확인
- [ ] 3.3 실기기(API 36+): 토글 ON 복귀 → promoted 스타일 복원 확인
- [ ] 3.4 API 35 이하 기기에서 설정 섹션 미노출 확인
