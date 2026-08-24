# Tasks — split-install-vs-update-onboarding

## 1. 노출 정책 분리

- [x] 1.1 iOS `BaseHapticApp.swift`: `evaluateWhatsNewTrigger` 재작성 — 신규 설치는
      가이드만, 업데이트·기존 사용자는 모달만(+가이드 키 미노출 처리),
      `queueFeatureGuideIfNeeded`를 1회성 게이트로 변경, What's New onConfirm 체인 제거
- [x] 1.2 Android `MainActivity.kt`: 트리거 LaunchedEffect 동일 재작성,
      `showFeatureGuideIfNeeded` 1회성 게이트, WhatsNewDialog onConfirm 체인 제거
      (알림 설정 프롬프트 직행)
- [x] 1.3 DEBUG 전용: 같은 버전 재실행 시(가이드 재개 없으면) 업데이트 안내 모달
      매 실행 노출 — 현재 버전 노트 없으면 최신 노트 폴백 (iOS/Android)

## 2. 분풀이 피처 가이드 스텝

- [x] 2.1 iOS `HomeScreen.swift`: `venting` 스텝 + `activeSteps`(피처 게이트 조건부) +
      오버레이 인덱스/총계 목록 기준 + 쇼케이스 샘플 카드 + venting scroll target
- [x] 2.2 Android `HomeScreen.kt`: `VENTING` 스텝 + `updateHighlightSteps` +
      오버레이 steps 파라미터 + 쇼케이스 샘플 카드(아이템 4) + 스크롤 보정(경기 카드 5)

## 3. 빌드 검증

- [x] 3.1 iOS 시뮬레이터 빌드 (BaseHaptic scheme, Debug)
- [x] 3.2 Android `:app:compileDebugKotlin`

## 4. 검증 (잔존)

- [ ] 4.1 신규 설치(앱 데이터 삭제): 온보딩 → 홈 피처 가이드 6스텝(분풀이 마지막) 노출,
      What's New 미노출 확인
- [ ] 4.2 업데이트 시나리오(`last_seen_update_version`을 이전 버전으로 세팅):
      What's New 모달만 노출, 가이드 미노출 확인
- [ ] 4.3 가이드 분풀이 스텝에서 샘플 카드 하이라이트·스크롤 위치 확인 (iOS/Android)
- [ ] 4.4 가이드 종료 후 샘플 카드가 사라지고 실데이터 홈카드 조건 노출로 복귀 확인
