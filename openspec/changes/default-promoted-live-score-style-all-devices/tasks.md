# Tasks

## 1. Notification manager

- [x] 1.1 `isPromotedStyleEnabled()` 기본값 제조사 조건부 → 무조건 `true`로 변경
- [x] 1.2 삼성 기본 OFF 근거 주석을 런타임 폴백 위임 설명으로 갱신
- [x] 1.3 `canPromote` 분기 삼성 관련 주석 "기본 OFF" → "기본 ON"으로 갱신

## 2. Verification

- [x] 2.1 `:app:compileDebugKotlin` 빌드 통과
- [ ] 2.2 삼성 실기기(API 36+, 미조작): 라이브 스코어 갱신 시 promoted 경로 진입 확인(승격 미지원이면 리치 카드 폴백)
- [ ] 2.3 비삼성 실기기(API 36+): 기존과 동일하게 promoted 기본 노출 확인
- [ ] 2.4 설정 토글 OFF 저장 후 재실행 시 저장값 유지(새 기본값에 덮이지 않음) 확인
