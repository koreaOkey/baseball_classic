## Schema

```text
cheer_events (existing)
  - 추가 인덱스: idx_cheer_events_user_id ON (user_id)

user_checkin_daily (new)
  - user_id     varchar(64)   NOT NULL
  - date        varchar(10)   NOT NULL   -- KST 기준 'YYYY-MM-DD'
  - count       integer       NOT NULL DEFAULT 1
  - updated_at  timestamptz   NOT NULL
  - PRIMARY KEY (user_id, date)

user_checkin_season (new)
  - user_id     varchar(64)   NOT NULL
  - season      varchar(8)    NOT NULL   -- KST 연도 'YYYY'
  - count       integer       NOT NULL DEFAULT 0
  - updated_at  timestamptz   NOT NULL
  - PRIMARY KEY (user_id, season)
  - INDEX idx_user_checkin_season_count ON (season, count DESC)
```

`team_checkin_*`와 동일한 컬럼 명명·타입(varchar `user_id`, varchar `date`/`season`)을 유지해 검증 워커 코드를 미러링 가능하게 한다.

## Aggregation Update Site

`workers/cheer_validator.py::_increment_aggregates`는 현재 `valid` 승격 1건당 `team_checkin_daily` + `team_checkin_season`을 +1 한다. 본 change는 같은 함수 안에 다음을 추가한다.

```python
# user 단위 집계 — team_checkin_* 와 동일 트랜잭션 안에서 갱신
user_daily = db.get(UserCheckinDaily, {"user_id": event.user_id, "date": date_key})
if user_daily is None:
    db.add(UserCheckinDaily(user_id=event.user_id, date=date_key, count=1))
else:
    user_daily.count += 1

user_season = db.get(UserCheckinSeason, {"user_id": event.user_id, "season": season})
if user_season is None:
    db.add(UserCheckinSeason(user_id=event.user_id, season=season, count=1))
else:
    user_season.count += 1
```

KST 기준 date/season 추출은 기존 코드를 그대로 재사용한다. 두 차원(팀·사용자)을 같은 함수 안에서 갱신하므로 SQLAlchemy 세션 단위로 원자성이 보장된다.

## Backfill 전략

1. 트리거 위치: `init_db()` 내부, `Base.metadata.create_all` 직후, 다른 `_ensure_*` 헬퍼들 옆.
2. 멱등 검사: `SELECT 1 FROM user_checkin_season LIMIT 1`. 한 행이라도 있으면 backfill skip.
3. 백필 쿼리 (PostgreSQL 기준):
   ```sql
   INSERT INTO user_checkin_daily (user_id, date, count, updated_at)
   SELECT
     user_id,
     to_char(client_ts AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD'),
     COUNT(*),
     now()
   FROM cheer_events
   WHERE validity_status = 'valid'
   GROUP BY 1, 2
   ON CONFLICT (user_id, date) DO NOTHING;

   INSERT INTO user_checkin_season (user_id, season, count, updated_at)
   SELECT
     user_id,
     to_char(client_ts AT TIME ZONE 'Asia/Seoul', 'YYYY'),
     COUNT(*),
     now()
   FROM cheer_events
   WHERE validity_status = 'valid'
   GROUP BY 1, 2
   ON CONFLICT (user_id, season) DO NOTHING;
   ```
4. SQLite(테스트 환경) fallback: `strftime`을 사용. `ON CONFLICT DO NOTHING`은 양쪽 모두 지원.

백필 실패 시 startup을 막지 않는다(try/except로 로깅 후 진행). 검증 워커가 이후 들어오는 신규 valid 이벤트는 계속 정상 증가시키므로 데이터 일관성은 시간이 흐르면 자기 치유한다.

## Why 별도 admin API를 두지 않는가

보상 정책이 정해지지 않은 상태에서 admin API의 응답 shape·인증 모델을 미리 정하면 정책에 끌려갈 위험이 있다. 본 change는 데이터 레이어에만 집중하고, 정책 결정 시점에 `add-checkin-reward-trigger` 같은 후속 change에서 API를 디자인한다.

## Trade-offs

- **`user_checkin_daily.count` 컬럼 유지**: 1일 1체크인 정책 하에서는 항상 1이라 컬럼이 무의미해 보일 수 있다. 그러나 (a) 향후 정책 완화 시 스키마 마이그레이션을 피하려고 유지하고, (b) `team_checkin_daily`와 컬럼 셋이 동일해 코드 재사용 비용이 낮다. 비용은 row당 4바이트 수준.
- **백필을 startup에서 처리**: 운영 데이터가 소규모인 현 시점에는 단순함이 큰 장점이다. 향후 데이터 증가 시 별도 admin endpoint로 분리하면 된다.
- **`user_checkin_season` 인덱스 `(season, count DESC)`**: "시즌 N회 이상 유저 목록"이 보상 트리거의 가장 흔한 쿼리가 될 것이라 가정하고 미리 추가한다. 정책에 따라 불필요해질 수 있으나 인덱스 1개 비용은 작다.
