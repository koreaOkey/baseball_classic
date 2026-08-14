# gate-venting-loss-push-by-app-version

## Why

패배 유도 푸시는 서버가 발송하고 OS가 배너를 그리므로 **앱 버전과 무관하게** 모든 팀
구독자에게 표시된다. venting을 지원하지 않는 구버전(또는 미출시라 기능이 없는 릴리즈)
사용자에게 배너가 뜨면 탭해도 진입이 안 돼 혼란스럽다. 발송 대상을 **venting 지원 버전
이상**으로 제한해야 하고, 동시에 이 임계값을 활용해 **출시 전 실테스트 때는 테스트 빌드
버전에만** 발송해 실사용자를 건드리지 않고 검증할 수 있다.

## What Changes

- **백엔드**: `TeamSubscriptionToken.app_version` 컬럼 추가(nullable, additive ALTER) +
  `POST /team-subscriptions` 가 `app_version` 수신·저장. `venting_loss_push_min_version`
  설정(기본 빈값=게이트 없음). `_send_loss_notification`이 min_version 지정 시 그 버전
  이상 구독자에게만 발송(구버전·미상 제외). 시맨틱 버전 숫자 비교(`_version_gte`).
- **클라(양 플랫폼)**: 팀 구독 등록 시 `app_version`(VERSION_NAME / CFBundleShortVersionString)
  전송. **재등록 스킵 캐시에 버전 포함** → 앱 업데이트 시 재등록되어 새 버전이 기록됨.

## Non-Goals

- venting 자체의 릴리즈(클라 DEBUG 게이트 해제)·활성화 결정. 이 change는 게이트 메커니즘만.
- 정확한 min_version 값 설정(운영 env 레버, 실테스트/출시 시점에 지정).

## Decisions

- **표준 대상 = TeamSubscriptionToken**(경기-수명-독립). 게임 스코프 DeviceToken 아님.
- **컬럼 추가 순서**: nullable ALTER 를 **프로덕션에 먼저 적용**한 뒤 app_version 을 쓰는 코드를
  배포한다(컬럼 없는 상태로 새 register 코드가 뜨면 구독 등록이 깨짐). nullable 이라 PG에서
  메타데이터 변경(무중단).
- **재등록 캐시에 버전 포함**: 안 하면 업데이트해도 token/team 동일해 스킵 → 새 버전 미기록 →
  게이트가 구버전으로 오판. 그래서 버전 변경도 재등록 트리거로 삼는다.
- **실테스트 = 미출시 버전 임계값**: 테스트 빌드 버전이 아직 스토어에 없으면 그 버전 이상은
  테스터 본인뿐 → 실사용자 무영향. 출시하면 그 버전이 공개되며 자연 확대.
