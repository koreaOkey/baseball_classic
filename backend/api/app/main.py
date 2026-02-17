from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Annotated
import secrets

from fastapi import Depends, FastAPI, Header, HTTPException, Query, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .db import SessionLocal, get_db, init_db
from .event_bus import event_bus
from .models import Game, GameEvent
from .schemas import CrawlerSnapshotRequest, EventsResponse, GameStateOut, GameStatus, GameSummaryOut, IngestResult
from .services import build_game_state, insert_events, normalize_status, to_event_out, to_game_summary, upsert_game_from_snapshot


settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=settings.cors_origins != ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "service": "backend-api",
        "environment": settings.environment,
        "time": datetime.now(UTC),
    }


@app.get("/games", response_model=list[GameSummaryOut])
def list_games(
    status: GameStatus | None = None,
    limit: int = Query(default=20, ge=1, le=100),
    db: Session = Depends(get_db),
) -> list[GameSummaryOut]:
    query = select(Game).order_by(Game.updated_at.desc()).limit(limit)
    if status is not None:
        query = query.where(Game.status == status.value)

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


@app.post("/internal/crawler/games/{game_id}/snapshot", response_model=IngestResult)
async def ingest_crawler_snapshot(
    game_id: str,
    payload: CrawlerSnapshotRequest,
    db: Session = Depends(get_db),
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> IngestResult:
    if x_api_key is None or not secrets.compare_digest(x_api_key, settings.crawler_api_key):
        raise HTTPException(status_code=401, detail="invalid crawler api key")

    game = upsert_game_from_snapshot(db, game_id=game_id, payload=payload)
    inserted_events, duplicate_count = insert_events(db, game_id=game_id, events=payload.events)
    db.commit()
    db.refresh(game)
    state = build_game_state(db, game)

    if inserted_events:
        await event_bus.broadcast(
            game_id,
            {
                "type": "events",
                "payload": {"items": [to_event_out(item).model_dump(mode="json") for item in inserted_events]},
            },
        )
    await event_bus.broadcast(
        game_id,
        {"type": "state", "payload": state.model_dump(mode="json")},
    )

    return IngestResult(
        gameId=game.id,
        receivedEvents=len(payload.events),
        insertedEvents=len(inserted_events),
        duplicateEvents=duplicate_count,
        status=normalize_status(game.status),
        updatedAt=game.updated_at,
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
