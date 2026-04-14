from contextlib import asynccontextmanager
from collections import defaultdict
from datetime import UTC, date, datetime
from typing import Annotated, Any
import asyncio
import httpx
import jwt
import logging
import secrets
import time
import threading

from fastapi import BackgroundTasks, Depends, FastAPI, Header, HTTPException, Query, Request, WebSocket, WebSocketDisconnect
from fastapi.encoders import jsonable_encoder
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import and_, or_, select
from sqlalchemy.exc import DBAPIError
from sqlalchemy.orm import Session

from .config import get_settings
from .db import SessionLocal, get_db, init_db
from .event_bus import event_bus
from .models import DeviceToken, Game, GameEvent, LiveActivityToken
from .redis_bus import RedisBroadcastRelay
from .apns import send_live_activity_push, send_push, send_push_to_tokens
from .schemas import (
    CrawlerSnapshotRequest,
    CrawlerTeamRecordRequest,
    DeviceTokenRequest,
    LiveActivityTokenRequest,
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
logging.basicConfig(
    level=logging.WARNING,
    format="%(asctime)s %(levelname)s %(name)s [%(process)d] %(message)s",
    force=True,
)
logger = logging.getLogger(__name__)
_snapshot_ingest_locks: dict[str, threading.Lock] = defaultdict(threading.Lock)

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


async def _cache_game_data(
    game_id: str,
    state_payload: dict[str, Any],
    new_events_payload: list[dict[str, Any]],
) -> None:
    await redis_relay.set_cache(f"game:state:{game_id}", state_payload)
    if new_events_payload:
        # Merge new events into existing cache (keep latest 20)
        existing = await redis_relay.get_cache(f"game:events:{game_id}")
        existing_items = (existing.get("items") if existing else None) or []
        merged = existing_items + new_events_payload
        await redis_relay.set_cache(f"game:events:{game_id}", {"items": merged[-20:]})


def _team_record_channel(*, category_id: str, season_code: str, team_id: str) -> str:
    normalized_category = category_id.strip().lower()
    normalized_season = season_code.strip()
    normalized_team = team_id.strip().upper()
    return f"team-record:{normalized_category}:{normalized_season}:{normalized_team}"


def _team_record_message(*, row: TeamRecordOut) -> dict[str, Any]:
    return {
        "type": "team_record",
        "payload": row.model_dump(mode="json"),
    }


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


@app.get("/debug/relay-stats")
async def debug_relay_stats() -> dict[str, Any]:
    import os as _os
    import socket as _socket
    return {
        "pid": _os.getpid(),
        "hostname": _socket.gethostname(),
        "source_instance_id": redis_relay._source_instance_id,
        "redis_subscribed_at": redis_relay.subscribed_at,
        "redis_stats": dict(redis_relay.stats),
        "event_bus_stats": event_bus.snapshot_stats(),
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


# MARK: - Account Deletion

def _extract_user_id_from_token(authorization: str) -> str:
    """JWT에서 user_id(sub)를 추출. 서명 검증은 Supabase가 담당."""
    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = jwt.decode(token, options={"verify_signature": False})
        user_id = payload.get("sub")
        if not user_id:
            raise HTTPException(status_code=401, detail="invalid token: missing sub")
        return user_id
    except jwt.DecodeError:
        raise HTTPException(status_code=401, detail="invalid token")


@app.delete("/account")
async def delete_account(
    request: Request,
    authorization: Annotated[str | None, Header()] = None,
) -> dict[str, str]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization header required")

    user_id = _extract_user_id_from_token(authorization)

    if not settings.supabase_url or not settings.supabase_service_role_key:
        raise HTTPException(status_code=500, detail="account deletion not configured")

    async with httpx.AsyncClient() as client:
        resp = await client.delete(
            f"{settings.supabase_url}/auth/v1/admin/users/{user_id}",
            headers={
                "apikey": settings.supabase_service_role_key,
                "Authorization": f"Bearer {settings.supabase_service_role_key}",
            },
        )

    if resp.status_code >= 400 and resp.status_code != 404:
        logger.error("supabase delete user failed: status=%s body=%s", resp.status_code, resp.text)
        raise HTTPException(status_code=502, detail="failed to delete account")

    return {"status": "ok"}


# MARK: - Device Token (APNs)

@app.post("/device-tokens")
def register_device_token(
    payload: DeviceTokenRequest,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    existing = db.execute(
        select(DeviceToken).where(
            DeviceToken.token == payload.token,
            DeviceToken.game_id == payload.game_id,
        )
    ).scalar_one_or_none()

    if existing:
        existing.my_team = payload.my_team
        existing.is_sandbox = payload.is_sandbox
    else:
        db.add(DeviceToken(
            token=payload.token,
            game_id=payload.game_id,
            my_team=payload.my_team,
            platform=payload.platform,
            is_sandbox=payload.is_sandbox,
        ))
    db.commit()
    return {"status": "ok"}


@app.delete("/device-tokens/{token}")
def unregister_device_token(
    token: str,
    game_id: str = Query(...),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    db.execute(
        select(DeviceToken).where(
            DeviceToken.token == token,
            DeviceToken.game_id == game_id,
        )
    )
    row = db.execute(
        select(DeviceToken).where(
            DeviceToken.token == token,
            DeviceToken.game_id == game_id,
        )
    ).scalar_one_or_none()
    if row:
        db.delete(row)
        db.commit()
    return {"status": "ok"}


@app.post("/live-activity-tokens")
def register_live_activity_token(
    payload: LiveActivityTokenRequest,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    existing = db.execute(
        select(LiveActivityToken).where(
            LiveActivityToken.game_id == payload.game_id,
            LiveActivityToken.token == payload.token,
        )
    ).scalar_one_or_none()

    if existing:
        existing.my_team = payload.my_team
    else:
        db.add(LiveActivityToken(
            token=payload.token,
            game_id=payload.game_id,
            my_team=payload.my_team,
        ))
    db.commit()
    return {"status": "ok"}


@app.delete("/live-activity-tokens")
def unregister_live_activity_token(
    game_id: str = Query(...),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    rows = db.execute(
        select(LiveActivityToken).where(LiveActivityToken.game_id == game_id)
    ).scalars().all()
    for row in rows:
        db.delete(row)
    db.commit()
    return {"status": "ok"}


def _load_push_tokens(game_id: str) -> list[tuple[str, str | None, bool, str]]:
    """게임에 구독된 디바이스 토큰 목록 조회 (sync DB work)"""
    with SessionLocal() as db:
        rows = db.execute(
            select(DeviceToken.token, DeviceToken.my_team, DeviceToken.is_sandbox, DeviceToken.platform)
            .where(DeviceToken.game_id == game_id)
        ).all()
        return [(row.token, row.my_team, row.is_sandbox, row.platform) for row in rows]


async def _send_push_for_game_events(
    game_id: str,
    state_payload: dict[str, Any],
    event_payloads: list[dict[str, Any]],
) -> None:
    """게임 이벤트 발생 시 구독된 디바이스에 silent push 전송"""
    token_rows = await asyncio.to_thread(_load_push_tokens, game_id)
    if not token_rows:
        return

    token_info = {t[0]: {"my_team": t[1], "is_sandbox": t[2], "platform": t[3]} for t in token_rows}
    tokens = list(token_info.keys())

    # 이벤트가 있으면 각 이벤트에 대해 push, 없으면 상태 업데이트만
    base_payload = {
        "game_id": state_payload.get("gameId", game_id),
        "home_team": state_payload.get("homeTeam", ""),
        "away_team": state_payload.get("awayTeam", ""),
        "home_score": state_payload.get("homeScore", 0),
        "away_score": state_payload.get("awayScore", 0),
        "status": state_payload.get("status", ""),
        "inning": state_payload.get("inning", ""),
        "ball": state_payload.get("ball", 0),
        "strike": state_payload.get("strike", 0),
        "out": state_payload.get("out", 0),
        "base_first": state_payload.get("bases", {}).get("first", False),
        "base_second": state_payload.get("bases", {}).get("second", False),
        "base_third": state_payload.get("bases", {}).get("third", False),
        "pitcher": state_payload.get("pitcher", ""),
        "batter": state_payload.get("batter", ""),
    }

    if event_payloads:
        for event in event_payloads:
            push_payload = {
                **base_payload,
                "event_type": event.get("type", ""),
                "event_cursor": event.get("cursor", 0),
            }
            for token in tokens:
                info = token_info[token]
                payload_with_team = {**push_payload, "my_team": info["my_team"] or ""}
                await send_push(token, payload_with_team, use_sandbox=info["is_sandbox"], platform=info["platform"])
    else:
        for token in tokens:
            info = token_info[token]
            payload_with_team = {**base_payload, "my_team": info["my_team"] or ""}
            await send_push(token, payload_with_team, use_sandbox=info["is_sandbox"], platform=info["platform"])


def _load_live_activity_tokens(game_id: str) -> list[str]:
    """게임에 구독된 Live Activity 토큰 목록 조회"""
    with SessionLocal() as db:
        rows = db.execute(
            select(LiveActivityToken.token).where(LiveActivityToken.game_id == game_id)
        ).all()
        return [row.token for row in rows]


async def _send_live_activity_update(
    game_id: str,
    state_payload: dict[str, Any],
    event_type: str = "update",
) -> None:
    """Live Activity push로 잠금화면 업데이트"""
    tokens = await asyncio.to_thread(_load_live_activity_tokens, game_id)
    if not tokens:
        return

    last_event_type = state_payload.get("lastEventType")
    content_state = {
        "homeScore": state_payload.get("homeScore", 0),
        "awayScore": state_payload.get("awayScore", 0),
        "inning": state_payload.get("inning", ""),
        "ball": state_payload.get("ball", 0),
        "strike": state_payload.get("strike", 0),
        "out": state_payload.get("out", 0),
        "baseFirst": state_payload.get("bases", {}).get("first", False),
        "baseSecond": state_payload.get("bases", {}).get("second", False),
        "baseThird": state_payload.get("bases", {}).get("third", False),
        "pitcher": state_payload.get("pitcher", "") or "",
        "batter": state_payload.get("batter", "") or "",
        "status": state_payload.get("status", "LIVE"),
        "lastEventType": last_event_type,
    }

    for token in tokens:
        await send_live_activity_push(token, content_state, event_type=event_type)


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

    game_lock = _snapshot_ingest_locks[game_id]
    with game_lock:
        return _ingest_crawler_snapshot_locked(game_id=game_id, payload=payload, background_tasks=background_tasks, db=db)


def _ingest_crawler_snapshot_locked(
    *,
    game_id: str,
    payload: CrawlerSnapshotRequest,
    background_tasks: BackgroundTasks,
    db: Session,
) -> IngestResult:
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

    background_tasks.add_task(
        _broadcast_live_message,
        game_id,
        {
            "type": "update",
            "payload": {
                "state": state_payload,
                "events": inserted_event_payload,
            },
        },
    )

    # Cache state and recent events in Redis for fast WS on-connect
    background_tasks.add_task(
        _cache_game_data, game_id, state_payload, inserted_event_payload,
    )

    # APNs silent push 전송 (백그라운드에서도 워치로 이벤트 전달)
    if inserted_event_payload:
        background_tasks.add_task(
            _send_push_for_game_events, game_id, state_payload, inserted_event_payload,
        )

    # Live Activity push 전송 (잠금화면 실시간 업데이트)
    la_event = "end" if state_payload.get("status") == "FINISHED" else "update"
    if inserted_event_payload:
        state_payload_with_event = {**state_payload, "lastEventType": inserted_event_payload[-1].get("type")}
    else:
        state_payload_with_event = {**state_payload, "lastEventType": None}
    background_tasks.add_task(
        _send_live_activity_update, game_id, state_payload_with_event, la_event,
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
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> TeamRecordIngestResult:
    if x_api_key is None or not secrets.compare_digest(x_api_key, settings.crawler_api_key):
        raise HTTPException(status_code=401, detail="invalid crawler api key")

    upsert_result = upsert_team_records(db, payload)
    db.commit()

    for row in upsert_result.changed_records:
        channel = _team_record_channel(
            category_id=row.category_id,
            season_code=row.season_code,
            team_id=row.team_id,
        )
        message = _team_record_message(row=to_team_record_out(row))
        background_tasks.add_task(_broadcast_live_message, channel, message)

    return TeamRecordIngestResult(
        categoryId=payload.categoryId,
        seasonCode=payload.seasonCode,
        receivedRecords=len(payload.records),
        upsertedRecords=upsert_result.upserted_records,
        updatedAt=datetime.now(UTC),
    )


def _load_game_initial_data(game_id: str) -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    """Sync DB work for WS on-connect, intended to run via asyncio.to_thread."""
    with SessionLocal() as db:
        game = db.get(Game, game_id)
        if game is None:
            return None, []
        state_payload = build_game_state(db, game).model_dump(mode="json")
        recent_events = db.execute(
            select(GameEvent).where(GameEvent.game_id == game_id).order_by(GameEvent.cursor.desc()).limit(20)
        ).scalars().all()
        events_payload = [to_event_out(event).model_dump(mode="json") for event in reversed(recent_events)]
    return state_payload, events_payload


async def _load_game_initial_data_cached(game_id: str) -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    """Try Redis cache first, fall back to DB."""
    state = await redis_relay.get_cache(f"game:state:{game_id}")
    events_data = await redis_relay.get_cache(f"game:events:{game_id}")
    if state is not None:
        events = (events_data.get("items") if events_data else None) or []
        return state, events
    # Cache miss → fall back to DB
    return await asyncio.to_thread(_load_game_initial_data, game_id)


@app.websocket("/ws/games/{game_id}")
async def websocket_game_stream(websocket: WebSocket, game_id: str) -> None:
    await event_bus.connect(websocket)
    await event_bus.register(game_id, websocket)

    state_payload, events_payload = await _load_game_initial_data_cached(game_id)
    if state_payload is not None:
        await event_bus.safe_send(websocket, {"type": "state", "payload": state_payload})
    if events_payload:
        await event_bus.safe_send(
            websocket, {"type": "events", "payload": {"items": events_payload}}
        )

    try:
        while True:
            await websocket.receive_text()
            await event_bus.safe_send(
                websocket,
                {"type": "pong", "payload": {"at": datetime.now(UTC).isoformat()}},
            )
    except WebSocketDisconnect:
        await event_bus.disconnect(game_id, websocket)


def _load_team_record_initial_data(
    category_id: str, season_code: str, team_id: str,
) -> dict[str, Any] | None:
    """Sync DB work for team-record WS on-connect, intended to run via asyncio.to_thread."""
    with SessionLocal() as db:
        row = get_team_record(db, category_id=category_id, season_code=season_code, team_id=team_id)
        if row is None:
            return None
        return _team_record_message(row=to_team_record_out(row))


async def _load_team_record_initial_data_cached(
    category_id: str, season_code: str, team_id: str,
) -> dict[str, Any] | None:
    """Try Redis cache first, fall back to DB."""
    cache_key = f"team-record:{category_id}:{season_code}:{team_id}"
    cached = await redis_relay.get_cache(cache_key)
    if cached is not None:
        return cached
    return await asyncio.to_thread(
        _load_team_record_initial_data, category_id, season_code, team_id,
    )


@app.websocket("/ws/team-records/{team_id}")
async def websocket_team_record_stream(
    websocket: WebSocket,
    team_id: str,
    category_id: str = Query(default="kbo", alias="categoryId"),
    season_code: str | None = Query(default=None, alias="seasonCode"),
) -> None:
    normalized_category_id = category_id.strip().lower()
    normalized_season_code = (season_code or str(datetime.now(UTC).year)).strip()
    normalized_team_id = team_id.strip().upper()
    channel = _team_record_channel(
        category_id=normalized_category_id,
        season_code=normalized_season_code,
        team_id=normalized_team_id,
    )
    await event_bus.connect(websocket)
    await event_bus.register(channel, websocket)

    message = await _load_team_record_initial_data_cached(
        normalized_category_id, normalized_season_code, normalized_team_id,
    )
    if message is not None:
        await event_bus.safe_send(websocket, message)

    try:
        while True:
            await websocket.receive_text()
            await event_bus.safe_send(
                websocket,
                {"type": "pong", "payload": {"at": datetime.now(UTC).isoformat()}},
            )
    except WebSocketDisconnect:
        await event_bus.disconnect(channel, websocket)
