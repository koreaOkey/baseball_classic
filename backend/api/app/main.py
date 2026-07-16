from contextlib import asynccontextmanager
from collections import OrderedDict, defaultdict
from datetime import UTC, date, datetime, timedelta
from typing import Annotated, Any, Callable
import asyncio
import hashlib
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
from sqlalchemy import and_, delete, func, or_, select
from sqlalchemy.exc import DBAPIError, IntegrityError, SQLAlchemyError
from sqlalchemy.orm import Session

from .config import get_settings
from .cheer_signals import build_cheer_signals, stadium_payloads
from .db import (
    DB_UNAVAILABLE_BACKOFF_SEC,
    SessionLocal,
    assert_db_available,
    clear_db_unavailable,
    db_unavailable_remaining_sec,
    get_db,
    init_db,
    mark_db_unavailable,
)
from .event_bus import event_bus
from .models import (
    AppConfig,
    CheerEvent,
    DeviceToken,
    Game,
    GameBatterStat,
    GameEvent,
    GameLineupSlot,
    GameNote,
    GamePitcherStat,
    LiveViewSession,
    LiveActivityToken,
    TeamCheckinDaily,
    TeamCheckinSeason,
    TeamSubscriptionToken,
    UserCheckinDaily,
    UserCheckinSeason,
)
from .redis_bus import RedisBroadcastRelay
from .apns import (
    send_live_activity_push_with_result,
    send_push_with_result,
    send_visible_push_to_tokens_detailed as send_apns_visible_push_to_tokens_detailed,
)
from .fcm import send_visible_push_to_tokens_detailed as send_fcm_visible_push_to_tokens_detailed
from .schemas import (
    AppConfigOut,
    AppNoticeOut,
    CrawlerSnapshotRequest,
    CrawlerTeamRecordRequest,
    DeviceTokenRequest,
    LiveViewSessionRequest,
    LiveActivityTokenRequest,
    EventsResponse,
    GameStateOut,
    GameStatus,
    GameSummaryOut,
    GameWeatherHourlyOut,
    IngestResult,
    TeamRecordIngestResult,
    TeamRecordOut,
    TeamSubscriptionRequest,
)
from .services import (
    build_game_state,
    get_team_record,
    get_team_records,
    insert_events,
    normalize_status,
    sync_snapshot_details,
    to_team_record_out,
    to_event_out,
    to_game_summary,
    upsert_team_records,
    upsert_game_from_snapshot,
)
from .weather import KST, build_hourly_weather, build_weather_summary
from .workers.cheer_validator import validate_pending_cheer_events


settings = get_settings()
logging.basicConfig(
    level=logging.WARNING,
    format="%(asctime)s %(levelname)s %(name)s [%(process)d] %(message)s",
    force=True,
)
logger = logging.getLogger(__name__)

# 락 레지스트리는 무한히 자라지 않도록 LRU 캡을 둔다 (오래되고 미사용인 락부터 축출).
_MAX_LOCK_REGISTRY_ENTRIES = 512
_snapshot_ingest_locks: OrderedDict[str, threading.Lock] = OrderedDict()
_snapshot_ingest_locks_guard = threading.Lock()


def _evict_stale_locks(registry: OrderedDict[str, Any], *, keep_key: str) -> None:
    """캡 초과 시 현재 잡혀 있지 않은 가장 오래된 락부터 제거."""
    while len(registry) > _MAX_LOCK_REGISTRY_ENTRIES:
        evicted = False
        for old_key, old_lock in list(registry.items()):
            if old_key == keep_key:
                continue
            if not old_lock.locked():
                registry.pop(old_key, None)
                evicted = True
                break
        if not evicted:
            break


def _get_snapshot_ingest_lock(game_id: str) -> threading.Lock:
    with _snapshot_ingest_locks_guard:
        lock = _snapshot_ingest_locks.get(game_id)
        if lock is None:
            lock = threading.Lock()
            _snapshot_ingest_locks[game_id] = lock
        else:
            _snapshot_ingest_locks.move_to_end(game_id)
        _evict_stale_locks(_snapshot_ingest_locks, keep_key=game_id)
        return lock

SNAPSHOT_INGEST_RETRY_DELAYS_SECONDS = (0.2, 0.5, 1.0)
redis_relay = RedisBroadcastRelay(
    redis_url=settings.redis_url,
    channel=settings.redis_pubsub_channel,
    source_instance_id=settings.instance_id,
)

HTTP_LIVE_CACHE_TTL_SEC = 5
HTTP_STANDINGS_CACHE_TTL_SEC = 30
HTTP_STALE_CACHE_TTL_SEC = 300
# 오늘이 포함되지 않은 일정 범위(전부 과거/전부 미래)는 크롤러 임포트 때만 바뀌므로 길게 캐시한다.
HTTP_SCHEDULE_RANGE_CACHE_TTL_SEC = 600
DB_UNAVAILABLE_CACHE_KEY = "ops:db_unavailable:v1"
DB_UNAVAILABLE_CACHE_TTL_SEC = 24 * 60 * 60
DEFAULT_STORE_URLS = {
    "android": "market://details?id=com.basehaptic.mobile",
    "ios": "itms-apps://itunes.apple.com/app/id6761336752",
}

_http_cache_locks: OrderedDict[str, asyncio.Lock] = OrderedDict()
_http_cache_stats: dict[str, int] = defaultdict(int)


def _get_http_cache_lock(lock_key: str) -> asyncio.Lock:
    # 이벤트 루프에서만 접근하므로 별도 가드 없이 LRU 캡만 적용.
    lock = _http_cache_locks.get(lock_key)
    if lock is None:
        lock = asyncio.Lock()
        _http_cache_locks[lock_key] = lock
    else:
        _http_cache_locks.move_to_end(lock_key)
    _evict_stale_locks(_http_cache_locks, keep_key=lock_key)
    return lock
try:
    _BASE_EXCEPTION_GROUP_TYPES = (BaseExceptionGroup,)
except NameError:
    _BASE_EXCEPTION_GROUP_TYPES = ()


def _stale_http_cache_key(cache_key: str) -> str:
    return f"{cache_key}:stale"


async def _redis_db_unavailable_remaining_sec(*, hold_expired: bool = False) -> float:
    payload = await redis_relay.get_cache(DB_UNAVAILABLE_CACHE_KEY)
    if payload is None:
        return 0.0
    try:
        until = float(payload.get("until") or 0)
    except (TypeError, ValueError):
        await redis_relay.delete_cache(DB_UNAVAILABLE_CACHE_KEY)
        return 0.0

    remaining = until - time.time()
    if remaining <= 0:
        await redis_relay.delete_cache(DB_UNAVAILABLE_CACHE_KEY)
        return 0.0
    return remaining


async def _mark_db_unavailable_global(backoff_sec: float = DB_UNAVAILABLE_BACKOFF_SEC) -> None:
    mark_db_unavailable(backoff_sec)
    await redis_relay.set_cache(
        DB_UNAVAILABLE_CACHE_KEY,
        {"until": time.time() + max(1.0, backoff_sec)},
        ttl_sec=DB_UNAVAILABLE_CACHE_TTL_SEC,
    )


async def _clear_db_unavailable_global() -> None:
    clear_db_unavailable()
    await redis_relay.delete_cache(DB_UNAVAILABLE_CACHE_KEY)


async def _db_unavailable_remaining_sec_global(*, hold_expired: bool = False) -> float:
    redis_remaining = await _redis_db_unavailable_remaining_sec(hold_expired=hold_expired)
    redis_connected = bool(
        getattr(redis_relay, "enabled", False)
        and getattr(redis_relay, "_publisher", None) is not None
    )
    if redis_connected and redis_remaining <= 0:
        clear_db_unavailable()
        return 0.0
    return max(db_unavailable_remaining_sec(), redis_remaining)


def _db_unavailable_response(remaining: float) -> JSONResponse:
    return JSONResponse(
        status_code=503,
        content={"detail": f"database temporarily unavailable; retry in {int(remaining) + 1}s"},
    )


def _is_database_failure(exc: BaseException) -> bool:
    if isinstance(exc, IntegrityError):
        return False

    if isinstance(exc, SQLAlchemyError):
        return True

    if _BASE_EXCEPTION_GROUP_TYPES and isinstance(exc, _BASE_EXCEPTION_GROUP_TYPES):
        return any(_is_database_failure(inner) for inner in exc.exceptions)

    return False


async def _set_http_cache_payload(cache_key: str, payload: dict[str, Any], ttl_sec: int) -> None:
    await redis_relay.set_cache(cache_key, payload, ttl_sec=ttl_sec)
    await redis_relay.set_cache(_stale_http_cache_key(cache_key), payload, ttl_sec=HTTP_STALE_CACHE_TTL_SEC)


async def _delete_http_cache_payload(cache_key: str, *, delete_stale: bool = False) -> None:
    await redis_relay.delete_cache(cache_key)
    if delete_stale:
        await redis_relay.delete_cache(_stale_http_cache_key(cache_key))


async def _get_or_set_http_cache_payload(
    *,
    cache_key: str,
    ttl_sec: int,
    loader: Callable[[], dict[str, Any]],
    lock_key: str | None = None,
) -> dict[str, Any]:
    cached = await redis_relay.get_cache(cache_key)
    if cached is not None:
        _http_cache_stats["hit"] += 1
        return cached

    _http_cache_stats["miss"] += 1
    lock = _get_http_cache_lock(lock_key or cache_key)
    async with lock:
        cached = await redis_relay.get_cache(cache_key)
        if cached is not None:
            _http_cache_stats["hit_after_wait"] += 1
            return cached

        try:
            payload = await asyncio.to_thread(loader)
        except HTTPException:
            raise
        except Exception:
            stale = await redis_relay.get_cache(_stale_http_cache_key(cache_key))
            if stale is not None:
                _http_cache_stats["stale_hit"] += 1
                logger.warning("serving stale http cache payload: key=%s", cache_key)
                return stale
            _http_cache_stats["loader_error"] += 1
            raise

        await _set_http_cache_payload(cache_key, payload, ttl_sec)
        _http_cache_stats["set"] += 1
        return payload


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


# 만료 푸시 데이터 정리 주기 (일 1회) 및 보존 기간
PUSH_DATA_PURGE_INITIAL_DELAY_SEC = 120
PURGE_RUN_HOUR_KST = 4          # 경기·크롤러와 겹치지 않는 새벽 시간대
LIVE_ROW_MAX_AGE_DAYS = 7       # live_activity_tokens / live_view_sessions
DEVICE_TOKEN_MAX_AGE_DAYS = 90  # device_tokens / team_subscription_tokens

# 경기 상세 데이터 보존 기간 — 지나면 이벤트/라인업/스탯/노트를 삭제한다.
# games 행(스코어·결과)은 계속 보존하며, 클라이언트는 라이브 중·직후에만
# 이벤트를 조회하므로 사용자 기능에는 영향이 없다.
GAME_DATA_RETENTION_DAYS = 7
GAME_DATA_PURGE_BATCH_ROWS = 10_000   # 배치당 삭제 행 상한 (짧은 트랜잭션 유지)
GAME_DATA_PURGE_BATCH_PAUSE_SEC = 0.5


def _seconds_until_next_kst_hour(hour: int) -> float:
    now = datetime.now(KST)
    target = now.replace(hour=hour, minute=0, second=0, microsecond=0)
    if target <= now:
        target += timedelta(days=1)
    return (target - now).total_seconds()


def _purge_stale_push_rows() -> dict[str, int]:
    """오래된 푸시/관람 데이터 삭제 (모든 대상 테이블에 updated_at 존재)."""
    assert_db_available()
    now = datetime.now(UTC)
    live_cutoff = now - timedelta(days=LIVE_ROW_MAX_AGE_DAYS)
    token_cutoff = now - timedelta(days=DEVICE_TOKEN_MAX_AGE_DAYS)
    deleted: dict[str, int] = {}
    with SessionLocal() as db:
        deleted["live_activity_tokens"] = int(db.execute(
            delete(LiveActivityToken).where(LiveActivityToken.updated_at < live_cutoff)
        ).rowcount or 0)
        deleted["live_view_sessions"] = int(db.execute(
            delete(LiveViewSession).where(LiveViewSession.updated_at < live_cutoff)
        ).rowcount or 0)
        deleted["device_tokens"] = int(db.execute(
            delete(DeviceToken).where(DeviceToken.updated_at < token_cutoff)
        ).rowcount or 0)
        deleted["team_subscription_tokens"] = int(db.execute(
            delete(TeamSubscriptionToken).where(TeamSubscriptionToken.updated_at < token_cutoff)
        ).rowcount or 0)
        db.commit()
    return deleted


def _purge_expired_game_rows() -> dict[str, int]:
    """보존 기간이 지난 경기의 상세 행(이벤트/라인업/스탯/노트)을 배치 삭제한다.

    라이브 경기와 game_id 가 겹치지 않는 행만 대상이라 행 잠금 경합이 없고,
    배치 단위로 커밋해 트랜잭션을 짧게 유지한다. games 행은 삭제하지 않는다.
    """
    assert_db_available()
    cutoff_date = (datetime.now(KST) - timedelta(days=GAME_DATA_RETENTION_DAYS)).date().isoformat()
    cutoff_ts = datetime.now(UTC) - timedelta(days=GAME_DATA_RETENTION_DAYS)
    deleted: dict[str, int] = {}
    with SessionLocal() as db:
        expired_ids = [
            row[0]
            for row in db.execute(
                select(Game.id).where(
                    or_(
                        Game.game_date < cutoff_date,
                        and_(Game.game_date.is_(None), Game.created_at < cutoff_ts),
                    )
                )
            )
        ]
        if not expired_ids:
            return deleted
        # FK 제약에 맞춘 삭제 순서 (db/migrations/20260302_001 기준):
        # - game_batter_stats → game_lineup_slots 복합 FK (RESTRICT) → batter 를 lineup 보다 먼저
        # - game_notes.event_cursor / game_lineup_slots.*_event_cursor → game_events (SET NULL)
        #   → events 를 맨 마지막에 지워 불필요한 SET NULL 업데이트를 피한다
        targets = (
            (GameNote, GameNote.id),
            (GameBatterStat, GameBatterStat.id),
            (GamePitcherStat, GamePitcherStat.id),
            (GameLineupSlot, GameLineupSlot.id),
            (GameEvent, GameEvent.cursor),
        )
        for model, pk in targets:
            total = 0
            while True:
                batch_ids = select(pk).where(model.game_id.in_(expired_ids)).limit(GAME_DATA_PURGE_BATCH_ROWS)
                count = int(db.execute(delete(model).where(pk.in_(batch_ids))).rowcount or 0)
                db.commit()
                total += count
                if count < GAME_DATA_PURGE_BATCH_ROWS:
                    break
                time.sleep(GAME_DATA_PURGE_BATCH_PAUSE_SEC)
            if total:
                deleted[model.__tablename__] = total
    return deleted


async def _push_data_purge_loop() -> None:
    """앱 수명 동안 하루 1회(KST 04:00) 만료 푸시/경기 데이터를 정리하는 백그라운드 태스크."""
    await asyncio.sleep(PUSH_DATA_PURGE_INITIAL_DELAY_SEC)
    while True:
        try:
            deleted = await asyncio.to_thread(_purge_stale_push_rows)
            if any(deleted.values()):
                logger.info("[push-purge] stale rows deleted: %s", deleted)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.warning("[push-purge] purge failed", exc_info=True)
        try:
            expired = await asyncio.to_thread(_purge_expired_game_rows)
            if expired:
                logger.info("[game-purge] expired game rows deleted: %s", expired)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.warning("[game-purge] purge failed", exc_info=True)
        await asyncio.sleep(_seconds_until_next_kst_hour(PURGE_RUN_HOUR_KST))


@asynccontextmanager
async def lifespan(_: FastAPI):
    if not settings.supabase_jwt_secret:
        # fail closed: 시크릿이 없으면 토큰 인증 요청은 전부 401 로 거부된다.
        logger.critical(
            "SUPABASE_JWT_SECRET is not configured; "
            "token-authenticated endpoints (/account, /cheer-events*) will reject all requests"
        )
    await redis_relay.start(_on_redis_live_message)
    remaining = await _redis_db_unavailable_remaining_sec(hold_expired=True)
    if remaining > 0:
        mark_db_unavailable(remaining)
        logger.warning("startup database initialization skipped during db backoff: remaining_sec=%.1f", remaining)
    else:
        try:
            init_db()
            await _clear_db_unavailable_global()
        except Exception:
            await _mark_db_unavailable_global()
            logger.exception("startup database initialization failed; continuing in degraded mode")
    purge_task = asyncio.create_task(_push_data_purge_loop())
    try:
        yield
    finally:
        purge_task.cancel()
        try:
            await purge_task
        except (asyncio.CancelledError, Exception):
            pass
        await redis_relay.stop()


app = FastAPI(title=settings.app_name, lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=settings.cors_origins != ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def log_unhandled_request_errors(request: Request, call_next):
    started = time.perf_counter()
    if request.url.path not in {"/health", "/ready", "/internal/ops/db-backoff/clear"}:
        remaining = await _db_unavailable_remaining_sec_global(hold_expired=True)
        if remaining > 0:
            return _db_unavailable_response(remaining)
    try:
        return await call_next(request)
    except Exception as exc:
        elapsed_ms = (time.perf_counter() - started) * 1000
        if _is_database_failure(exc):
            await _mark_db_unavailable_global()
            logger.exception(
                "unhandled database request failed: method=%s path=%s elapsed_ms=%.1f",
                request.method,
                request.url.path,
                elapsed_ms,
            )
            raise
        logger.exception(
            "unhandled request failed: method=%s path=%s elapsed_ms=%.1f",
            request.method,
            request.url.path,
            elapsed_ms,
        )
        raise


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
    remaining = await _db_unavailable_remaining_sec_global()
    try:
        with SessionLocal() as db:
            db.execute(select(1)).scalar_one()
        await _clear_db_unavailable_global()
    except Exception as exc:
        if remaining <= 0:
            await _mark_db_unavailable_global()
        logger.warning("ready database probe failed: %s", exc, exc_info=True)
        db_connected = False
        if remaining > 0:
            db_detail = f"backoff:{int(remaining) + 1}s"
        else:
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


@app.post("/internal/ops/db-backoff/clear")
async def clear_db_backoff(
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> dict[str, str]:
    if x_api_key is None or not secrets.compare_digest(x_api_key, settings.crawler_api_key):
        raise HTTPException(status_code=401, detail="invalid api key")
    await _clear_db_unavailable_global()
    return {"status": "ok"}


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


def _app_config_out(row: AppConfig | None, *, platform: str) -> AppConfigOut:
    normalized_platform = platform.strip().lower() or "unknown"
    if row is None:
        return AppConfigOut(
            platform=normalized_platform,
            storeUrl=DEFAULT_STORE_URLS.get(normalized_platform, ""),
        )

    return AppConfigOut(
        platform=normalized_platform,
        minSupportedVersion=row.min_supported_version,
        latestVersion=row.latest_version,
        forceUpdate=row.force_update,
        updateTitle=row.update_title,
        updateMessage=row.update_message,
        storeUrl=row.store_url or DEFAULT_STORE_URLS.get(normalized_platform, ""),
        notice=AppNoticeOut(
            enabled=row.notice_enabled,
            title=row.notice_title,
            message=row.notice_message,
        ),
    )


@app.get("/app-config", response_model=AppConfigOut)
def get_app_config(
    platform: str = Query(default="unknown", min_length=1, max_length=16),
    version: str | None = Query(default=None, max_length=32),
    db: Session = Depends(get_db),
) -> AppConfigOut:
    del version
    normalized_platform = platform.strip().lower()
    row = db.execute(
        select(AppConfig)
        .where(AppConfig.platform.in_([normalized_platform, "all"]))
        .order_by((AppConfig.platform == normalized_platform).desc())
        .limit(1)
    ).scalar_one_or_none()
    return _app_config_out(row, platform=normalized_platform)


@app.get("/debug/relay-stats")
async def debug_relay_stats(
    x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None,
) -> dict[str, Any]:
    if x_api_key is None or not secrets.compare_digest(x_api_key, settings.crawler_api_key):
        raise HTTPException(status_code=401, detail="invalid api key")
    import os as _os
    import socket as _socket
    return {
        "pid": _os.getpid(),
        "hostname": _socket.gethostname(),
        "source_instance_id": redis_relay._source_instance_id,
        "redis_subscribed_at": redis_relay.subscribed_at,
        "redis_stats": dict(redis_relay.stats),
        "http_cache_stats": dict(_http_cache_stats),
        "event_bus_stats": event_bus.snapshot_stats(),
    }


@app.get("/games", response_model=list[GameSummaryOut])
async def list_games(
    status: GameStatus | None = None,
    game_date: date | None = Query(default=None, alias="date"),
    from_date: date | None = Query(default=None, alias="from"),
    to_date: date | None = Query(default=None, alias="to"),
    team: str | None = Query(default=None, min_length=2, max_length=32),
    limit: int = Query(default=20, ge=1, le=500),
) -> list[dict[str, Any]]:
    team_labels: set[str] | None = None
    if team is not None:
        team_labels = _team_filter_labels(team)
        if team_labels is None:
            raise HTTPException(status_code=400, detail="unknown team code")
    cache_key = (
        "http:games:v1:"
        f"status={(status.value if status else '')}:"
        f"date={(game_date.isoformat() if game_date else '')}:"
        f"from={(from_date.isoformat() if from_date else '')}:"
        f"to={(to_date.isoformat() if to_date else '')}:"
        f"team={(team.strip().upper() if team else '')}:"
        f"limit={limit}"
    )
    payload = await _get_or_set_http_cache_payload(
        cache_key=cache_key,
        ttl_sec=_games_list_cache_ttl(game_date=game_date, from_date=from_date, to_date=to_date),
        loader=lambda: {
            "items": _list_games_payload(
                status=status,
                game_date=game_date,
                from_date=from_date,
                to_date=to_date,
                team_labels=team_labels,
                limit=limit,
            )
        },
    )
    return payload.get("items") or []


def _games_list_cache_ttl(*, game_date: date | None, from_date: date | None, to_date: date | None) -> int:
    """오늘(KST)이 포함되지 않는 조회는 일정성 데이터라 캐시를 길게 가져간다."""
    today_kst = datetime.now(KST).date()
    if game_date is not None and game_date != today_kst:
        return HTTP_SCHEDULE_RANGE_CACHE_TTL_SEC
    if from_date is not None and to_date is not None and not (from_date <= today_kst <= to_date):
        return HTTP_SCHEDULE_RANGE_CACHE_TTL_SEC
    return HTTP_LIVE_CACHE_TTL_SEC


def _team_filter_labels(team_code: str) -> set[str] | None:
    """영문 응원팀 코드(DOOSAN 등)를 games 테이블에 저장되는 라벨 집합
    (영문 코드 / 한글 모기업 / 마스코트)으로 확장한다. 미지원 코드는 None."""
    code = team_code.strip().upper()
    club = _TEAM_CODE_TO_CLUB.get(code)
    if club is None:
        return None
    return {code, club, _TEAM_CODE_TO_MASCOT[code]}


def _list_games_payload(
    *,
    status: GameStatus | None,
    game_date: date | None,
    from_date: date | None,
    to_date: date | None,
    limit: int,
    team_labels: set[str] | None = None,
) -> list[dict[str, Any]]:
    assert_db_available()
    query = select(Game)
    if status is not None:
        query = query.where(Game.status == status.value)
    if team_labels:
        labels = sorted(team_labels)
        query = query.where(or_(Game.home_team.in_(labels), Game.away_team.in_(labels)))
    if game_date is not None:
        iso_date = game_date.isoformat()
        prefix = game_date.strftime("%Y%m%d")
        query = query.where(
            or_(
                Game.game_date == iso_date,
                and_(Game.game_date.is_(None), Game.id.like(f"{prefix}%")),
            )
        )
    elif from_date is not None or to_date is not None:
        if from_date is None or to_date is None:
            raise HTTPException(status_code=400, detail="from and to must be provided together")
        if to_date < from_date:
            raise HTTPException(status_code=400, detail="to must be greater than or equal to from")

        from_iso = from_date.isoformat()
        to_iso = to_date.isoformat()
        from_prefix = from_date.strftime("%Y%m%d")
        to_prefix = to_date.strftime("%Y%m%d")
        game_id_date = func.substr(Game.id, 1, 8)
        query = query.where(
            or_(
                and_(Game.game_date >= from_iso, Game.game_date <= to_iso),
                and_(
                    Game.game_date.is_(None),
                    game_id_date >= from_prefix,
                    game_id_date <= to_prefix,
                ),
            )
        )

    if from_date is not None or to_date is not None:
        query = query.order_by(Game.game_date.asc(), Game.start_time.asc(), Game.id.asc()).limit(limit)
    else:
        query = query.order_by(Game.updated_at.desc()).limit(limit)

    with SessionLocal() as db:
        games = db.execute(query).scalars().all()
        return [_game_summary_payload(game, allow_weather_network=False) for game in games]


@app.get("/games/{game_id}", response_model=GameSummaryOut)
async def get_game(game_id: str) -> dict[str, Any]:
    cache_key = f"http:game:v1:{game_id}"
    payload = await _get_or_set_http_cache_payload(
        cache_key=cache_key,
        ttl_sec=HTTP_LIVE_CACHE_TTL_SEC,
        loader=lambda: {"item": _get_game_payload(game_id)},
    )
    if payload.get("item") is not None:
        return payload["item"]
    raise HTTPException(status_code=404, detail="game not found")


def _get_game_payload(game_id: str) -> dict[str, Any]:
    assert_db_available()
    with SessionLocal() as db:
        game = db.get(Game, game_id)
        if game is None:
            raise HTTPException(status_code=404, detail="game not found")
    # 날씨 네트워크 조회(최대 수 초 블로킹)는 DB 세션/커넥션을 반납한 뒤 수행한다.
    return _game_summary_payload(game)


def _game_summary_payload(game: Game, *, allow_weather_network: bool = True) -> dict[str, Any]:
    payload = to_game_summary(game).model_dump(mode="json")
    try:
        weather = build_weather_summary(
            game,
            service_key=settings.weather_service_key,
            api_base_url=settings.weather_api_base_url,
            allow_network=allow_weather_network,
        )
    except Exception:
        logger.warning("weather summary unavailable: game_id=%s", game.id, exc_info=True)
        weather = None
    payload["weather"] = weather
    return payload


def _load_game_for_weather(game_id: str) -> Game | None:
    assert_db_available()
    with SessionLocal() as db:
        return db.get(Game, game_id)


@app.get("/games/{game_id}/weather", response_model=GameWeatherHourlyOut)
async def get_game_weather(
    game_id: str,
    weather_date: date | None = Query(default=None, alias="date"),
) -> dict[str, Any]:
    # 게임 조회는 세션 안에서 끝내고, 최대 십수 초 걸리는 날씨 네트워크 조회는
    # DB 세션/커넥션을 반납한 뒤 수행한다.
    game = await asyncio.to_thread(_load_game_for_weather, game_id)
    if game is None:
        raise HTTPException(status_code=404, detail="game not found")
    target_date = weather_date or datetime.now(KST).date()

    try:
        payload = await asyncio.to_thread(
            build_hourly_weather,
            game,
            service_key=settings.weather_service_key,
            api_base_url=settings.weather_api_base_url,
            target_date=target_date,
        )
    except Exception:
        logger.warning("hourly weather unavailable: game_id=%s", game_id, exc_info=True)
        payload = None

    if payload is None:
        raise HTTPException(status_code=503, detail="weather unavailable")
    return payload


@app.get("/games/{game_id}/state", response_model=GameStateOut)
async def get_game_state(game_id: str) -> dict[str, Any]:
    cache_key = f"http:game_state:v1:{game_id}"
    return await _get_or_set_http_cache_payload(
        cache_key=cache_key,
        ttl_sec=HTTP_LIVE_CACHE_TTL_SEC,
        loader=lambda: _get_game_state_payload(game_id),
    )


def _get_game_state_payload(game_id: str) -> dict[str, Any]:
    assert_db_available()
    with SessionLocal() as db:
        game = db.get(Game, game_id)
        if game is None:
            raise HTTPException(status_code=404, detail="game not found")
        return build_game_state(db, game).model_dump(mode="json")


@app.get("/games/{game_id}/events", response_model=EventsResponse)
async def get_game_events(
    game_id: str,
    after: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
    inning_number: int | None = Query(default=None, alias="inningNumber", ge=1, le=12),
    scoring_only: bool = Query(default=False, alias="scoringOnly"),
) -> dict[str, Any]:
    cache_key = _game_events_http_cache_key(
        game_id=game_id,
        after=after,
        limit=limit,
        inning_number=inning_number,
        scoring_only=scoring_only,
    )
    return await _get_or_set_http_cache_payload(
        cache_key=cache_key,
        ttl_sec=HTTP_LIVE_CACHE_TTL_SEC,
        loader=lambda: _get_game_events_payload(
            game_id=game_id,
            after=after,
            limit=limit,
            inning_number=inning_number,
            scoring_only=scoring_only,
        ),
        # after 커서까지 락 키에 포함하면 락 엔트리가 폭증하므로 game_id 단위로만 잠근다.
        lock_key=f"http:game_events:lock:{game_id}",
    )


def _get_game_events_payload(
    *,
    game_id: str,
    after: int,
    limit: int,
    inning_number: int | None,
    scoring_only: bool,
) -> dict[str, Any]:
    assert_db_available()
    with SessionLocal() as db:
        game = db.get(Game, game_id)
        if game is None:
            raise HTTPException(status_code=404, detail="game not found")

        filters = [GameEvent.game_id == game_id, GameEvent.cursor > after]
        if inning_number is not None:
            filters.append(GameEvent.inning.like(f"{inning_number}회%"))
        if scoring_only:
            filters.append(GameEvent.event_type.in_(("SCORE", "SAC_FLY_SCORE")))

        rows = db.execute(
            select(GameEvent)
            .where(*filters)
            .order_by(GameEvent.cursor.asc())
            .limit(limit + 1)
        ).scalars().all()

        chunk = rows[:limit]
        next_cursor = chunk[-1].cursor if len(rows) > limit and chunk else None
        return {
            "items": [to_event_out(row).model_dump(mode="json") for row in chunk],
            "nextCursor": next_cursor,
        }


# MARK: - Account Deletion

def _extract_user_id_from_token(authorization: str) -> str:
    """Supabase JWT 서명(HS256)을 검증하고 user_id(sub)를 추출.

    시크릿 미설정 시 무검증 디코드로 폴백하지 않고 401 로 fail-closed 한다.
    """
    if not settings.supabase_jwt_secret:
        logger.critical(
            "SUPABASE_JWT_SECRET is not configured; rejecting token-authenticated request"
        )
        raise HTTPException(status_code=401, detail="token verification not configured")

    token = authorization.removeprefix("Bearer ").strip()
    try:
        payload = jwt.decode(
            token,
            key=settings.supabase_jwt_secret,
            algorithms=["HS256"],
            audience="authenticated",
        )
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="invalid token")
    user_id = payload.get("sub")
    if not user_id:
        raise HTTPException(status_code=401, detail="invalid token: missing sub")
    return user_id


def _purge_user_backend_rows(user_id: str) -> None:
    """탈퇴한 사용자의 백엔드 데이터 삭제 (best-effort, 실패해도 탈퇴는 성공 처리)."""
    try:
        with SessionLocal() as db:
            cheer_deleted = db.execute(
                delete(CheerEvent).where(CheerEvent.user_id == user_id)
            ).rowcount
            daily_deleted = db.execute(
                delete(UserCheckinDaily).where(UserCheckinDaily.user_id == user_id)
            ).rowcount
            season_deleted = db.execute(
                delete(UserCheckinSeason).where(UserCheckinSeason.user_id == user_id)
            ).rowcount
            session_deleted = db.execute(
                delete(LiveViewSession).where(LiveViewSession.user_key == user_id)
            ).rowcount
            db.commit()
        logger.info(
            "account data purged: user_id=%s cheer_events=%d user_checkin_daily=%d "
            "user_checkin_season=%d live_view_sessions=%d",
            user_id, cheer_deleted, daily_deleted, season_deleted, session_deleted,
        )
    except Exception:
        logger.exception("account data purge failed: user_id=%s", user_id)


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

    # Supabase 계정 삭제 성공 시 백엔드 보유 데이터도 함께 정리 (best-effort)
    await asyncio.to_thread(_purge_user_backend_rows, user_id)

    return {"status": "ok"}


# MARK: - Device Token (APNs)

@app.post("/device-tokens")
def register_device_token(
    payload: DeviceTokenRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    if db.bind is not None and db.bind.dialect.name == "postgresql":
        from sqlalchemy.dialects.postgresql import insert as pg_insert

        stmt = pg_insert(DeviceToken).values(
            token=payload.token,
            game_id=payload.game_id,
            my_team=payload.my_team,
            platform=payload.platform,
            is_sandbox=payload.is_sandbox,
            display_name_style=payload.display_name_style,
        ).on_conflict_do_update(
            constraint="uq_device_token_game",
            set_={
                "my_team": payload.my_team,
                "platform": payload.platform,
                "is_sandbox": payload.is_sandbox,
                "display_name_style": payload.display_name_style,
                # 재등록 시 최근 사용 시각 갱신 (90일 미사용 purge 기준)
                "updated_at": datetime.now(UTC),
            },
        )
        db.execute(stmt)
    else:
        existing = db.execute(
            select(DeviceToken).where(
                DeviceToken.token == payload.token,
                DeviceToken.game_id == payload.game_id,
            )
        ).scalar_one_or_none()

        if existing:
            existing.my_team = payload.my_team
            existing.platform = payload.platform
            existing.is_sandbox = payload.is_sandbox
            existing.display_name_style = payload.display_name_style
        else:
            db.add(DeviceToken(
                token=payload.token,
                game_id=payload.game_id,
                my_team=payload.my_team,
                platform=payload.platform,
                is_sandbox=payload.is_sandbox,
                display_name_style=payload.display_name_style,
            ))
    db.commit()
    background_tasks.add_task(_invalidate_push_token_cache, payload.game_id)
    return {"status": "ok"}


@app.delete("/device-tokens/{token}")
def unregister_device_token(
    token: str,
    background_tasks: BackgroundTasks,
    game_id: str = Query(...),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    row = db.execute(
        select(DeviceToken).where(
            DeviceToken.token == token,
            DeviceToken.game_id == game_id,
        )
    ).scalar_one_or_none()
    if row:
        db.delete(row)
        db.commit()
    background_tasks.add_task(_invalidate_push_token_cache, game_id)
    return {"status": "ok"}


@app.post("/live-view-sessions")
def upsert_live_view_session(
    payload: LiveViewSessionRequest,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    now = datetime.now(UTC)
    if db.bind is not None and db.bind.dialect.name == "postgresql":
        from sqlalchemy.dialects.postgresql import insert as pg_insert

        stmt = pg_insert(LiveViewSession).values(
            game_id=payload.game_id,
            user_key=payload.user_key,
            surface=payload.surface,
            token_key=payload.token_key,
            my_team=payload.my_team,
            active=payload.active,
            updated_at=now,
        ).on_conflict_do_update(
            constraint="uq_live_view_session_surface",
            set_={
                "token_key": payload.token_key,
                "my_team": payload.my_team,
                "active": payload.active,
                "updated_at": now,
            },
        )
        db.execute(stmt)
    else:
        existing = db.execute(
            select(LiveViewSession).where(
                LiveViewSession.game_id == payload.game_id,
                LiveViewSession.user_key == payload.user_key,
                LiveViewSession.surface == payload.surface,
            )
        ).scalar_one_or_none()
        if existing:
            existing.token_key = payload.token_key
            existing.my_team = payload.my_team
            existing.active = payload.active
            existing.updated_at = now
        else:
            db.add(LiveViewSession(
                game_id=payload.game_id,
                user_key=payload.user_key,
                surface=payload.surface,
                token_key=payload.token_key,
                my_team=payload.my_team,
                active=payload.active,
                updated_at=now,
            ))
    db.commit()
    return {"status": "ok"}


@app.post("/live-activity-tokens")
def register_live_activity_token(
    payload: LiveActivityTokenRequest,
    background_tasks: BackgroundTasks,
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
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        existing = db.execute(
            select(LiveActivityToken).where(
                LiveActivityToken.game_id == payload.game_id,
                LiveActivityToken.token == payload.token,
            )
        ).scalar_one_or_none()
        if existing is None:
            raise
        existing.my_team = payload.my_team
        db.commit()
    background_tasks.add_task(_invalidate_live_activity_token_cache, payload.game_id)
    return {"status": "ok"}


@app.delete("/live-activity-tokens")
def unregister_live_activity_token(
    background_tasks: BackgroundTasks,
    game_id: str = Query(...),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    rows = db.execute(
        select(LiveActivityToken).where(LiveActivityToken.game_id == game_id)
    ).scalars().all()
    for row in rows:
        db.delete(row)
    db.commit()
    background_tasks.add_task(_invalidate_live_activity_token_cache, game_id)
    return {"status": "ok"}


@app.post("/team-subscriptions")
def register_team_subscription(
    payload: TeamSubscriptionRequest,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    """응원팀 단위 글로벌 푸시 구독 등록.

    한 디바이스(=token) 가 응원팀을 변경하면 my_team 만 갱신.
    """
    if db.bind is not None and db.bind.dialect.name == "postgresql":
        from sqlalchemy.dialects.postgresql import insert as pg_insert

        stmt = pg_insert(TeamSubscriptionToken).values(
            token=payload.token,
            my_team=payload.my_team,
            platform=payload.platform,
            is_sandbox=payload.is_sandbox,
            display_name_style=payload.display_name_style,
        ).on_conflict_do_update(
            constraint="uq_team_subscription_token",
            set_={
                "my_team": payload.my_team,
                "platform": payload.platform,
                "is_sandbox": payload.is_sandbox,
                "display_name_style": payload.display_name_style,
                # 재등록 시 최근 사용 시각 갱신 (90일 미사용 purge 기준)
                "updated_at": datetime.now(UTC),
            },
        )
        db.execute(stmt)
    else:
        existing = db.execute(
            select(TeamSubscriptionToken).where(TeamSubscriptionToken.token == payload.token)
        ).scalar_one_or_none()
        if existing:
            existing.my_team = payload.my_team
            existing.platform = payload.platform
            existing.is_sandbox = payload.is_sandbox
            existing.display_name_style = payload.display_name_style
        else:
            db.add(TeamSubscriptionToken(
                token=payload.token,
                my_team=payload.my_team,
                platform=payload.platform,
                is_sandbox=payload.is_sandbox,
                display_name_style=payload.display_name_style,
            ))
    db.commit()
    return {"status": "ok"}


@app.delete("/team-subscriptions/{token}")
def unregister_team_subscription(
    token: str,
    db: Session = Depends(get_db),
) -> dict[str, str]:
    db.execute(
        delete(TeamSubscriptionToken).where(TeamSubscriptionToken.token == token)
    )
    db.commit()
    return {"status": "ok"}


def _load_push_tokens(game_id: str) -> list[tuple[str, str | None, bool, str, str]]:
    """게임에 구독된 디바이스 토큰 목록 조회 (sync DB work)"""
    assert_db_available()
    with SessionLocal() as db:
        rows = db.execute(
            select(
                DeviceToken.token,
                DeviceToken.my_team,
                DeviceToken.is_sandbox,
                DeviceToken.platform,
                DeviceToken.display_name_style,
            )
            .where(DeviceToken.game_id == game_id)
        ).all()
        return [
            (
                row.token,
                row.my_team,
                row.is_sandbox,
                row.platform,
                _normalize_display_style(row.display_name_style),
            )
            for row in rows
        ]


# 푸시 토큰은 경기당 수십~수백 건이고 거의 변하지 않으므로 짧은 TTL 로 Redis 캐시.
# 이벤트마다 DB SELECT 를 돌리면 핫패스에 불필요한 부하가 쌓인다.
PUSH_TOKEN_CACHE_TTL_SEC = 60
_PUSH_TOKEN_CACHE_KEY = "push_tokens:{game_id}"
_LIVE_ACTIVITY_TOKEN_CACHE_KEY = "live_activity_tokens:{game_id}"


async def _cached_push_tokens(game_id: str) -> list[tuple[str, str | None, bool, str, str]]:
    cache_key = _PUSH_TOKEN_CACHE_KEY.format(game_id=game_id)
    cached = await redis_relay.get_cache(cache_key)
    if cached is not None:
        items = cached.get("items") if isinstance(cached, dict) else None
        if isinstance(items, list):
            return [
                (
                    str(row[0]),
                    row[1],
                    bool(row[2]),
                    str(row[3]),
                    _normalize_display_style(str(row[4]) if len(row) > 4 else None),
                )
                for row in items
                if isinstance(row, (list, tuple)) and len(row) >= 4
            ]

    rows = await asyncio.to_thread(_load_push_tokens, game_id)
    await redis_relay.set_cache(
        cache_key,
        {"items": [list(row) for row in rows]},
        ttl_sec=PUSH_TOKEN_CACHE_TTL_SEC,
    )
    return rows


async def _cached_live_activity_tokens(game_id: str) -> list[str]:
    cache_key = _LIVE_ACTIVITY_TOKEN_CACHE_KEY.format(game_id=game_id)
    cached = await redis_relay.get_cache(cache_key)
    if cached is not None:
        items = cached.get("items") if isinstance(cached, dict) else None
        if isinstance(items, list):
            return [str(item) for item in items if isinstance(item, str)]

    tokens = await asyncio.to_thread(_load_live_activity_tokens, game_id)
    await redis_relay.set_cache(
        cache_key,
        {"items": list(tokens)},
        ttl_sec=PUSH_TOKEN_CACHE_TTL_SEC,
    )
    return tokens


async def _invalidate_push_token_cache(game_id: str) -> None:
    await redis_relay.delete_cache(_PUSH_TOKEN_CACHE_KEY.format(game_id=game_id))


async def _invalidate_live_activity_token_cache(game_id: str) -> None:
    await redis_relay.delete_cache(_LIVE_ACTIVITY_TOKEN_CACHE_KEY.format(game_id=game_id))


# MARK: - Dead push token pruning (B3)

def _delete_device_token_rows(game_id: str, tokens: list[str]) -> int:
    with SessionLocal() as db:
        result = db.execute(
            delete(DeviceToken).where(
                DeviceToken.game_id == game_id,
                DeviceToken.token.in_(tokens),
            )
        )
        db.commit()
        return int(result.rowcount or 0)


async def _prune_dead_device_tokens(game_id: str, tokens: list[str]) -> None:
    """APNs 영구 실패(410 Unregistered / BadDeviceToken) 토큰을 DB 에서 정리."""
    if not tokens:
        return
    try:
        deleted = await asyncio.to_thread(_delete_device_token_rows, game_id, tokens)
        await _invalidate_push_token_cache(game_id)
        logger.info("[push-prune] device_tokens pruned: game_id=%s count=%d", game_id, deleted)
    except Exception:
        logger.warning("[push-prune] device_tokens prune failed: game_id=%s", game_id, exc_info=True)


def _delete_team_subscription_token_rows(tokens: list[str]) -> int:
    with SessionLocal() as db:
        result = db.execute(
            delete(TeamSubscriptionToken).where(TeamSubscriptionToken.token.in_(tokens))
        )
        db.commit()
        return int(result.rowcount or 0)


async def _prune_dead_team_subscription_tokens(tokens: list[str]) -> None:
    if not tokens:
        return
    try:
        deleted = await asyncio.to_thread(_delete_team_subscription_token_rows, tokens)
        logger.info("[push-prune] team_subscription_tokens pruned: count=%d", deleted)
    except Exception:
        logger.warning("[push-prune] team_subscription_tokens prune failed", exc_info=True)


def _delete_live_activity_token_rows(game_id: str, tokens: list[str]) -> int:
    with SessionLocal() as db:
        result = db.execute(
            delete(LiveActivityToken).where(
                LiveActivityToken.game_id == game_id,
                LiveActivityToken.token.in_(tokens),
            )
        )
        db.commit()
        return int(result.rowcount or 0)


async def _prune_dead_live_activity_tokens(game_id: str, tokens: list[str]) -> None:
    if not tokens:
        return
    try:
        deleted = await asyncio.to_thread(_delete_live_activity_token_rows, game_id, tokens)
        await _invalidate_live_activity_token_cache(game_id)
        logger.info("[push-prune] live_activity_tokens pruned: game_id=%s count=%d", game_id, deleted)
    except Exception:
        logger.warning("[push-prune] live_activity_tokens prune failed: game_id=%s", game_id, exc_info=True)


# 배포된 워치(2026-04-05 커밋 30720af 이후)는 home_team/away_team을 마스코트로
# 변환(displayTeamName)해서 my_team(team code, "DOOSAN")과 단순 비교하는 회귀가 있음.
# 앱 재배포 없이 해결하려면 백엔드가 APNs payload를 보낼 때 my_team을
# 마스코트 형식("베어스")으로 미리 변환해서 비교가 일치하도록 만든다.
_TEAM_CODE_TO_MASCOT: dict[str, str] = {
    "DOOSAN": "베어스",
    "LG": "트윈스",
    "KIWOOM": "히어로즈",
    "SAMSUNG": "라이온즈",
    "LOTTE": "자이언츠",
    "SSG": "랜더스",
    "KT": "위즈",
    "HANWHA": "이글스",
    "KIA": "타이거즈",
    "NC": "다이노스",
}

_TEAM_CODE_TO_CLUB: dict[str, str] = {
    "DOOSAN": "두산",
    "LG": "LG",
    "KIWOOM": "키움",
    "SAMSUNG": "삼성",
    "LOTTE": "롯데",
    "SSG": "SSG",
    "KT": "KT",
    "HANWHA": "한화",
    "KIA": "KIA",
    "NC": "NC",
}

# 네이버 KBO 라벨이 영문 코드 / 한글 모기업 / 마스코트 어느 형태로 들어와도
# 사용자 노출용 마스코트로 정규화하기 위한 양방향 별칭 테이블.
_TEAM_ALIAS_TO_MASCOT: dict[str, str] = {
    # 한글 모기업/도시 라벨 → 마스코트
    "두산": "베어스",
    "엘지": "트윈스",
    "키움": "히어로즈",
    "삼성": "라이온즈",
    "롯데": "자이언츠",
    "에스에스지": "랜더스",
    "케이티": "위즈",
    "한화": "이글스",
    "기아": "타이거즈",
    "엔씨": "다이노스",
    # 마스코트 셀프 매핑 (이미 마스코트로 들어온 경우 no-op)
    "베어스": "베어스",
    "트윈스": "트윈스",
    "히어로즈": "히어로즈",
    "라이온즈": "라이온즈",
    "자이언츠": "자이언츠",
    "랜더스": "랜더스",
    "위즈": "위즈",
    "이글스": "이글스",
    "타이거즈": "타이거즈",
    "다이노스": "다이노스",
}

_TEAM_ALIAS_TO_CLUB: dict[str, str] = {
    "두산": "두산",
    "베어스": "두산",
    "엘지": "LG",
    "LG": "LG",
    "트윈스": "LG",
    "키움": "키움",
    "히어로즈": "키움",
    "넥센": "키움",
    "삼성": "삼성",
    "라이온즈": "삼성",
    "롯데": "롯데",
    "자이언츠": "롯데",
    "에스에스지": "SSG",
    "SSG": "SSG",
    "랜더스": "SSG",
    "케이티": "KT",
    "KT": "KT",
    "위즈": "KT",
    "한화": "한화",
    "이글스": "한화",
    "기아": "KIA",
    "KIA": "KIA",
    "타이거즈": "KIA",
    "엔씨": "NC",
    "NC": "NC",
    "다이노스": "NC",
}


def _normalize_display_style(style: str | None) -> str:
    return "MASCOT" if style == "MASCOT" else "TEAM"


def _resolve_mascot(raw: str | None) -> str | None:
    """모든 라벨 형태(영문 코드 / 한글 모기업 / 마스코트)를 마스코트로 변환.
    매칭 실패 시 None.
    """
    if not raw:
        return None
    key = raw.strip()
    if not key:
        return None
    if mapped := _TEAM_CODE_TO_MASCOT.get(key.upper()):
        return mapped
    if mapped := _TEAM_ALIAS_TO_MASCOT.get(key):
        return mapped
    lowered = key.lower()
    if "doosan" in lowered or "두산" in key or "베어스" in key:
        return "베어스"
    if "lg" in lowered or "엘지" in key or "트윈스" in key:
        return "트윈스"
    if "kiwoom" in lowered or "키움" in key or "히어로즈" in key or "넥센" in key:
        return "히어로즈"
    if "samsung" in lowered or "삼성" in key or "라이온즈" in key:
        return "라이온즈"
    if "lotte" in lowered or "롯데" in key or "자이언츠" in key:
        return "자이언츠"
    if "ssg" in lowered or "lander" in lowered or "에스에스지" in key or "랜더스" in key:
        return "랜더스"
    if "kt" in lowered or "wiz" in lowered or "케이티" in key or "위즈" in key:
        return "위즈"
    if "hanwha" in lowered or "한화" in key or "이글스" in key:
        return "이글스"
    if "kia" in lowered or "기아" in key or "타이거즈" in key:
        return "타이거즈"
    if "nc" in lowered or "dinos" in lowered or "엔씨" in key or "다이노스" in key:
        return "다이노스"
    return None


def _resolve_club(raw: str | None) -> str | None:
    """모든 라벨 형태(영문 코드 / 한글 모기업 / 마스코트)를 팀명으로 변환.
    매칭 실패 시 None.
    """
    if not raw:
        return None
    key = raw.strip()
    if not key:
        return None
    if mapped := _TEAM_CODE_TO_CLUB.get(key.upper()):
        return mapped
    if mapped := _TEAM_ALIAS_TO_CLUB.get(key):
        return mapped
    lowered = key.lower()
    if "doosan" in lowered or "두산" in key or "베어스" in key:
        return "두산"
    if "lg" in lowered or "엘지" in key or "트윈스" in key:
        return "LG"
    if "kiwoom" in lowered or "키움" in key or "히어로즈" in key or "넥센" in key:
        return "키움"
    if "samsung" in lowered or "삼성" in key or "라이온즈" in key:
        return "삼성"
    if "lotte" in lowered or "롯데" in key or "자이언츠" in key:
        return "롯데"
    if "ssg" in lowered or "lander" in lowered or "에스에스지" in key or "랜더스" in key:
        return "SSG"
    if "kt" in lowered or "wiz" in lowered or "케이티" in key or "위즈" in key:
        return "KT"
    if "hanwha" in lowered or "한화" in key or "이글스" in key:
        return "한화"
    if "kia" in lowered or "기아" in key or "타이거즈" in key:
        return "KIA"
    if "nc" in lowered or "dinos" in lowered or "엔씨" in key or "다이노스" in key:
        return "NC"
    return None


def _normalize_my_team_for_watch(raw: str | None) -> str:
    if not raw:
        return ""
    return _resolve_mascot(raw) or raw


def _team_display_name(raw: str | None, style: str | None = "TEAM") -> str:
    """home_team / away_team 의 백엔드 코드("DOOSAN") 또는 한글 라벨("두산")을
    사용자 선호 표시명으로 변환. 알 수 없는 값은 원본 그대로.
    """
    if not raw:
        return ""
    if _normalize_display_style(style) == "MASCOT":
        return _resolve_mascot(raw) or raw
    return _resolve_club(raw) or raw


def _team_codes_for_match(raw: str | None) -> set[str]:
    """home/away 팀 라벨(영문 코드 / 한글 모기업 / 마스코트)로부터
    매칭에 쓸 응원팀 코드 후보 집합. 마스코트로 먼저 정규화한 뒤
    해당 마스코트와 연결된 영문 코드를 역으로 찾는다.
    """
    if not raw:
        return set()
    upper = raw.strip().upper()
    candidates = {upper}
    mascot = _resolve_mascot(raw)
    if mascot:
        candidates.add(mascot)
        for code, m in _TEAM_CODE_TO_MASCOT.items():
            if m == mascot:
                candidates.add(code)
    return candidates


def _load_team_subscriptions(my_teams: set[str]) -> list[tuple[str, str, str, bool, str]]:
    """응원팀이 my_teams 에 포함된 구독 토큰 조회. (token, my_team, platform, is_sandbox, display_style)"""
    if not my_teams:
        return []
    assert_db_available()
    with SessionLocal() as db:
        rows = db.execute(
            select(
                TeamSubscriptionToken.token,
                TeamSubscriptionToken.my_team,
                TeamSubscriptionToken.platform,
                TeamSubscriptionToken.is_sandbox,
                TeamSubscriptionToken.display_name_style,
            ).where(TeamSubscriptionToken.my_team.in_(list(my_teams)))
        ).all()
        return [
            (r.token, r.my_team, r.platform, bool(r.is_sandbox), _normalize_display_style(r.display_name_style))
            for r in rows
        ]


async def _send_game_start_notification(
    game_id: str,
    home_team: str,
    away_team: str,
) -> None:
    """경기 시작(SCHEDULED→LIVE) 시점에 응원팀 구독자에게 visible push 발송.

    제목은 양 팀 마스코트 ("[트윈스] vs [베어스]"), 본문은 수신자 응원팀을
    강조 ("베어스 경기가 시작되었습니다!"). 응원팀별로 그룹화해 전송한다.
    """
    target_codes = _team_codes_for_match(home_team) | _team_codes_for_match(away_team)
    if not target_codes:
        return

    subscriptions = await asyncio.to_thread(_load_team_subscriptions, target_codes)
    if not subscriptions:
        return

    grouped: dict[tuple[str, str], list[tuple[str, str, bool]]] = defaultdict(list)
    for token, my_team, platform, is_sandbox, display_style in subscriptions:
        grouped[(my_team, display_style)].append((token, (platform or "ios").lower(), bool(is_sandbox)))

    tasks: list[Any] = []
    total_ios = 0
    total_android = 0
    for (my_team, display_style), group in grouped.items():
        home_display = _team_display_name(home_team, display_style)
        away_display = _team_display_name(away_team, display_style)
        title = f"[{away_display}] vs [{home_display}]"
        data = {
            "game_id": game_id,
            "kind": "game_start",
            "home_team": home_display,
            "away_team": away_display,
            "display_name_style": display_style,
        }
        team_display = _team_display_name(my_team, display_style)
        body = f"{team_display} 경기가 시작되었습니다!"

        ios_targets: list[tuple[str, bool]] = []
        android_tokens: list[str] = []
        for token, platform, is_sandbox in group:
            if platform == "android":
                android_tokens.append(token)
            else:
                ios_targets.append((token, is_sandbox))

        if ios_targets:
            tasks.append(
                send_apns_visible_push_to_tokens_detailed(
                    ios_targets,
                    title=title,
                    body=body,
                    data=data,
                    category="OPEN_LIVE_GAME",
                )
            )
            total_ios += len(ios_targets)
        if android_tokens:
            tasks.append(
                send_fcm_visible_push_to_tokens_detailed(android_tokens, title=title, body=body, data=data)
            )
            total_android += len(android_tokens)

    dead_tokens: list[str] = []
    if tasks:
        results = await asyncio.gather(*tasks, return_exceptions=True)
        for result in results:
            if isinstance(result, BaseException):
                continue
            _, permanently_failed = result
            dead_tokens.extend(permanently_failed)
    # 영구 실패(APNs Unregistered/BadDeviceToken, FCM Unregistered) 토큰 정리
    await _prune_dead_team_subscription_tokens(dead_tokens)
    logger.info(
        "[game-start-push] gameId=%s ios=%d android=%d teams=%s",
        game_id, total_ios, total_android, sorted(f"{team}:{style}" for team, style in grouped.keys()),
    )


async def _send_push_for_game_events(
    game_id: str,
    state_payload: dict[str, Any],
    event_payloads: list[dict[str, Any]],
) -> None:
    """게임 이벤트 발생 시 구독된 디바이스에 silent push 전송"""
    token_rows = await _cached_push_tokens(game_id)
    if not token_rows:
        return

    token_info = {
        t[0]: {
            "my_team": t[1],
            "is_sandbox": t[2],
            "platform": t[3],
            "display_name_style": t[4],
        }
        for t in token_rows
    }
    tokens = list(token_info.keys())

    # 이벤트가 있으면 각 이벤트에 대해 push, 없으면 상태 업데이트만
    # 누적 투구수: nullable. iOS 측은 -1 sentinel 로 nil 표현. 키 자체가 빠지면
    # AppDelegate 가 nil 로 forwarding 해 워치에서 투구수가 사라진다 — 항상 포함.
    pitcher_pitch_count = state_payload.get("pitcherPitchCount")
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
        "pitcher_pitch_count": pitcher_pitch_count if pitcher_pitch_count is not None else -1,
    }

    dead_tokens: set[str] = set()

    async def _fanout(push_payload: dict[str, Any]) -> None:
        coros = []
        for token in tokens:
            info = token_info[token]
            payload_with_team = {
                **push_payload,
                "my_team": _normalize_my_team_for_watch(info["my_team"]),
                "display_name_style": info["display_name_style"],
            }
            coros.append(send_push_with_result(
                token,
                payload_with_team,
                use_sandbox=info["is_sandbox"],
                platform=info["platform"],
            ))
        results = await asyncio.gather(*coros, return_exceptions=True)
        for token, result in zip(tokens, results):
            if isinstance(result, BaseException):
                continue
            ok, permanent = result
            # android 행은 APNs 판정으로 삭제하지 않는다 (FCM 토큰이 APNs 에서
            # BadDeviceToken 으로 보일 수 있으므로 오삭제 방지).
            if not ok and permanent and token_info[token]["platform"] != "android":
                dead_tokens.add(token)

    if event_payloads:
        for event in event_payloads:
            await _fanout({
                **base_payload,
                "event_type": event.get("type", ""),
                "event_cursor": event.get("cursor", 0),
            })
    else:
        await _fanout(base_payload)

    if dead_tokens:
        await _prune_dead_device_tokens(game_id, sorted(dead_tokens))


def _load_live_activity_tokens(game_id: str) -> list[str]:
    """게임에 구독된 Live Activity 토큰 목록 조회"""
    assert_db_available()
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
    tokens = await _cached_live_activity_tokens(game_id)
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

    results = await asyncio.gather(
        *(send_live_activity_push_with_result(token, content_state, event_type=event_type) for token in tokens),
        return_exceptions=True,
    )
    dead_tokens: list[str] = []
    for token, result in zip(tokens, results):
        if isinstance(result, BaseException):
            continue
        ok, permanent = result
        if not ok and permanent:
            dead_tokens.append(token)
    if dead_tokens:
        await _prune_dead_live_activity_tokens(game_id, dead_tokens)


@app.get("/team-records", response_model=list[TeamRecordOut])
async def get_team_record_standings(
    category_id: str = Query(default="kbo", alias="categoryId"),
    season_code: str | None = Query(default=None, alias="seasonCode"),
) -> list[dict[str, Any]]:
    normalized_season_code = (season_code or str(datetime.now(UTC).year)).strip()
    normalized_category_id = category_id.strip()
    cache_key = f"http:team_records:v1:{normalized_category_id}:{normalized_season_code}"
    payload = await _get_or_set_http_cache_payload(
        cache_key=cache_key,
        ttl_sec=HTTP_STANDINGS_CACHE_TTL_SEC,
        loader=lambda: {
            "items": _get_team_record_standings_payload(
                category_id=normalized_category_id,
                season_code=normalized_season_code,
            )
        },
    )
    return payload.get("items") or []


def _get_team_record_standings_payload(
    *,
    category_id: str,
    season_code: str,
) -> list[dict[str, Any]]:
    assert_db_available()
    with SessionLocal() as db:
        rows = get_team_records(
            db,
            category_id=category_id,
            season_code=season_code,
        )
        return [to_team_record_out(row).model_dump(mode="json") for row in rows]


@app.get("/team-records/{team_id}", response_model=TeamRecordOut)
async def get_team_record_by_team(
    team_id: str,
    category_id: str = Query(default="kbo", alias="categoryId"),
    season_code: str | None = Query(default=None, alias="seasonCode"),
) -> dict[str, Any]:
    normalized_season_code = (season_code or str(datetime.now(UTC).year)).strip()
    normalized_category_id = category_id.strip()
    normalized_team_id = team_id.strip()
    cache_key = (
        f"http:team_record:v1:{normalized_category_id}:"
        f"{normalized_season_code}:{normalized_team_id}"
    )
    payload = await _get_or_set_http_cache_payload(
        cache_key=cache_key,
        ttl_sec=HTTP_STANDINGS_CACHE_TTL_SEC,
        loader=lambda: {
            "item": _get_team_record_by_team_payload(
                team_id=normalized_team_id,
                category_id=normalized_category_id,
                season_code=normalized_season_code,
            )
        },
    )
    if payload.get("item") is not None:
        return payload["item"]
    raise HTTPException(status_code=404, detail="team record not found")


def _get_team_record_by_team_payload(
    *,
    team_id: str,
    category_id: str,
    season_code: str,
) -> dict[str, Any]:
    assert_db_available()
    with SessionLocal() as db:
        row = get_team_record(
            db,
            category_id=category_id,
            season_code=season_code,
            team_id=team_id,
        )
        if row is None:
            raise HTTPException(status_code=404, detail="team record not found")
        return to_team_record_out(row).model_dump(mode="json")


def _team_record_http_cache_key(*, category_id: str, season_code: str, team_id: str) -> str:
    return f"http:team_record:v1:{category_id}:{season_code}:{team_id}"


def _team_records_http_cache_key(*, category_id: str, season_code: str) -> str:
    return f"http:team_records:v1:{category_id}:{season_code}"


def _game_events_http_cache_key(
    *,
    game_id: str,
    after: int = 0,
    limit: int = 50,
    inning_number: int | None = None,
    scoring_only: bool = False,
) -> str:
    return (
        f"http:game_events:v1:{game_id}:after={after}:limit={limit}:"
        f"inning={(inning_number if inning_number is not None else '')}:"
        f"scoring={int(scoring_only)}"
    )


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

    game_lock = _get_snapshot_ingest_lock(game_id)
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
    game_summary_payload: dict[str, Any] | None = None
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
                fallback_inning=game.inning,
            )
            details_changed = sync_snapshot_details(db, game_id=game_id, payload=payload)
            current_event_payload = [to_event_out(item).model_dump(mode="json") for item in inserted_events]
            snapshot_changed = bool(
                current_event_payload
                or details_changed
                or getattr(game, "_snapshot_meaningful_changed", False)
            )
            current_state_payload = None
            current_game_summary_payload = None
            if snapshot_changed:
                current_state = build_game_state(db, game)
                current_state_payload = current_state.model_dump(mode="json")
                current_game_summary_payload = to_game_summary(game).model_dump(mode="json")
            response_status = normalize_status(game.status)
            response_updated_at = game.updated_at
            db.commit()
            state_payload = current_state_payload
            inserted_event_payload = current_event_payload
            game_summary_payload = current_game_summary_payload
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

    if game is None or response_status is None or response_updated_at is None:
        raise HTTPException(status_code=500, detail="snapshot ingest failed")

    # NOTE: 배포된 iOS의 .update 경로가 state.homeTeamId.teamName(마스코트만)을
    # 워치로 전달해 myTeam 비교가 깨지는 버그가 있어, 정상 동작하는 .state 경로로
    # 흐르도록 events + state 두 메시지로 분리해서 broadcast.
    if state_payload is None:
        return IngestResult(
            gameId=game.id,
            receivedEvents=len(payload.events),
            insertedEvents=len(inserted_event_payload),
            duplicateEvents=duplicate_count,
            status=response_status,
            updatedAt=response_updated_at,
        )

    if inserted_event_payload:
        background_tasks.add_task(
            _broadcast_live_message,
            game_id,
            {"type": "events", "payload": {"items": inserted_event_payload}},
        )
    background_tasks.add_task(
        _broadcast_live_message,
        game_id,
        {"type": "state", "payload": state_payload},
    )

    # Cache state and recent events in Redis for fast WS on-connect
    background_tasks.add_task(
        _cache_game_data, game_id, state_payload, inserted_event_payload,
    )
    if game_summary_payload is not None:
        background_tasks.add_task(
            _set_http_cache_payload,
            f"http:game:v1:{game_id}",
            {"item": game_summary_payload},
            HTTP_LIVE_CACHE_TTL_SEC,
        )
    background_tasks.add_task(
        _set_http_cache_payload,
        f"http:game_state:v1:{game_id}",
        state_payload,
        HTTP_LIVE_CACHE_TTL_SEC,
    )
    if inserted_event_payload:
        background_tasks.add_task(
            _delete_http_cache_payload,
            _game_events_http_cache_key(game_id=game_id),
        )

    # APNs silent push 전송 (백그라운드에서도 워치로 이벤트 전달)
    if inserted_event_payload:
        background_tasks.add_task(
            _send_push_for_game_events, game_id, state_payload, inserted_event_payload,
        )

    # 경기 시작(SCHEDULED→LIVE) 1회 한정 visible push (응원팀 구독자에게)
    if getattr(game, "_just_became_live", False):
        background_tasks.add_task(
            _send_game_start_notification,
            game_id,
            state_payload.get("homeTeam", "") or "",
            state_payload.get("awayTeam", "") or "",
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
        row_payload = to_team_record_out(row).model_dump(mode="json")
        message = {"type": "team_record", "payload": row_payload}
        background_tasks.add_task(_broadcast_live_message, channel, message)
        background_tasks.add_task(
            redis_relay.set_cache,
            f"team-record:{row.category_id}:{row.season_code}:{row.team_id}",
            message,
            300,
        )
        background_tasks.add_task(
            _set_http_cache_payload,
            _team_record_http_cache_key(
                category_id=row.category_id,
                season_code=row.season_code,
                team_id=row.team_id,
            ),
            {"item": row_payload},
            HTTP_STANDINGS_CACHE_TTL_SEC,
        )
        background_tasks.add_task(
            _delete_http_cache_payload,
            _team_records_http_cache_key(
                category_id=row.category_id,
                season_code=row.season_code,
            ),
            delete_stale=True,
        )

    return TeamRecordIngestResult(
        categoryId=payload.categoryId,
        seasonCode=payload.seasonCode,
        receivedRecords=len(payload.records),
        upsertedRecords=upsert_result.upserted_records,
        updatedAt=datetime.now(UTC),
    )


def _load_game_initial_data(game_id: str) -> tuple[dict[str, Any] | None, list[dict[str, Any]]]:
    """Sync DB work for WS on-connect, intended to run via asyncio.to_thread."""
    assert_db_available()
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
    remaining = await _db_unavailable_remaining_sec_global(hold_expired=True)
    if remaining > 0:
        await websocket.accept()
        await websocket.close(code=1013, reason=f"database retry in {int(remaining) + 1}s")
        return
    if not await event_bus.connect(websocket):
        return
    await event_bus.register(game_id, websocket)
    try:
        state_payload, events_payload = await _load_game_initial_data_cached(game_id)
        if state_payload is not None:
            await event_bus.safe_send(websocket, {"type": "state", "payload": state_payload})
        if events_payload:
            await event_bus.safe_send(
                websocket, {"type": "events", "payload": {"items": events_payload}}
            )

        while True:
            await websocket.receive_text()
            await event_bus.safe_send(
                websocket,
                {"type": "pong", "payload": {"at": datetime.now(UTC).isoformat()}},
            )
    except WebSocketDisconnect:
        pass
    except HTTPException as exc:
        if exc.status_code != 503:
            logger.warning("ws game stream aborted game_id=%s: %s", game_id, exc)
    except Exception as exc:
        if _is_database_failure(exc):
            await _mark_db_unavailable_global()
        logger.warning("ws game stream aborted game_id=%s: %s", game_id, exc)
    finally:
        await event_bus.disconnect(game_id, websocket)


def _load_team_record_initial_data(
    category_id: str, season_code: str, team_id: str,
) -> dict[str, Any] | None:
    """Sync DB work for team-record WS on-connect, intended to run via asyncio.to_thread."""
    assert_db_available()
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
    remaining = await _db_unavailable_remaining_sec_global(hold_expired=True)
    if remaining > 0:
        await websocket.accept()
        await websocket.close(code=1013, reason=f"database retry in {int(remaining) + 1}s")
        return
    normalized_category_id = category_id.strip().lower()
    normalized_season_code = (season_code or str(datetime.now(UTC).year)).strip()
    normalized_team_id = team_id.strip().upper()
    channel = _team_record_channel(
        category_id=normalized_category_id,
        season_code=normalized_season_code,
        team_id=normalized_team_id,
    )
    if not await event_bus.connect(websocket):
        return
    await event_bus.register(channel, websocket)
    try:
        message = await _load_team_record_initial_data_cached(
            normalized_category_id, normalized_season_code, normalized_team_id,
        )
        if message is not None:
            await event_bus.safe_send(websocket, message)

        while True:
            await websocket.receive_text()
            await event_bus.safe_send(
                websocket,
                {"type": "pong", "payload": {"at": datetime.now(UTC).isoformat()}},
            )
    except WebSocketDisconnect:
        pass
    except HTTPException as exc:
        if exc.status_code != 503:
            logger.warning("ws team-record stream aborted channel=%s: %s", channel, exc)
    except Exception as exc:
        if _is_database_failure(exc):
            await _mark_db_unavailable_global()
        logger.warning("ws team-record stream aborted channel=%s: %s", channel, exc)
    finally:
        await event_bus.disconnect(channel, websocket)


def _record_cheer_event(
    db: Session,
    *,
    user_id: str,
    team_code: str,
    stadium_code: str,
    game_id: str | None,
    client_ts: datetime,
    lat: float | None,
    lng: float | None,
    accuracy_m: float | None,
    mock_location: bool,
    app_version: str | None,
    device_id_hash: str | None,
    ip_hash: str | None,
    is_home_team: bool | None,
    opponent_team_code: str | None,
    platform: str,
) -> CheerEvent:
    event = CheerEvent(
        user_id=user_id,
        team_code=team_code,
        stadium_code=stadium_code,
        game_id=game_id,
        client_ts=client_ts,
        lat=lat,
        lng=lng,
        accuracy_m=accuracy_m,
        mock_location=mock_location,
        app_version=app_version,
        device_id_hash=device_id_hash,
        ip_hash=ip_hash,
        is_home_team=is_home_team,
        opponent_team_code=opponent_team_code,
        platform=platform,
        validity_status="pending",
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


@app.get("/stadiums")
def get_stadiums() -> dict[str, Any]:
    return {"items": stadium_payloads()}


@app.get("/cheer-signals")
def get_cheer_signals(
    db: Annotated[Session, Depends(get_db)],
    target_date: date | None = Query(default=None, alias="date"),
) -> dict[str, Any]:
    resolved_date = target_date or datetime.now(KST).date()
    return {
        "date": resolved_date.isoformat(),
        "items": build_cheer_signals(db, target_date=resolved_date),
    }


@app.post("/cheer-events")
def post_cheer_event(
    payload: dict[str, Any],
    request: Request,
    background_tasks: BackgroundTasks,
    db: Annotated[Session, Depends(get_db)],
    authorization: Annotated[str | None, Header()] = None,
) -> dict[str, Any]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization header required")

    user_id = _extract_user_id_from_token(authorization)
    team_code = str(payload.get("team_code") or "").strip()
    stadium_code = str(payload.get("stadium_code") or "").strip()
    if not (team_code and stadium_code):
        raise HTTPException(status_code=400, detail="team_code, stadium_code required")

    client_ts_raw = payload.get("client_ts")
    try:
        client_ts = datetime.fromisoformat(str(client_ts_raw).replace("Z", "+00:00")) if client_ts_raw else datetime.now(UTC)
    except ValueError:
        raise HTTPException(status_code=400, detail="client_ts must be ISO8601")

    platform_raw = str(payload.get("platform") or "unknown").lower()
    platform = platform_raw if platform_raw in ("ios", "android") else "unknown"
    event = _record_cheer_event(
        db,
        user_id=user_id,
        team_code=team_code,
        stadium_code=stadium_code,
        game_id=payload.get("game_id"),
        client_ts=client_ts,
        lat=payload.get("lat"),
        lng=payload.get("lng"),
        accuracy_m=payload.get("accuracy_m"),
        mock_location=bool(payload.get("mock_location") or False),
        app_version=payload.get("app_version"),
        device_id_hash=payload.get("device_id_hash"),
        ip_hash=_hash_ip(request.client.host if request.client else None),
        is_home_team=payload.get("is_home_team"),
        opponent_team_code=payload.get("opponent_team_code"),
        platform=platform,
    )
    background_tasks.add_task(_validate_pending_cheer_events_background)
    return {"id": event.id, "status": event.validity_status, "platform": platform}


def _hash_ip(ip: str | None) -> str | None:
    if not ip:
        return None
    return hashlib.sha256(f"{settings.instance_id or 'basehaptic'}:{ip}".encode()).hexdigest()


def _validate_pending_cheer_events_background() -> None:
    assert_db_available()
    with SessionLocal() as db:
        validate_pending_cheer_events(db, limit=100)


@app.post("/cheer-events/validate-pending")
def validate_pending_cheer_events_now(
    db: Annotated[Session, Depends(get_db)],
    authorization: Annotated[str | None, Header()] = None,
    limit: int = Query(default=100, ge=1, le=500),
) -> dict[str, Any]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization header required")
    _extract_user_id_from_token(authorization)
    return {"updated": validate_pending_cheer_events(db, limit=limit)}


@app.get("/rankings/teams")
def get_team_rankings(
    db: Annotated[Session, Depends(get_db)],
    period: str = Query("season", pattern="^(season|weekly)$"),
    season: str = Query("2026"),
) -> dict[str, Any]:
    if period == "season":
        rows = (
            db.execute(
                select(TeamCheckinSeason)
                .where(TeamCheckinSeason.season == season)
                .order_by(TeamCheckinSeason.count.desc())
            )
            .scalars()
            .all()
        )
        items = [
            {"team_code": r.team_code, "count": r.count, "rank": idx + 1}
            for idx, r in enumerate(rows)
        ]
    else:
        today = datetime.now(KST).date()
        since = today - timedelta(days=7)
        total_count = func.sum(TeamCheckinDaily.count).label("count")
        rows = (
            db.execute(
                select(TeamCheckinDaily.team_code, total_count)
                .where(TeamCheckinDaily.date >= since.isoformat())
                .group_by(TeamCheckinDaily.team_code)
                .order_by(total_count.desc(), TeamCheckinDaily.team_code.asc())
            )
            .all()
        )
        items = [
            {"team_code": row.team_code, "count": int(row.count), "rank": idx + 1}
            for idx, row in enumerate(rows)
        ]
    # 집계는 iOS + Android raw 이벤트를 합산한 값. 분리 응답이 필요하면 별도 엔드포인트로 추가.
    return {
        "period": period,
        "season": season,
        "platforms_aggregated": ["ios", "android"],
        "items": items,
    }


@app.get("/cheer-events/me")
def get_my_cheer_events(
    db: Annotated[Session, Depends(get_db)],
    authorization: Annotated[str | None, Header()] = None,
    since: str | None = Query(None, description="ISO8601 또는 YYYY-MM-DD. 시즌 시작 등 하한."),
    until: str | None = Query(None, description="ISO8601 또는 YYYY-MM-DD. 상한."),
    only_valid: bool = Query(True, description="false 시 invalid/suspicious도 포함."),
    limit: int = Query(200, ge=1, le=500, description="최대 반환 개수."),
    offset: int = Query(0, ge=0, description="페이지네이션 오프셋."),
) -> dict[str, Any]:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authorization header required")
    user_id = _extract_user_id_from_token(authorization)
    stmt = select(CheerEvent).where(CheerEvent.user_id == user_id)
    if only_valid:
        stmt = stmt.where(CheerEvent.validity_status == "valid")
    if since:
        try:
            stmt = stmt.where(CheerEvent.client_ts >= datetime.fromisoformat(since.replace("Z", "+00:00")))
        except ValueError:
            raise HTTPException(status_code=400, detail="since must be ISO8601 or YYYY-MM-DD")
    if until:
        try:
            stmt = stmt.where(CheerEvent.client_ts <= datetime.fromisoformat(until.replace("Z", "+00:00")))
        except ValueError:
            raise HTTPException(status_code=400, detail="until must be ISO8601 or YYYY-MM-DD")
    stmt = stmt.order_by(CheerEvent.client_ts.desc()).limit(limit).offset(offset)

    rows = db.execute(stmt).scalars().all()
    items = [
        {
            "id": r.id,
            "client_ts": r.client_ts.isoformat(),
            "stadium_code": r.stadium_code,
            "team_code": r.team_code,
            "game_id": r.game_id,
            "is_home_team": r.is_home_team,
            "opponent_team_code": r.opponent_team_code,
            "validity_status": r.validity_status,
        }
        for r in rows
    ]
    return {"user_id": user_id, "count": len(items), "items": items}
