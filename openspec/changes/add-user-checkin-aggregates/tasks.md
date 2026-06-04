## 1. 모델·스키마

- [ ] 1.1 `app/models.py`에 `UserCheckinDaily` 모델 추가 (PK `(user_id, date)`)
- [ ] 1.2 `app/models.py`에 `UserCheckinSeason` 모델 추가 (PK `(user_id, season)` + `idx_user_checkin_season_count`)
- [ ] 1.3 `app/models.py::CheerEvent.__table_args__`에 `Index("idx_cheer_events_user_id", "user_id")` 추가

## 2. DB 초기화

- [ ] 2.1 `app/db.py`에 `_ensure_cheer_events_user_id_index()` 헬퍼 추가 (`CREATE INDEX IF NOT EXISTS`)
- [ ] 2.2 `app/db.py`에 `_ensure_user_checkin_backfill()` 헬퍼 추가 (`user_checkin_season`에 행이 0건일 때만 raw cheer_events에서 valid 이벤트를 집계해 채움, idempotent)
- [ ] 2.3 `init_db()`의 `Base.metadata.create_all` 직후, postgres·sqlite 양쪽 분기에서 두 헬퍼 호출
- [ ] 2.4 백필 실패 시 startup이 죽지 않도록 try/except + 로깅

## 3. 집계 갱신 로직

- [ ] 3.1 `app/workers/cheer_validator.py`에 `UserCheckinDaily`, `UserCheckinSeason` import
- [ ] 3.2 `_increment_aggregates()`에 user 차원 +1 로직을 team 차원 직후에 추가
- [ ] 3.3 사용자 단위 집계도 KST 기준 date/season을 사용하는지 확인 (team 집계와 동일 timezone)

## 4. 테스트

- [ ] 4.1 `tests/test_api.py`에 valid 승격 시 `user_checkin_daily`/`user_checkin_season`이 각각 +1 되는지 검증하는 테스트 추가
- [ ] 4.2 같은 user_id + 같은 KST date에 valid 2건이 들어오면 daily.count = 2, season.count = 2 로 누적되는지 검증
- [ ] 4.3 백필 멱등성 테스트: 기존 valid 이벤트 N건이 있는 상태에서 `_ensure_user_checkin_backfill()`을 두 번 호출해도 카운트가 변하지 않는지 검증
- [ ] 4.4 `idx_cheer_events_user_id`가 실제로 생성되었는지 메타데이터 쿼리로 확인

## 5. 검증·완료

- [ ] 5.1 `pytest backend/api/tests/test_api.py -k checkin` 로컬 통과
- [ ] 5.2 `openspec validate add-user-checkin-aggregates --strict` 통과
- [ ] 5.3 PR 디스크립션에 본 change가 보상 정책 사전 인프라임을 명시, 운영 영향 0 강조
- [ ] 5.4 archive: `openspec archive add-user-checkin-aggregates` (배포 후)
