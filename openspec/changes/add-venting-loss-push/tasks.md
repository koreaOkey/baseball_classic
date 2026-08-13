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
- [ ] 4.3 **활성화 게이트**: `venting_loss_push_enabled=true`는 카피 마케터 검토 + venting 릴리즈 노출 준비(DEBUG 해제+법률) 후. 그 전까지 다크 유지
