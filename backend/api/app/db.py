import logging
from contextlib import contextmanager
from collections.abc import Generator
from typing import Any

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Connection, Engine
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import NullPool

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

is_sqlite = settings.database_url.startswith("sqlite")
# Supavisor Transaction 모드 풀러(6543)는 prepared statement 캐시와 충돌하므로
# 클라이언트 풀을 끄고(NullPool) psycopg prepared statement 를 비활성화한다.
is_transaction_pooler = (not is_sqlite) and (":6543" in settings.database_url)

if is_sqlite:
    connect_args: dict[str, Any] = {"check_same_thread": False}
else:
    connect_args = {"connect_timeout": max(1, settings.db_connect_timeout_sec)}
    if is_transaction_pooler:
        connect_args["prepare_threshold"] = None

engine_kwargs: dict[str, Any] = {
    "future": True,
    "echo": False,
    "connect_args": connect_args,
    "pool_pre_ping": True,
    "pool_recycle": max(60, settings.db_pool_recycle_sec),
}

if is_transaction_pooler:
    engine_kwargs["poolclass"] = NullPool
elif not is_sqlite:
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

    # Reason: uvicorn --workers N 으로 다중 워커가 동시에 startup 하면 SQLAlchemy 의
    # create_all(checkfirst=True) 가 race 를 일으켜 일부 워커가 pg_type/pg_class
    # UniqueViolation 으로 실패한다. 첫 새 테이블 추가 시 startup 이 길어지거나
    # 영구 실패할 수 있으므로 PostgreSQL advisory lock 으로 init 을 직렬화한다.
    if engine.dialect.name == "postgresql":
        with engine.connect() as lock_conn:
            lock_conn.execute(text("SELECT pg_advisory_lock(hashtext('basehaptic_init_db'))"))
            lock_conn.commit()
            try:
                schema_conn = lock_conn.execution_options(isolation_level="AUTOCOMMIT")
                _run_schema_init(schema_conn)
            finally:
                lock_conn.execute(text("SELECT pg_advisory_unlock(hashtext('basehaptic_init_db'))"))
                lock_conn.commit()
    else:
        _run_schema_init(engine)


SchemaBind = Engine | Connection


def _run_schema_init(bind: SchemaBind = engine) -> None:
    Base.metadata.create_all(bind=bind)
    _ensure_game_columns(bind)
    _ensure_game_event_columns(bind)
    _ensure_boxscore_context_columns(bind)
    _ensure_game_event_type_check_constraint(bind)
    _ensure_device_token_columns(bind)
    _ensure_cheer_events_user_id_index(bind)
    _ensure_user_checkin_backfill(bind)


@contextmanager
def _with_connection(bind: SchemaBind) -> Generator[Connection, None, None]:
    if isinstance(bind, Connection):
        yield bind
        return

    with bind.connect() as conn:
        yield conn


def _execute_ddl_statements(bind: SchemaBind, ddl_statements: list[str]) -> None:
    if not ddl_statements:
        return

    if isinstance(bind, Connection):
        for ddl in ddl_statements:
            _execute_ddl_best_effort(bind, ddl)
        return

    with bind.begin() as conn:
        for ddl in ddl_statements:
            _execute_ddl_best_effort(conn, ddl)


def _ensure_game_columns(bind: SchemaBind = engine) -> None:
    inspector = inspect(bind)
    table_names = set(inspector.get_table_names())
    if "games" not in table_names:
        return

    columns = {column["name"] for column in inspector.get_columns("games")}
    ddl_statements: list[str] = []
    nullable_columns = {
        "start_time": "VARCHAR(5)",
        "game_date": "VARCHAR(10)",
        "live_started_at": "TIMESTAMPTZ",
        "base_first_runner": "VARCHAR(128)",
        "base_second_runner": "VARCHAR(128)",
        "base_third_runner": "VARCHAR(128)",
        "last_event_type": "VARCHAR(32)",
        "last_event_desc": "TEXT",
        "last_event_at": "TIMESTAMPTZ",
    }
    for column_name, column_type in nullable_columns.items():
        if column_name not in columns:
            ddl_statements.append(f"ALTER TABLE games ADD COLUMN {column_name} {column_type}")

    summary_columns = {
        "home_hits": "INTEGER NOT NULL DEFAULT 0",
        "away_hits": "INTEGER NOT NULL DEFAULT 0",
        "home_home_runs": "INTEGER NOT NULL DEFAULT 0",
        "away_home_runs": "INTEGER NOT NULL DEFAULT 0",
        "home_outs_total": "INTEGER NOT NULL DEFAULT 0",
        "away_outs_total": "INTEGER NOT NULL DEFAULT 0",
    }
    for column_name, column_type in summary_columns.items():
        if column_name not in columns:
            ddl_statements.append(f"ALTER TABLE games ADD COLUMN {column_name} {column_type}")

    _execute_ddl_statements(bind, ddl_statements)


def _ensure_game_event_columns(bind: SchemaBind = engine) -> None:
    inspector = inspect(bind)
    table_names = set(inspector.get_table_names())
    if "game_events" not in table_names:
        return

    columns = {column["name"] for column in inspector.get_columns("game_events")}
    ddl_statements: list[str] = []
    if "pitcher" not in columns:
        ddl_statements.append("ALTER TABLE game_events ADD COLUMN pitcher VARCHAR(128)")
    if "batter" not in columns:
        ddl_statements.append("ALTER TABLE game_events ADD COLUMN batter VARCHAR(128)")
    if "inning" not in columns:
        ddl_statements.append("ALTER TABLE game_events ADD COLUMN inning VARCHAR(32)")

    _execute_ddl_statements(bind, ddl_statements)


def _ensure_boxscore_context_columns(bind: SchemaBind = engine) -> None:
    inspector = inspect(bind)
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

        _execute_ddl_statements(bind, ddl_statements)


def _ensure_game_event_type_check_constraint(bind: SchemaBind = engine) -> None:
    if engine.dialect.name != "postgresql":
        return

    inspector = inspect(bind)
    table_names = set(inspector.get_table_names())
    if "game_events" not in table_names:
        return

    with _with_connection(bind) as conn:
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
    _execute_ddl_statements(
        bind,
        [
            "alter table public.game_events drop constraint if exists game_events_event_type_check",
            f"""
            alter table public.game_events
            add constraint game_events_event_type_check
            check (event_type in ({values}))
            """,
        ],
    )


def _ensure_device_token_columns(bind: SchemaBind = engine) -> None:
    inspector = inspect(bind)
    table_names = set(inspector.get_table_names())
    if "device_tokens" not in table_names:
        return

    columns = {column["name"] for column in inspector.get_columns("device_tokens")}
    if "is_sandbox" in columns:
        return

    _execute_ddl_statements(
        bind,
        ["ALTER TABLE device_tokens ADD COLUMN is_sandbox BOOLEAN NOT NULL DEFAULT FALSE"],
    )


def _ensure_cheer_events_user_id_index(bind: SchemaBind = engine) -> None:
    # 기존 cheer_events 테이블에 user_id 인덱스가 없을 수 있다(모델에 추가되기 전 배포된 인스턴스).
    # CREATE INDEX IF NOT EXISTS 로 멱등 보강.
    inspector = inspect(bind)
    if "cheer_events" not in set(inspector.get_table_names()):
        return
    _execute_ddl_statements(
        bind,
        ["CREATE INDEX IF NOT EXISTS idx_cheer_events_user_id ON cheer_events (user_id)"],
    )


def _ensure_user_checkin_backfill(bind: SchemaBind = engine) -> None:
    # user_checkin_season 행이 0건일 때만 기존 valid cheer_events 를 user 단위 집계 테이블에 채운다.
    # 한 번 채워지면 이후 startup 에서는 스킵. 검증 워커가 신규 valid 이벤트로 이후를 증가시킨다.
    inspector = inspect(bind)
    tables = set(inspector.get_table_names())
    if not {"cheer_events", "user_checkin_daily", "user_checkin_season"}.issubset(tables):
        return

    try:
        with _with_connection(bind) as conn:
            existing = conn.execute(text("SELECT 1 FROM user_checkin_season LIMIT 1")).first()
            if existing is not None:
                return
            has_valid = conn.execute(
                text("SELECT 1 FROM cheer_events WHERE validity_status = 'valid' LIMIT 1")
            ).first()
            if has_valid is None:
                return
    except SQLAlchemyError as exc:
        logger.warning("user_checkin backfill probe failed err=%s", exc)
        return

    if engine.dialect.name == "postgresql":
        daily_sql = """
            INSERT INTO user_checkin_daily (user_id, date, count, updated_at)
            SELECT
                user_id,
                to_char(client_ts AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD'),
                COUNT(*),
                now()
            FROM cheer_events
            WHERE validity_status = 'valid'
            GROUP BY 1, 2
            ON CONFLICT (user_id, date) DO NOTHING
        """
        season_sql = """
            INSERT INTO user_checkin_season (user_id, season, count, updated_at)
            SELECT
                user_id,
                to_char(client_ts AT TIME ZONE 'Asia/Seoul', 'YYYY'),
                COUNT(*),
                now()
            FROM cheer_events
            WHERE validity_status = 'valid'
            GROUP BY 1, 2
            ON CONFLICT (user_id, season) DO NOTHING
        """
    else:
        # SQLite: client_ts 는 UTC 저장이므로 +9h 시프트 후 strftime.
        daily_sql = """
            INSERT OR IGNORE INTO user_checkin_daily (user_id, date, count, updated_at)
            SELECT
                user_id,
                strftime('%Y-%m-%d', client_ts, '+9 hours'),
                COUNT(*),
                CURRENT_TIMESTAMP
            FROM cheer_events
            WHERE validity_status = 'valid'
            GROUP BY 1, 2
        """
        season_sql = """
            INSERT OR IGNORE INTO user_checkin_season (user_id, season, count, updated_at)
            SELECT
                user_id,
                strftime('%Y', client_ts, '+9 hours'),
                COUNT(*),
                CURRENT_TIMESTAMP
            FROM cheer_events
            WHERE validity_status = 'valid'
            GROUP BY 1, 2
        """

    try:
        if isinstance(bind, Connection):
            conn = bind
            conn.execute(text(daily_sql))
            conn.execute(text(season_sql))
        else:
            with bind.begin() as conn:
                conn.execute(text(daily_sql))
                conn.execute(text(season_sql))
    except SQLAlchemyError as exc:
        # 백필 실패해도 startup 은 계속 — 검증 워커가 신규 이벤트로 자기 치유한다.
        logger.warning("user_checkin backfill failed err=%s", exc)


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
