from contextlib import asynccontextmanager
from datetime import UTC, date, datetime
from typing import Annotated, Any
import logging
import secrets
import time

from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.encoders import jsonable_encoder
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import and_, or_, select
from sqlalchemy.exc import DBAPIError
from sqlalchemy.orm import Session

from .config import get_settings
from .db import SessionLocal, get_db, init_db
from .event_bus import event_bus
from .models import Game, GameEvent
from .redis_bus import RedisBroadcastRelay
from .schemas import (
    CrawlerSnapshotRequest,
    CrawlerTeamRecordRequest,
    EventsResponse,
    GameStateOut,
    GameStatus,
    GameSummaryOut,
    IngestResult,
    TeamRecordIngestResult,
    TeamRecordOut,
)
from .services import (
    build_game_state,
    get_team_record,
    insert_events,
    normalize_status,
    sync_snapshot_details,
    to_team_record_out,
    to_event_out,
    to_game_summary,
    upsert_team_records,
    upsert_game_from_snapshot,
)


settings = get_settings()
logger = logging.getLogger(__name__)

SNAPSHOT_INGEST_RETRY_DELAYS_SECONDS = (0.2, 0.5, 1.0)
redis_relay = RedisBroadcastRelay(
    redis_url=settings.redis_url,
    channel=settings.redis_pubsub_channel,
    source_instance_id=settings.instance_id,
)


def _is_snapshot_lock_timeout(exc: DBAPIError) -> bool:
    # Supabase/Postgres can raise statement timeout while waiting on row lock:
    # "canceling statement due to statement timeout ... while locking tuple ... in relation \"games\""
    message = str(exc).lower()
    original = getattr(exc, "orig", None)
    if original is not None:
        message = f"{message} {original}".lower()
        sqlstate = getattr(original, "sqlstate", None) or getattr(original, "pgcode", None)
        if sqlstate in {"55P03", "57014"} and "lock" in message:
            return True

    return "statement timeout" in message and "locking tuple" in message and "games" in message


def _rollback_session_safely(db: Session, *, game_id: str, attempt: int) -> bool:
    try:
        db.rollback()
        return True
    except Exception:
        logger.exception(
            "snapshot ingest rollback failed: game_id=%s attempt=%s",
            game_id,
            attempt,
        )
        try:
            db.close()
        except Exception:
            logger.exception("snapshot ingest session close failed: game_id=%s", game_id)
        return False


async def _broadcast_live_message(game_id: str, message: dict[str, Any]) -> None:
    await event_bus.broadcast(game_id, message)
    await redis_relay.publish(game_id, message)


async def _on_redis_live_message(game_id: str, message: dict[str, Any]) -> None:
    await event_bus.broadcast(game_id, message)


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    await redis_relay.start(_on_redis_live_message)
    try:
        yield
    finally:
        await redis_relay.stop()


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=settings.cors_origins != ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, Any]:
    # Keep /health lightweight for platform health checks.
    return {
        "status": "ok",
        "service": "backend-api",
        "environment": settings.environment,
        "time": datetime.now(UTC),
    }


@app.get("/live")
def live() -> dict[str, Any]:
    return health()


@app.get("/ready")
async def ready() -> JSONResponse:
    db_connected = True
    db_detail: str = "connected"
    try:
        with SessionLocal() as db:
            db.execute(select(1)).scalar_one()
    except Exception as exc:
        db_connected = False
        db_detail = f"error:{exc.__class__.__name__}"

    redis_connected, redis_detail = await redis_relay.ping()
    is_ready = db_connected and redis_connected
    payload = {
        "status": "ok" if is_ready else "degraded",
        "service": "backend-api",
        "environment": settings.environment,
        "db": db_detail,
        "redis": redis_detail if not redis_connected else "connected",
        "time": datetime.now(UTC),
    }
    return JSONResponse(status_code=200 if is_ready else 503, content=jsonable_encoder(payload))


@app.get("/health/verbose")
async def health_verbose() -> dict[str, Any]:
    redis_connected, redis_detail = await redis_relay.ping()
    return {
        "status": "ok",
        "service": "backend-api",
        "environment": settings.environment,
        "redis": redis_detail if not redis_connected else "connected",
        "time": datetime.now(UTC),
    }


@app.get("/games", response_model=list[GameSummaryOut])
def list_games(
    status: GameStatus | None = None,
    game_date: date | None = Query(default=None, alias="date"),
    limit: int = Query(default=20, ge=1, le=100),
    db: Session = Depends(get_db),
) -> list[GameSummaryOut]:
    query = select(Game)
    if status is not None:
        query = query.where(Game.status == status.value)
    if game_date is not None:
        iso_date = game_date.isoformat()
        prefix = game_date.strftime("%Y%m%d")
        query = query.where(
            or_(
                Game.game_date == iso_date,
                and_(Game.game_date.is_(None), Game.id.like(f"{prefix}%")),
            )
        )

    query = query.order_by(Game.updated_at.desc()).limit(limit)

    games = db.execute(query).scalars().all()
    return [to_game_summary(game) for game in games]


@app.get("/games/{game_id}", response_model=GameSummaryOut)
def get_game(game_id: str, db: Session = Depends(get_db)) -> GameSummaryOut:
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="game not found")
    return to_game_summary(game)


@app.get("/games/{game_id}/state", response_model=GameStateOut)
def get_game_state(game_id: str, db: Session = Depends(get_db)) -> GameStateOut:
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="game not found")
    return build_game_state(db, game)


@app.get("/games/{game_id}/events", response_model=EventsResponse)
def get_game_events(
    game_id: str,
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
    db: Session = Depends(get_db),
) -> EventsResponse:
    game = db.get(Game, game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="game not found")

    rows = db.execute(
        select(GameEvent)
        .where(GameEvent.game_id == game_id, GameEvent.cursor > after)
        .order_by(GameEvent.cursor.asc())
        .limit(limit + 1)
    ).scalars().all()

    chunk = rows[:limit]
    next_cursor = chunk[-1].cursor if len(rows) > limit and chunk else None
    return EventsResponse(
        items=[to_event_out(row) for row in chunk],
        nextCursor=next_cursor,
    )


@app.get("/team-records/{team_id}", response_model=TeamRecordOut)
def get_team_record_by_team(
    team_id: str,
    category_id: str = Query(default="kbo", alias="categoryId"),
    season_code: str | None = Query(default=None, alias="seasonCode"),
    db: Session = Depends(get_db),
) -> TeamRecordOut:
    normalized_season_code = (season_code or str(datetime.now(UTC).year)).strip()
    row = get_team_record(
        db,
        category_id=category_id.strip(),
        season_code=normalized_season_code,
        team_id=team_id.strip(),
    )
    if row is None:
        raise HTTPException(status_code=404, detail="team record not found")
    return to_team_record_out(row)


@app.post("/internal/crawler/games/{game_id}/snapshot", response_model=IngestResult)
def ingest_crawler_snapshot(
    game_id: str,
    payload: CrawlerSnapshotRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> IngestResult:
    if x_api_key is None or not secrets.compare_digest(x_api_key, settings.crawler_api_key):
        raise HTTPException(status_code=401, detail="invalid crawler api key")

    game: Game | None = None
    inserted_events: list[GameEvent] = []
    duplicate_count = 0
    state_payload: dict[str, Any] | None = None
    inserted_event_payload: list[dict[str, Any]] = []
    response_status: GameStatus | None = None
    response_updated_at: datetime | None = None

    for attempt in range(len(SNAPSHOT_INGEST_RETRY_DELAYS_SECONDS) + 1):
        try:
            game = upsert_game_from_snapshot(db, game_id=game_id, payload=payload)
            inserted_events, duplicate_count = insert_events(
                db,
                game_id=game_id,
                events=payload.events,
                fallback_pitcher=payload.pitcher,
                fallback_batter=payload.batter,
            )
            sync_snapshot_details(db, game_id=game_id, payload=payload)
            current_state = build_game_state(db, game)
            current_state_payload = current_state.model_dump(mode="json")
            current_event_payload = [to_event_out(item).model_dump(mode="json") for item in inserted_events]
            response_status = normalize_status(game.status)
            response_updated_at = game.updated_at
            db.commit()
            state_payload = current_state_payload
            inserted_event_payload = current_event_payload
            break
        except DBAPIError as exc:
            rollback_ok = _rollback_session_safely(db, game_id=game_id, attempt=attempt + 1)
            if not rollback_ok:
                raise HTTPException(status_code=503, detail="snapshot ingest busy; retry shortly") from exc
            if not _is_snapshot_lock_timeout(exc):
                raise

            if attempt >= len(SNAPSHOT_INGEST_RETRY_DELAYS_SECONDS):
                logger.error("snapshot ingest lock timeout exhausted: game_id=%s", game_id)
                raise HTTPException(status_code=503, detail="snapshot ingest busy; retry shortly") from exc

            delay = SNAPSHOT_INGEST_RETRY_DELAYS_SECONDS[attempt]
            logger.warning(
                "snapshot ingest lock timeout; retrying game_id=%s attempt=%s delay=%.1fs",
                game_id,
                attempt + 1,
                delay,
            )
            time.sleep(delay)

    if game is None or state_payload is None or response_status is None or response_updated_at is None:
        raise HTTPException(status_code=500, detail="snapshot ingest failed")

    if inserted_event_payload:
        background_tasks.add_task(
            _broadcast_live_message,
            game_id,
            {
                "type": "events",
                "payload": {"items": inserted_event_payload},
            },
        )
    background_tasks.add_task(
        _broadcast_live_message,
        game_id,
        {"type": "state", "payload": state_payload},
    )

    return IngestResult(
        gameId=game.id,
        receivedEvents=len(payload.events),
        insertedEvents=len(inserted_event_payload),
        duplicateEvents=duplicate_count,
        status=response_status,
        updatedAt=response_updated_at,
    )


@app.post("/internal/crawler/team-records", response_model=TeamRecordIngestResult)
def ingest_crawler_team_records(
    payload: CrawlerTeamRecordRequest,
    db: Session = Depends(get_db),
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> TeamRecordIngestResult:
    if x_api_key is None or not secrets.compare_digest(x_api_key, settings.crawler_api_key):
        raise HTTPException(status_code=401, detail="invalid crawler api key")

    upserted_records = upsert_team_records(db, payload)
    db.commit()
    return TeamRecordIngestResult(
        categoryId=payload.categoryId,
        seasonCode=payload.seasonCode,
        receivedRecords=len(payload.records),
        upsertedRecords=upserted_records,
        updatedAt=datetime.now(UTC),
    )


@app.websocket("/ws/games/{game_id}")
async def websocket_game_stream(websocket: WebSocket, game_id: str) -> None:
    await event_bus.connect(game_id, websocket)

    with SessionLocal() as db:
        game = db.get(Game, game_id)
        if game is not None:
            state = build_game_state(db, game)
            await websocket.send_json({"type": "state", "payload": state.model_dump(mode="json")})

            recent_events = db.execute(
                select(GameEvent).where(GameEvent.game_id == game_id).order_by(GameEvent.cursor.desc()).limit(20)
            ).scalars().all()
            if recent_events:
                payload = [to_event_out(event).model_dump(mode="json") for event in reversed(recent_events)]
                await websocket.send_json({"type": "events", "payload": {"items": payload}})

    try:
        while True:
            await websocket.receive_text()
            await websocket.send_json({"type": "pong", "payload": {"at": datetime.now(UTC).isoformat()}})
    except WebSocketDisconnect:
        await event_bus.disconnect(game_id, websocket)
