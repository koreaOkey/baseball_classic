-- 패배 분풀이 푸시 버전 게이트: team_subscription_tokens 에 app_version 추가.
-- nullable + additive 라 Postgres 에서 메타데이터 변경(테이블 재작성/장시간 락 없음)으로 무중단.
-- 구버전/미전송 구독은 NULL → 버전 게이트(min_version) 설정 시 자연 제외.
-- 적용 순서: 이 ALTER 를 먼저 프로덕션에 적용한 뒤 app_version 을 쓰는 코드를 배포한다
-- (컬럼 없는 상태에서 새 register 코드가 뜨면 구독 등록이 깨지므로).

alter table if exists team_subscription_tokens
    add column if not exists app_version varchar(32);
