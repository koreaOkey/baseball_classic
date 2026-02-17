from typing import Any
from datetime import datetime, timezone

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Index, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Game(Base):
    __tablename__ = "games"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    home_team: Mapped[str] = mapped_column(String(64), nullable=False)
    away_team: Mapped[str] = mapped_column(String(64), nullable=False)
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="SCHEDULED")
    inning: Mapped[str] = mapped_column(String(32), nullable=False, default="-")

    home_score: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_score: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    ball_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    strike_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    out_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    base_first: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    base_second: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    base_third: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)

    pitcher: Mapped[str | None] = mapped_column(String(128), nullable=True)
    batter: Mapped[str | None] = mapped_column(String(128), nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
        onupdate=utcnow,
    )

    events: Mapped[list["GameEvent"]] = relationship(
        back_populates="game",
        order_by="GameEvent.cursor",
        cascade="all, delete-orphan",
    )


class GameEvent(Base):
    __tablename__ = "game_events"
    __table_args__ = (
        UniqueConstraint("game_id", "source_event_id", name="uq_game_event_source"),
        Index("idx_game_events_game_cursor", "game_id", "cursor"),
    )

    cursor: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(
        ForeignKey("games.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    source_event_id: Mapped[str] = mapped_column(String(80), nullable=False)
    event_type: Mapped[str] = mapped_column(String(24), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    event_time: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    haptic_pattern: Mapped[str | None] = mapped_column(String(64), nullable=True)
    payload_json: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=utcnow,
    )

    game: Mapped[Game] = relationship(back_populates="events")
