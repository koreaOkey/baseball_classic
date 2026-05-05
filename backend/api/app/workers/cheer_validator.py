from __future__ import annotations

from datetime import timedelta, timezone
from math import atan2, cos, radians, sin, sqrt

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..cheer_signals import stadium_by_code
from ..models import CheerEvent, TeamCheckinDaily, TeamCheckinSeason


KST = timezone(timedelta(hours=9))


def validate_pending_cheer_events(db: Session, *, limit: int = 100) -> int:
    rows = db.execute(
        select(CheerEvent)
        .where(CheerEvent.validity_status == "pending")
        .order_by(CheerEvent.server_ts.asc())
        .limit(limit)
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


def _increment_aggregates(db: Session, event: CheerEvent) -> None:
    kst_date = event.client_ts.astimezone(KST).date()
    date_key = kst_date.isoformat()
    season = str(kst_date.year)

    daily = db.get(TeamCheckinDaily, {"team_code": event.team_code, "date": date_key})
    if daily is None:
        db.add(TeamCheckinDaily(team_code=event.team_code, date=date_key, count=1))
    else:
        daily.count += 1

    season_row = db.get(TeamCheckinSeason, {"team_code": event.team_code, "season": season})
    if season_row is None:
        db.add(TeamCheckinSeason(team_code=event.team_code, season=season, count=1))
    else:
        season_row.count += 1


def _distance_meters(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    radius = 6_371_000.0
    d_lat = radians(lat2 - lat1)
    d_lng = radians(lng2 - lng1)
    a = sin(d_lat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(d_lng / 2) ** 2
    c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return radius * c
