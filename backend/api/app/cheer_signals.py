from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta, timezone
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import Game


KST = timezone(timedelta(hours=9))


@dataclass(frozen=True)
class StadiumInfo:
    code: str
    name: str
    home_team_codes: tuple[str, ...]
    latitude: float
    longitude: float
    radius_meters: float
    indoor: bool = False


STADIUMS: tuple[StadiumInfo, ...] = (
    StadiumInfo("JAMSIL", "잠실야구장", ("DOOSAN", "LG"), 37.5121, 127.0719, 350),
    StadiumInfo("GOCHEOK", "고척스카이돔", ("KIWOOM",), 37.4982, 126.8670, 350, True),
    StadiumInfo("INCHEON", "인천SSG랜더스필드", ("SSG",), 37.4370, 126.6932, 350),
    StadiumInfo("SUWON", "수원KT위즈파크", ("KT",), 37.2997, 127.0097, 350),
    StadiumInfo("DAEJEON", "대전한화생명이글스파크", ("HANWHA",), 36.3170, 127.4291, 350),
    StadiumInfo("DAEGU", "대구삼성라이온즈파크", ("SAMSUNG",), 35.8411, 128.6817, 350),
    StadiumInfo("SAJIK", "사직야구장", ("LOTTE",), 35.1939, 129.0617, 350),
    StadiumInfo("GWANGJU", "광주기아챔피언스필드", ("KIA",), 35.1681, 126.8889, 350),
    StadiumInfo("CHANGWON", "창원NC파크", ("NC",), 35.2225, 128.5822, 350),
)


TEAM_COLORS: dict[str, str] = {
    "DOOSAN": "#131230",
    "LG": "#C30452",
    "KIWOOM": "#820024",
    "SAMSUNG": "#074CA1",
    "LOTTE": "#041E42",
    "SSG": "#CE0E2D",
    "KT": "#000000",
    "HANWHA": "#FF6600",
    "KIA": "#EA0029",
    "NC": "#315288",
}


def stadium_payloads() -> list[dict[str, Any]]:
    return [
        {
            "code": stadium.code,
            "name": stadium.name,
            "home_team_codes": list(stadium.home_team_codes),
            "latitude": stadium.latitude,
            "longitude": stadium.longitude,
            "radius_meters": stadium.radius_meters,
            "indoor": stadium.indoor,
        }
        for stadium in STADIUMS
    ]


def stadium_by_code(code: str) -> StadiumInfo | None:
    normalized = code.strip().upper()
    return next((stadium for stadium in STADIUMS if stadium.code == normalized), None)


def stadium_for_home_team(team_code: str) -> StadiumInfo | None:
    normalized = team_code.strip().upper()
    return next((stadium for stadium in STADIUMS if normalized in stadium.home_team_codes), None)


def team_code_from_name(value: str | None) -> str | None:
    normalized = (value or "").strip().lower()
    if not normalized:
        return None

    checks = (
        ("DOOSAN", ("doosan", "두산", "베어스")),
        ("LG", ("lg", "엘지", "트윈스")),
        ("KIWOOM", ("kiwoom", "키움", "히어로즈", "넥센")),
        ("SAMSUNG", ("samsung", "삼성", "라이온즈")),
        ("LOTTE", ("lotte", "롯데", "자이언츠")),
        ("SSG", ("ssg", "랜더스", "lander")),
        ("KT", ("kt", "케이티", "위즈", "wiz")),
        ("HANWHA", ("hanwha", "한화", "이글스")),
        ("KIA", ("kia", "기아", "타이거즈")),
        ("NC", ("nc", "엔씨", "다이노스", "dinos")),
    )
    for code, tokens in checks:
        if any(token in normalized for token in tokens):
            return code
    return None


def build_cheer_signals(db: Session, *, target_date: date) -> list[dict[str, Any]]:
    game_date = target_date.isoformat()
    rows = db.execute(
        select(Game)
        .where(Game.game_date == game_date)
        .order_by(Game.start_time.asc().nullslast(), Game.id.asc())
    ).scalars().all()

    entries: list[dict[str, Any]] = []
    for game in rows:
        home_code = team_code_from_name(game.home_team)
        away_code = team_code_from_name(game.away_team)
        if not home_code or not away_code:
            continue

        stadium = stadium_for_home_team(home_code)
        if stadium is None:
            continue

        fire_at = _fire_at_datetime(target_date=target_date, start_time_raw=game.start_time)
        entries.append({
            "game_id": game.id,
            "stadium_code": stadium.code,
            "fire_at_iso": fire_at.isoformat(),
            "signals": [
                _signal_payload(home_code, role="home"),
                _signal_payload(away_code, role="away"),
            ],
        })
    return entries


def _signal_payload(team_code: str, *, role: str) -> dict[str, str]:
    return {
        "team_code": team_code,
        "role": role,
        "cheer_text": "지금, 함께 응원해요!",
        "primary_color_hex": TEAM_COLORS.get(team_code, "#3B82F6"),
        "haptic_pattern_id": f"{team_code.lower()}_intro_v1",
    }


def _fire_at_datetime(*, target_date: date, start_time_raw: str | None) -> datetime:
    hour, minute = 18, 30
    if start_time_raw:
        parts = start_time_raw.split(":")
        if len(parts) == 2:
            try:
                hour = int(parts[0])
                minute = int(parts[1])
            except ValueError:
                pass
    return datetime.combine(target_date, time(hour=hour, minute=minute), tzinfo=KST).astimezone(UTC)
