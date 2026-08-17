# Tasks

## 1. Detection + deep-link helpers

- [x] 1.1 `isPromotedBlockedBySystemSetting(context)` 추가
- [x] 1.2 `openLiveUpdatesSettings(context)` 딥링크(폴백 포함) 추가
- [x] 1.3 `KEY_PROMOTED_PROMPT_DISMISSED` + `isPromotedPromptDismissed`/`markPromotedPromptDismissed` 추가

## 2. Settings banner (#1)

- [x] 2.1 "잠금화면 라이브 스코어" 스위치 item에 blocked 배너 통합
- [x] 2.2 `ON_RESUME` 재확인으로 켜고 돌아오면 배너 사라짐
- [x] 2.3 `PromotedLiveUpdatesBanner` 컴포저블 추가

## 3. Live entry prompt (#2)

- [x] 3.1 라이브 진입 `LaunchedEffect`로 blocked && 미닫힘 시 다이얼로그 노출
- [x] 3.2 "설정 열기"/"나중에" 어느 쪽이든 1회성 dismiss 기록

## 4. Verification

- [x] 4.1 `:app:compileDebugKotlin` 빌드 통과
- [ ] 4.2 실기기(appop reject 상태): 설정 배너 노출 + "켜기" → 설정 딥링크 확인
- [ ] 4.3 실기기: 실시간 업데이트 켜고 복귀 → 배너 사라짐 확인
- [ ] 4.4 실기기: 라이브 진입 프롬프트 1회 노출 + 재진입 시 미노출 확인
- [ ] 4.5 appop allow 상태에서는 배너·프롬프트 모두 미노출 확인
