import logging
from collections.abc import Generator

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Connection
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


settings = get_settings()
logger = logging.getLogger(__name__)

GAME_EVENT_TYPE_VALUES = (
    "BALL",
    "STRIKE",
    "WALK",
    "OUT",
    "DOUBLE_PLAY",
    "TRIPLE_PLAY",
    "HALF_INNING_CHANGE",
    "HIT",
    "HOMERUN",
    "SCORE",
    "SAC_FLY_SCORE",
    "TAG_UP_ADVANCE",
    "STEAL",
    "PITCHER_CHANGE",
    "OTHER",
)

connect_args = {}
if settings.database_url.startswith("sqlite"):
    connect_args = {"check_same_thread": False}
else:
    connect_args = {"connect_timeout": max(1, settings.db_connect_timeout_sec)}

engine_kwargs = {
    "future": True,
    "echo": False,
    "connect_args": connect_args,
    "pool_pre_ping": True,
    "pool_recycle": max(60, settings.db_pool_recycle_sec),
}

if not settings.database_url.startswith("sqlite"):
    engine_kwargs.update(
        {
            "pool_size": max(1, settings.db_pool_size),
            "max_overflow": max(0, settings.db_max_overflow),
            "pool_timeout": max(1, settings.db_pool_timeout_sec),
            "pool_use_lifo": True,
        }
    )

engine = create_engine(settings.database_url, **engine_kwargs)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def init_db() -> None:
    from . import models  # noqa: F401

    Base.metadata.create_all(bind=engine)
    _ensure_game_columns()
    _ensure_game_event_columns()
    _ensure_boxscore_context_columns()
    _ensure_game_event_type_check_constraint()
    _ensure_device_token_columns()


def _ensure_game_columns() -> None:
    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    if "games" not in table_names:
        return

    columns = {column["name"] for column in inspector.get_columns("games")}
    ddl_statements: list[str] = []
    if "start_time" not in columns:
        ddl_statements.append("ALTER TABLE games ADD COLUMN start_time VARCHAR(5)")
    if "game_date" not in columns:
        ddl_statements.append("ALTER TABLE games ADD COLUMN game_date VARCHAR(10)")
    if not ddl_statements:
        return

    with engine.begin() as conn:
        for ddl in ddl_statements:
            _execute_ddl_best_effort(conn, ddl)


def _ensure_game_event_columns() -> None:
    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    if "game_events" not in table_names:
        return

    columns = {column["name"] for column in inspector.get_columns("game_events")}
    ddl_statements: list[str] = []
    if "pitcher" not in columns:
        ddl_statements.append("ALTER TABLE game_events ADD COLUMN pitcher VARCHAR(128)")
    if "batter" not in columns:
        ddl_statements.append("ALTER TABLE game_events ADD COLUMN batter VARCHAR(128)")

    if not ddl_statements:
        return

    with engine.begin() as conn:
        for ddl in ddl_statements:
            _execute_ddl_best_effort(conn, ddl)


def _ensure_boxscore_context_columns() -> None:
    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    target_tables = ("game_lineup_slots", "game_batter_stats", "game_pitcher_stats")
    for table_name in target_tables:
        if table_name not in table_names:
            continue

        columns = {column["name"] for column in inspector.get_columns(table_name)}
        ddl_statements: list[str] = []
        if "player_team" not in columns:
            ddl_statements.append(f"ALTER TABLE {table_name} ADD COLUMN player_team VARCHAR(64)")
        if "game_date" not in columns:
            ddl_statements.append(f"ALTER TABLE {table_name} ADD COLUMN game_date VARCHAR(10)")
        if "home_team" not in columns:
            ddl_statements.append(f"ALTER TABLE {table_name} ADD COLUMN home_team VARCHAR(64)")
        if "away_team" not in columns:
            ddl_statements.append(f"ALTER TABLE {table_name} ADD COLUMN away_team VARCHAR(64)")

        if not ddl_statements:
            continue

        with engine.begin() as conn:
            for ddl in ddl_statements:
                _execute_ddl_best_effort(conn, ddl)


def _ensure_game_event_type_check_constraint() -> None:
    if engine.dialect.name != "postgresql":
        return

    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    if "game_events" not in table_names:
        return

    with engine.connect() as conn:
        row = conn.execute(
            text(
                """
                select pg_get_constraintdef(c.oid) as condef
                from pg_constraint c
                join pg_class t on t.oid = c.conrelid
                join pg_namespace n on n.oid = t.relnamespace
                where n.nspname = 'public'
                  and t.relname = 'game_events'
                  and c.conname = 'game_events_event_type_check'
                """
            )
        ).mappings().first()

    condef = str((row or {}).get("condef") or "")
    expected_tokens = [f"'{event_type}'::text" for event_type in GAME_EVENT_TYPE_VALUES]
    if condef and all(token in condef for token in expected_tokens):
        return

    values = ", ".join(f"'{event_type}'" for event_type in GAME_EVENT_TYPE_VALUES)
    with engine.begin() as conn:
        _execute_ddl_best_effort(conn, "alter table public.game_events drop constraint if exists game_events_event_type_check")
        _execute_ddl_best_effort(
            conn,
            f"""
            alter table public.game_events
            add constraint game_events_event_type_check
            check (event_type in ({values}))
            """,
        )


def _ensure_device_token_columns() -> None:
    inspector = inspect(engine)
    table_names = set(inspector.get_table_names())
    if "device_tokens" not in table_names:
        return

    columns = {column["name"] for column in inspector.get_columns("device_tokens")}
    if "is_sandbox" in columns:
        return

    with engine.begin() as conn:
        _execute_ddl_best_effort(
            conn,
            "ALTER TABLE device_tokens ADD COLUMN is_sandbox BOOLEAN NOT NULL DEFAULT FALSE",
        )


def _execute_ddl_best_effort(conn: Connection, ddl: str) -> None:
    try:
        conn.execute(text(ddl))
    except SQLAlchemyError as exc:
        logger.warning("ddl skipped during startup sql=%s error=%s", ddl, exc)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
