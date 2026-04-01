from datetime import datetime, timezone
from typing import Any

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    ForeignKeyConstraint,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


BIGINT_TYPE = BigInteger().with_variant(Integer, "sqlite")


class Game(Base):
    __tablename__ = "games"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    game_date: Mapped[str | None] = mapped_column(String(10), nullable=True, index=True)
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
    start_time: Mapped[str | None] = mapped_column(String(5), nullable=True)
    observed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    home_hits: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_hits: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    home_home_runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_home_runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    home_outs_total: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_outs_total: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    last_event_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    last_event_desc: Mapped[str | None] = mapped_column(Text, nullable=True)
    last_event_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)

    events: Mapped[list["GameEvent"]] = relationship(
        back_populates="game",
        order_by="GameEvent.cursor",
        cascade="all, delete-orphan",
    )
    lineup_slots: Mapped[list["GameLineupSlot"]] = relationship(
        back_populates="game",
        cascade="all, delete-orphan",
    )
    batter_stats: Mapped[list["GameBatterStat"]] = relationship(
        back_populates="game",
        cascade="all, delete-orphan",
    )
    pitcher_stats: Mapped[list["GamePitcherStat"]] = relationship(
        back_populates="game",
        cascade="all, delete-orphan",
    )
    notes: Mapped[list["GameNote"]] = relationship(
        back_populates="game",
        cascade="all, delete-orphan",
    )


class GameEvent(Base):
    __tablename__ = "game_events"
    __table_args__ = (
        UniqueConstraint("game_id", "source_event_id", name="uq_game_event_source"),
        Index("idx_game_events_game_cursor", "game_id", "cursor"),
    )

    cursor: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("games.id", ondelete="CASCADE"), nullable=False, index=True)
    source_event_id: Mapped[str] = mapped_column(String(80), nullable=False)
    event_type: Mapped[str] = mapped_column(String(24), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False, default="")
    event_time: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    pitcher: Mapped[str | None] = mapped_column(String(128), nullable=True)
    batter: Mapped[str | None] = mapped_column(String(128), nullable=True)
    haptic_pattern: Mapped[str | None] = mapped_column(String(64), nullable=True)
    payload_json: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)

    game: Mapped[Game] = relationship(back_populates="events")


class GameLineupSlot(Base):
    __tablename__ = "game_lineup_slots"
    __table_args__ = (
        UniqueConstraint("game_id", "team_side", "batting_order", name="uq_game_lineup_slot"),
        Index("idx_game_lineup_slots_game_side_order", "game_id", "team_side", "batting_order"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("games.id", ondelete="CASCADE"), nullable=False)
    team_side: Mapped[str] = mapped_column(String(8), nullable=False)
    player_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    game_date: Mapped[str | None] = mapped_column(String(10), nullable=True)
    home_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    away_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    batting_order: Mapped[int] = mapped_column(Integer, nullable=False)
    player_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    player_name: Mapped[str] = mapped_column(String(128), nullable=False)
    position_code: Mapped[str | None] = mapped_column(String(32), nullable=True)
    position_name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    is_starter: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    entered_at_event_cursor: Mapped[int | None] = mapped_column(BIGINT_TYPE, ForeignKey("game_events.cursor", ondelete="SET NULL"), nullable=True)
    exited_at_event_cursor: Mapped[int | None] = mapped_column(BIGINT_TYPE, ForeignKey("game_events.cursor", ondelete="SET NULL"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)

    game: Mapped[Game] = relationship(back_populates="lineup_slots")


class GameBatterStat(Base):
    __tablename__ = "game_batter_stats"
    __table_args__ = (
        ForeignKeyConstraint(
            ["game_id", "team_side", "batting_order"],
            ["game_lineup_slots.game_id", "game_lineup_slots.team_side", "game_lineup_slots.batting_order"],
        ),
        Index("idx_game_batter_stats_game_side_order", "game_id", "team_side", "batting_order"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("games.id", ondelete="CASCADE"), nullable=False)
    team_side: Mapped[str] = mapped_column(String(8), nullable=False)
    player_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    game_date: Mapped[str | None] = mapped_column(String(10), nullable=True)
    home_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    away_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    player_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    player_name: Mapped[str] = mapped_column(String(128), nullable=False)
    batting_order: Mapped[int | None] = mapped_column(Integer, nullable=True)
    primary_position: Mapped[str | None] = mapped_column(String(64), nullable=True)
    is_starter: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    plate_appearances: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    at_bats: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    hits: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    rbi: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    doubles: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    triples: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    home_runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    walks: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    strikeouts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    stolen_bases: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    caught_stealing: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    hit_by_pitch: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    sac_bunts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    sac_flies: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    left_on_base: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)

    game: Mapped[Game] = relationship(back_populates="batter_stats")


class GamePitcherStat(Base):
    __tablename__ = "game_pitcher_stats"
    __table_args__ = (
        Index("idx_game_pitcher_stats_game_side_appearance", "game_id", "team_side", "appearance_order"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("games.id", ondelete="CASCADE"), nullable=False)
    team_side: Mapped[str] = mapped_column(String(8), nullable=False)
    player_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    game_date: Mapped[str | None] = mapped_column(String(10), nullable=True)
    home_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    away_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    appearance_order: Mapped[int | None] = mapped_column(Integer, nullable=True)
    player_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    player_name: Mapped[str] = mapped_column(String(128), nullable=False)
    is_starter: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    outs_recorded: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    hits_allowed: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    runs_allowed: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    earned_runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    walks_allowed: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    strikeouts: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    home_runs_allowed: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    batters_faced: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    at_bats_against: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    pitches_thrown: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)

    game: Mapped[Game] = relationship(back_populates="pitcher_stats")


class GameNote(Base):
    __tablename__ = "game_notes"
    __table_args__ = (Index("idx_game_notes_game_created", "game_id", "created_at"),)

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(ForeignKey("games.id", ondelete="CASCADE"), nullable=False)
    team_side: Mapped[str | None] = mapped_column(String(8), nullable=True)
    note_type: Mapped[str] = mapped_column(String(64), nullable=False)
    note_title: Mapped[str] = mapped_column(String(255), nullable=False, default="")
    note_body: Mapped[str] = mapped_column(Text, nullable=False, default="")
    inning: Mapped[str | None] = mapped_column(String(32), nullable=True)
    event_cursor: Mapped[int | None] = mapped_column(BIGINT_TYPE, ForeignKey("game_events.cursor", ondelete="SET NULL"), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)

    game: Mapped[Game] = relationship(back_populates="notes")


class DeviceToken(Base):
    __tablename__ = "device_tokens"
    __table_args__ = (
        UniqueConstraint("token", "game_id", name="uq_device_token_game"),
        Index("idx_device_tokens_game_id", "game_id"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    token: Mapped[str] = mapped_column(String(256), nullable=False)
    game_id: Mapped[str] = mapped_column(String(64), nullable=False)
    my_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    platform: Mapped[str] = mapped_column(String(16), nullable=False, default="ios")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class TeamRecord(Base):
    __tablename__ = "team_record"
    __table_args__ = (
        UniqueConstraint("category_id", "season_code", "team_id", name="uq_team_record_category_season_team"),
        Index("idx_team_record_category_season_rank", "category_id", "season_code", "ranking"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    upper_category_id: Mapped[str | None] = mapped_column(String(32), nullable=True)
    category_id: Mapped[str] = mapped_column(String(32), nullable=False)
    season_code: Mapped[str] = mapped_column(String(8), nullable=False)
    team_id: Mapped[str] = mapped_column(String(32), nullable=False)
    team_name: Mapped[str] = mapped_column(String(64), nullable=False)
    team_short_name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    ranking: Mapped[int | None] = mapped_column(Integer, nullable=True)
    order_no: Mapped[int | None] = mapped_column(Integer, nullable=True)
    game_type: Mapped[str | None] = mapped_column(String(32), nullable=True)
    wra: Mapped[float | None] = mapped_column(Float, nullable=True)
    game_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    win_game_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    drawn_game_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    lose_game_count: Mapped[int | None] = mapped_column(Integer, nullable=True)
    game_behind: Mapped[float | None] = mapped_column(Float, nullable=True)
    continuous_game_result: Mapped[str | None] = mapped_column(String(32), nullable=True)
    last_five_games: Mapped[str | None] = mapped_column(String(16), nullable=True)
    offense_hra: Mapped[float | None] = mapped_column(Float, nullable=True)
    defense_era: Mapped[float | None] = mapped_column(Float, nullable=True)
    payload_json: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    observed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)
