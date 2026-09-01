## 1. 백엔드 (다크, 무영향)

- [x] 1.1 `config.py` 에 `venting_loss_push_enabled: bool = False` 추가(venting 플래그군)
- [x] 1.2 `main.py` 에 `_send_loss_notification(game_id, home_team, away_team, home_score, away_score)` 추가 — `_send_game_start_notification` 패턴, 대상은 **패배팀 코드만**, 무승부 스킵, `kind=venting_loss`, iOS `category="OPEN_VENTING"`, out-of-band 발송 + 영구 실패 토큰 정리
- [x] 1.3 FINISHED 훅 옆에 `venting_loss_push_enabled` 게이트로 `background_tasks.add_task(_send_loss_notification, ...)` 스케줄(state_payload 팀/스코어)
- [~] 1.4 배포(플래그 OFF)·다크 무영향 확인 — 테스트 108 통과(신규 loss_push 2건), 커밋 후 프로덕션 배포 확인 대기

## 2. iOS 딥링크 라우팅 (DEBUG)

- [x] 2.1 `AppDelegate.swift` `registerNotificationCategories()` 에 `OPEN_VENTING` 카테고리 등록
- [x] 2.2 `didReceive`에서 `userInfo["kind"]=="venting_loss"` 분기(#if DEBUG) → `.openVentingRequested` post
- [x] 2.3 `BaseHapticApp` `.onReceive(.openVentingRequested)`(#if DEBUG + 피처 플래그) → 서버 regret-top5(폴백 로컬)로 컨텍스트 빌드 → `fullScreenCover`로 `VentingFlowCoordinator`(entrySource="loss_push"). 시뮬 빌드 통과

## 3. Android 딥링크 라우팅 (DEBUG)

- [x] 3.1 `BaseHapticMessagingService` 에서 `data["kind"]` 판독 + `EXTRA_VENTING` intent extra
- [x] 3.2 `NotificationIntentBus.PendingIntent` 에 `venting` 필드 추가, `MainActivity.handleNotificationIntent`에서 세팅
- [x] 3.3 `MainActivity` 소비 `LaunchedEffect`에서 venting 분기 → 서버 regret-top5(폴백 로컬)로 컨텍스트 빌드 → `VentingFlowController.open(entrySource="loss_push")`, DEBUG 게이트 준수. compileDebugKotlin 통과

## 4. 검증·게이트

- [ ] 4.1 백엔드 단위 테스트: 패배팀만 대상/무승부 스킵/플래그 OFF 스킵/커넥션 비점유
- [ ] 4.2 DEBUG 빌드에서 푸시 탭 → 분풀이 진입 실기기/시뮬 확인(양 플랫폼)
- [~] 4.3 **활성화 게이트**: 실명 법률 확인 완료(2026-08-14). 남은 것 — 푸시 카피 마케터 검토 + 버전 게이트(gate-venting-loss-push-by-app-version) + venting 릴리즈(DEBUG 해제). 그 전까지 다크 유지

## 5. 릴리즈 오픈 후속 (2026-08-25)

- [x] 5.1 iOS `AppDelegate.didReceive` 의 venting_loss 분기 `#if DEBUG` 제거 — open-venting-in-release(cedc9162)가 소비단만 열고 생산단을 누락해 릴리즈에서 loss-push 탭이 홈으로 떨어지던 버그 수정. 관련 낡은 주석 3곳(카테고리 등록·분기·Notification.Name)도 현행화. Android는 원래 게이트 없음(변경 불필요)
- [ ] 5.2 실기기 RELEASE 빌드에서 loss-push 탭 → 빠따존 진입 확인(iOS)
