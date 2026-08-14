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
    base_first_runner: Mapped[str | None] = mapped_column(String(128), nullable=True)
    base_second_runner: Mapped[str | None] = mapped_column(String(128), nullable=True)
    base_third_runner: Mapped[str | None] = mapped_column(String(128), nullable=True)

    pitcher: Mapped[str | None] = mapped_column(String(128), nullable=True)
    batter: Mapped[str | None] = mapped_column(String(128), nullable=True)
    start_time: Mapped[str | None] = mapped_column(String(5), nullable=True)
    observed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    live_started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    home_hits: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_hits: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    home_home_runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_home_runs: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    home_outs_total: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    away_outs_total: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    # 이닝별 라인스코어 {"home": {"1": 0, ...}, "away": {...}}. 스냅샷 미제공 시 NULL 유지.
    line_score_json: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)
    home_errors: Mapped[int | None] = mapped_column(Integer, nullable=True)
    away_errors: Mapped[int | None] = mapped_column(Integer, nullable=True)
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
    inning: Mapped[str | None] = mapped_column(String(32), nullable=True)
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
    is_sandbox: Mapped[bool] = mapped_column(nullable=False, server_default="false")
    display_name_style: Mapped[str] = mapped_column(String(16), nullable=False, default="TEAM", server_default="TEAM")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class LiveViewSession(Base):
    """토글 기반 경기 관람 집계 세션.

    device_tokens 는 푸시 송신 대상이고, live_view_sessions 는 운영 모니터링용
    활성 관람 surface/person 집계 원천이다.
    """

    __tablename__ = "live_view_sessions"
    __table_args__ = (
        UniqueConstraint("game_id", "user_key", "surface", name="uq_live_view_session_surface"),
        Index("idx_live_view_sessions_game_active", "game_id", "active"),
        Index("idx_live_view_sessions_updated_at", "updated_at"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(String(64), nullable=False)
    user_key: Mapped[str] = mapped_column(String(128), nullable=False)
    surface: Mapped[str] = mapped_column(String(16), nullable=False)
    token_key: Mapped[str | None] = mapped_column(String(256), nullable=True)
    my_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default="true")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class TeamSubscriptionToken(Base):
    """응원팀 단위 글로벌 푸시 구독.

    device_tokens 가 (token, game_id) 단위 임시 구독인 반면,
    이 테이블은 token 1개 = 응원팀 1개로 영속 구독. 경기 시작 같은
    경기-수명-독립 알림에 사용한다.
    """

    __tablename__ = "team_subscription_tokens"
    __table_args__ = (
        UniqueConstraint("token", name="uq_team_subscription_token"),
        Index("idx_team_subscription_tokens_my_team", "my_team"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    token: Mapped[str] = mapped_column(String(256), nullable=False)
    my_team: Mapped[str] = mapped_column(String(64), nullable=False)
    platform: Mapped[str] = mapped_column(String(16), nullable=False, default="ios")
    is_sandbox: Mapped[bool] = mapped_column(nullable=False, server_default="false")
    display_name_style: Mapped[str] = mapped_column(String(16), nullable=False, default="TEAM", server_default="TEAM")
    # 등록 시점 앱 버전(예 "8.6.0"). 패배 푸시 버전 게이트에 사용. 구버전/미전송은 NULL.
    app_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class LiveActivityToken(Base):
    __tablename__ = "live_activity_tokens"
    __table_args__ = (
        UniqueConstraint("game_id", "token", name="uq_live_activity_token_game"),
        Index("idx_live_activity_tokens_game_id", "game_id"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    token: Mapped[str] = mapped_column(String(256), nullable=False)
    game_id: Mapped[str] = mapped_column(String(64), nullable=False)
    my_team: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class AppConfig(Base):
    __tablename__ = "app_config"
    __table_args__ = (
        UniqueConstraint("platform", name="uq_app_config_platform"),
        Index("idx_app_config_platform", "platform"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    platform: Mapped[str] = mapped_column(String(16), nullable=False)
    min_supported_version: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    latest_version: Mapped[str] = mapped_column(String(32), nullable=False, default="")
    force_update: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    update_title: Mapped[str] = mapped_column(String(128), nullable=False, default="업데이트가 필요합니다")
    update_message: Mapped[str] = mapped_column(Text, nullable=False, default="안정적인 서비스 운영을 위해 최신 버전으로 업데이트해 주세요.")
    store_url: Mapped[str] = mapped_column(Text, nullable=False, default="")
    notice_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    notice_title: Mapped[str] = mapped_column(String(128), nullable=False, default="")
    notice_message: Mapped[str] = mapped_column(Text, nullable=False, default="")
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


class CheerEvent(Base):
    __tablename__ = "cheer_events"
    __table_args__ = (
        Index("idx_cheer_events_team_status", "team_code", "validity_status"),
        Index("idx_cheer_events_stadium_ts", "stadium_code", "client_ts"),
        Index("idx_cheer_events_user_id", "user_id"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(String(64), nullable=False)
    team_code: Mapped[str] = mapped_column(String(32), nullable=False)
    stadium_code: Mapped[str] = mapped_column(String(32), nullable=False)
    game_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    client_ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    server_ts: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    accuracy_m: Mapped[float | None] = mapped_column(Float, nullable=True)
    mock_location: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    app_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    device_id_hash: Mapped[str | None] = mapped_column(String(128), nullable=True)
    ip_hash: Mapped[str | None] = mapped_column(String(128), nullable=True)
    # raw 레벨에서만 platform 보존. 집계 테이블(team_checkin_daily/season)은 iOS+Android 합산.
    platform: Mapped[str] = mapped_column(String(16), nullable=False, default="unknown")
    validity_status: Mapped[str] = mapped_column(String(16), nullable=False, default="pending")
    invalidity_reason: Mapped[str | None] = mapped_column(Text, nullable=True)
    is_home_team: Mapped[bool | None] = mapped_column(Boolean, nullable=True)
    opponent_team_code: Mapped[str | None] = mapped_column(String(32), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class TeamCheckinDaily(Base):
    __tablename__ = "team_checkin_daily"
    __table_args__ = (
        Index("idx_team_checkin_daily_date", "date"),
    )

    team_code: Mapped[str] = mapped_column(String(32), primary_key=True)
    date: Mapped[str] = mapped_column(String(10), primary_key=True)
    count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class TeamCheckinSeason(Base):
    __tablename__ = "team_checkin_season"
    __table_args__ = (
        Index("idx_team_checkin_season_season_count", "season", "count"),
    )

    team_code: Mapped[str] = mapped_column(String(32), primary_key=True)
    season: Mapped[str] = mapped_column(String(8), primary_key=True)
    count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class UserCheckinDaily(Base):
    __tablename__ = "user_checkin_daily"
    __table_args__ = (
        Index("idx_user_checkin_daily_date", "date"),
    )

    user_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    date: Mapped[str] = mapped_column(String(10), primary_key=True)
    count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class UserCheckinSeason(Base):
    __tablename__ = "user_checkin_season"
    __table_args__ = (
        Index("idx_user_checkin_season_count", "season", "count"),
    )

    user_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    season: Mapped[str] = mapped_column(String(8), primary_key=True)
    count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


# --- 분풀이(venting) 모드 백엔드 Phase 2 (다크: 플래그 OFF 시 미사용) ---


class TeamManager(Base):
    """팀별 감독 디렉터리(시즌 단위 수동 관리). 경질 시 새 행을 추가하고
    이전 행의 effective_to 를 채운다. '현재 감독' = effective_to IS NULL 최신."""

    __tablename__ = "team_manager"
    __table_args__ = (
        Index("idx_team_manager_team_season", "team_code", "season"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    team_code: Mapped[str] = mapped_column(String(32), nullable=False)
    manager_name: Mapped[str] = mapped_column(String(64), nullable=False)
    season: Mapped[str] = mapped_column(String(8), nullable=False)
    effective_from: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    effective_to: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class VentingRegretCache(Base):
    """경기 종료 후 산정한 패배팀 관점 regret-top5 캐시.
    items 에는 역할 레이블·타순·event 참조·사유문구만 저장하고 선수 실명은 저장하지 않는다
    (실명은 클라이언트가 조회 시점에 박스스코어와 결합해 선택 화면에서만 표시)."""

    __tablename__ = "venting_regret_cache"
    __table_args__ = (
        UniqueConstraint("game_id", "team_code", name="uq_venting_regret_cache_game_team"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    game_id: Mapped[str] = mapped_column(String(64), nullable=False)
    team_code: Mapped[str] = mapped_column(String(32), nullable=False)  # 패배팀
    items: Mapped[Any] = mapped_column(JSON, nullable=False)  # 실명 없음
    manager_name: Mapped[str | None] = mapped_column(String(64), nullable=True)
    source: Mapped[str] = mapped_column(String(16), nullable=False, default="rule_fallback")  # llm | rule_fallback
    computed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class VentingEvent(Base):
    """분풀이 지표 raw 이벤트. team(유저 응원팀) 필수 — 팀 랭킹 집계 기반."""

    __tablename__ = "venting_event"
    __table_args__ = (
        Index("idx_venting_event_team_created", "team", "created_at"),
        Index("idx_venting_event_type", "event_type"),
    )

    id: Mapped[int] = mapped_column(BIGINT_TYPE, primary_key=True, autoincrement=True)
    event_type: Mapped[str] = mapped_column(String(32), nullable=False)  # room_enter|destroy_complete|retry_prompt_shown|retry_ad_start
    entry_source: Mapped[str | None] = mapped_column(String(24), nullable=True)  # home_card|live_button|loss_prompt
    team: Mapped[str] = mapped_column(String(32), nullable=False)  # 유저 응원팀
    game_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    user_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    client_ts: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    platform: Mapped[str] = mapped_column(String(16), nullable=False, default="unknown")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow)


class VentingTeamDaily(Base):
    """팀별 일간 분풀이 방 실행 카운트 캐시(랭킹용)."""

    __tablename__ = "venting_team_daily"
    __table_args__ = (
        Index("idx_venting_team_daily_date", "date"),
    )

    team: Mapped[str] = mapped_column(String(32), primary_key=True)
    date: Mapped[str] = mapped_column(String(10), primary_key=True)
    count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)


class VentingTeamSeason(Base):
    """팀별 시즌 누적 분풀이 방 실행 카운트 캐시(랭킹용)."""

    __tablename__ = "venting_team_season"
    __table_args__ = (
        Index("idx_venting_team_season_count", "season", "count"),
    )

    team: Mapped[str] = mapped_column(String(32), primary_key=True)
    season: Mapped[str] = mapped_column(String(8), primary_key=True)
    count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=utcnow, onupdate=utcnow)
