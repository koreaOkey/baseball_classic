## 1. 백엔드 (컬럼 추가 → 코드 순)

- [x] 1.1 `models.py` `TeamSubscriptionToken.app_version`(nullable String32) + `db/migrations/20260814_001` ALTER SQL
- [x] 1.2 `schemas.py` `TeamSubscriptionRequest.app_version`(옵션) + `register_team_subscription` 저장(PG upsert values/set_ + sqlite 양쪽)
- [x] 1.3 `config.py` `venting_loss_push_min_version`(기본 빈값=게이트 없음)
- [x] 1.4 `_load_team_subscriptions_with_version` + `_parse_version`/`_version_gte`(숫자 비교), `_send_loss_notification`에 min_version 필터
- [x] 1.5 테스트: 버전 게이트 필터/헬퍼(8.10>8.9 숫자비교/NULL 제외/빈값 게이트없음), 전체 111 통과
- [ ] 1.6 **프로덕션 ALTER 적용(승인 필요)** → 그 다음 코드 배포(순서 중요). 이상 시 컬럼은 nullable이라 무해

## 2. 클라 (양 플랫폼)

- [x] 2.1 Android `TeamSubscriptionRegistrar`: 바디에 `app_version=BuildConfig.VERSION_NAME` + 스킵 캐시에 버전 포함 + 성공 시 저장. compileDebugKotlin 통과
- [x] 2.2 iOS `PushTokenManager`: 바디에 `app_version=CFBundleShortVersionString` + 스킵 비교에 버전 포함 + 저장/해제 정리. BaseHaptic Debug 빌드 통과

## 3. 활성화

- [x] 3.1 ALTER 적용(Supabase SQL Editor) 후 코드 배포(커밋 3e2ca4f5), 프로덕션 register app_version 동작 검증
- [~] 3.2 실테스트 활성(2026-08-14): `venting_loss_push_min_version=1.1.7` + `venting_loss_push_enabled=true` 설정, health 200. app_version 전송 코드가 미출시라 **실사용자 전원 NULL=제외**, 재빌드 테스터(1.1.7 전송)만 대상. 잔여: 실기기에서 재빌드→응원팀(지는 팀) 선택→경기 종료 시 푸시→딥링크→분풀이 확인
- [ ] 3.3 출시: venting 릴리즈(DEBUG 게이트 해제) + 그 버전으로 min_version 갱신 → 업데이트 사용자에게 확대
