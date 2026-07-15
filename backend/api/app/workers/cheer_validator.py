from __future__ import annotations

from datetime import timedelta, timezone
from math import atan2, cos, radians, sin, sqrt
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..cheer_signals import stadium_by_code
from ..models import (
    CheerEvent,
    TeamCheckinDaily,
    TeamCheckinSeason,
    UserCheckinDaily,
    UserCheckinSeason,
    utcnow,
)


KST = timezone(timedelta(hours=9))


def validate_pending_cheer_events(db: Session, *, limit: int = 100) -> int:
    # 여러 워커/요청이 동시에 돌 때 같은 pending 행을 이중 집계하지 않도록
    # 행 잠금 + skip_locked 로 분할 처리한다. (SQLite 는 FOR UPDATE 를 무시하지만
    # 테스트 환경 단일 프로세스라 문제없음)
    rows = db.execute(
        select(CheerEvent)
        .where(CheerEvent.validity_status == "pending")
        .order_by(CheerEvent.server_ts.asc())
        .limit(limit)
        .with_for_update(skip_locked=True)
    ).scalars().all()

    updated = 0
    for event in rows:
        status, reason = _validate_event(event)
        event.validity_status = status
        event.invalidity_reason = reason
        if status == "valid":
            _increment_aggregates(db, event)
        updated += 1
    db.commit()
    return updated


def _validate_event(event: CheerEvent) -> tuple[str, str | None]:
    if event.mock_location:
        return "invalid", "mock_location"

    stadium = stadium_by_code(event.stadium_code)
    if stadium is None:
        return "invalid", "unknown_stadium"

    if event.lat is None or event.lng is None:
        return "suspicious", "missing_coordinates"

    distance = _distance_meters(event.lat, event.lng, stadium.latitude, stadium.longitude)
    if distance > stadium.radius_meters:
        return "invalid", "outside_stadium_radius"

    return "valid", None


def _increment_count_upsert(db: Session, model: type, keys: dict[str, Any]) -> None:
    """집계 카운트를 read-modify-write 없이 원자적 upsert 로 +1.

    동시 실행 시 잃어버린 갱신(lost update)을 막기 위해
    UPDATE ... SET count = count + 1 형태로 DB 에서 증가시킨다.
    """
    dialect = db.get_bind().dialect.name
    if dialect == "postgresql":
        from sqlalchemy.dialects.postgresql import insert as dialect_insert
    else:
        from sqlalchemy.dialects.sqlite import insert as dialect_insert

    table = model.__table__
    stmt = dialect_insert(model).values(**keys, count=1, updated_at=utcnow())
    stmt = stmt.on_conflict_do_update(
        index_elements=list(keys.keys()),
        set_={"count": table.c.count + 1, "updated_at": utcnow()},
    )
    db.execute(stmt)


def _increment_aggregates(db: Session, event: CheerEvent) -> None:
    kst_date = event.client_ts.astimezone(KST).date()
    date_key = kst_date.isoformat()
    season = str(kst_date.year)

    _increment_count_upsert(db, TeamCheckinDaily, {"team_code": event.team_code, "date": date_key})
    _increment_count_upsert(db, TeamCheckinSeason, {"team_code": event.team_code, "season": season})
    _increment_count_upsert(db, UserCheckinDaily, {"user_id": event.user_id, "date": date_key})
    _increment_count_upsert(db, UserCheckinSeason, {"user_id": event.user_id, "season": season})


def _distance_meters(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius = 6_371_000.0
    d_lat = radians(lat2 - lat1)
    d_lng = radians(lng2 - lng1)
    a = sin(d_lat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(d_lng / 2) ** 2
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return radius * c
