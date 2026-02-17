import json
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from .models import Game, GameEvent
from .schemas import (
    BaseStatus,
    CrawlerEventIn,
    CrawlerSnapshotRequest,
    EventType,
    GameEventOut,
    GameStateOut,
    GameStatus,
    GameSummaryOut,
)


STATUS_MAP = {
    "LIVE": GameStatus.LIVE,
    "PLAYING": GameStatus.LIVE,
    "IN_PROGRESS": GameStatus.LIVE,
    "READY": GameStatus.SCHEDULED,
    "SCHEDULED": GameStatus.SCHEDULED,
    "PREGAME": GameStatus.SCHEDULED,
    "END": GameStatus.FINISHED,
    "FINAL": GameStatus.FINISHED,
    "RESULT": GameStatus.FINISHED,
    "FINISHED": GameStatus.FINISHED,
}

EVENT_MAP = {
    "BALL": EventType.BALL,
    "STRIKE": EventType.STRIKE,
    "OUT": EventType.OUT,
    "HIT": EventType.HIT,
    "HOMERUN": EventType.HOMERUN,
    "HOME_RUN": EventType.HOMERUN,
    "SCORE": EventType.SCORE,
}


def now_utc() -> datetime:
    return datetime.now(UTC)


def ensure_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC)


def normalize_status(raw: str) -> GameStatus:
    return STATUS_MAP.get(raw.strip().upper(), GameStatus.SCHEDULED)


def normalize_event_type(raw: str) -> EventType:
    return EVENT_MAP.get(raw.strip().upper(), EventType.OTHER)


def upsert_game_from_snapshot(db: Session, game_id: str, payload: CrawlerSnapshotRequest) -> Game:
    game = db.get(Game, game_id)
    if game is None:
        game = Game(id=game_id, home_team=payload.homeTeam, away_team=payload.awayTeam)
        db.add(game)

    game.home_team = payload.homeTeam
    game.away_team = payload.awayTeam
    game.status = normalize_status(payload.status).value
    game.inning = payload.inning
    game.home_score = payload.homeScore
    game.away_score = payload.awayScore
    game.ball_count = payload.ball
    game.strike_count = payload.strike
    game.out_count = payload.out
    game.base_first = payload.bases.first
    game.base_second = payload.bases.second
    game.base_third = payload.bases.third
    game.pitcher = payload.pitcher
    game.batter = payload.batter
    game.updated_at = ensure_utc(payload.observedAt) if payload.observedAt else now_utc()
    db.flush()
    return game


def insert_events(
    db: Session,
    game_id: str,
    events: list[CrawlerEventIn],
) -> tuple[list[GameEvent], int]:
    if not events:
        return [], 0

    source_ids = [item.sourceEventId for item in events]
    existing_source_ids = set(
        db.execute(
            select(GameEvent.source_event_id).where(
                GameEvent.game_id == game_id,
                GameEvent.source_event_id.in_(source_ids),
            )
        ).scalars()
    )

    inserted: list[GameEvent] = []
    duplicate_count = 0
    seen_in_batch: set[str] = set()

    for event_in in sorted(events, key=lambda item: ensure_utc(item.occurredAt)):
        source_id = event_in.sourceEventId
        if source_id in existing_source_ids or source_id in seen_in_batch:
            duplicate_count += 1
            continue

        seen_in_batch.add(source_id)
        event = GameEvent(
            game_id=game_id,
            source_event_id=source_id,
            event_type=normalize_event_type(event_in.type).value,
            description=event_in.description,
            event_time=ensure_utc(event_in.occurredAt),
            haptic_pattern=event_in.hapticPattern,
            payload_json=(
                json.dumps(event_in.metadata, ensure_ascii=False) if event_in.metadata is not None else None
            ),
        )
        db.add(event)
        inserted.append(event)

    db.flush()
    return inserted, duplicate_count


def to_game_summary(game: Game) -> GameSummaryOut:
    return GameSummaryOut(
        id=game.id,
        homeTeam=game.home_team,
        awayTeam=game.away_team,
        homeScore=game.home_score,
        awayScore=game.away_score,
        inning=game.inning,
        status=normalize_status(game.status),
        updatedAt=game.updated_at,
    )


def to_event_out(event: GameEvent) -> GameEventOut:
    return GameEventOut(
        cursor=event.cursor,
        id=event.source_event_id,
        type=normalize_event_type(event.event_type),
        description=event.description,
        time=event.event_time,
        hapticPattern=event.haptic_pattern,
    )


def build_game_state(db: Session, game: Game) -> GameStateOut:
    latest_event = db.execute(
        select(GameEvent).where(GameEvent.game_id == game.id).order_by(GameEvent.cursor.desc()).limit(1)
    ).scalar_one_or_none()

    return GameStateOut(
        gameId=game.id,
        homeTeam=game.home_team,
        awayTeam=game.away_team,
        homeScore=game.home_score,
        awayScore=game.away_score,
        inning=game.inning,
        status=normalize_status(game.status),
        ball=game.ball_count,
        strike=game.strike_count,
        out=game.out_count,
        bases=BaseStatus(first=game.base_first, second=game.base_second, third=game.base_third),
        pitcher=game.pitcher,
        batter=game.batter,
        lastEventType=normalize_event_type(latest_event.event_type) if latest_event else None,
        lastEventAt=latest_event.event_time if latest_event else None,
        updatedAt=game.updated_at,
    )
