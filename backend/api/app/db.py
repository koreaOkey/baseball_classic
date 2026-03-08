from collections.abc import Generator

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from .config import get_settings


settings = get_settings()

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

engine = create_engine(
    settings.database_url,
    future=True,
    echo=False,
    connect_args=connect_args,
)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)


class Base(DeclarativeBase):
    pass


def init_db() -> None:
    from . import models  # noqa: F401

    Base.metadata.create_all(bind=engine)
    _ensure_game_columns()
    _ensure_game_event_columns()
    _ensure_game_event_type_check_constraint()


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
            conn.execute(text(ddl))


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
            conn.execute(text(ddl))


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
        conn.execute(text("alter table public.game_events drop constraint if exists game_events_event_type_check"))
        conn.execute(
            text(
                f"""
                alter table public.game_events
                add constraint game_events_event_type_check
                check (event_type in ({values}))
                """
            )
        )


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
