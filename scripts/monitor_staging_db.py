#!/usr/bin/env python3
"""Sample PostgreSQL load indicators during a staging load test."""

from __future__ import annotations

import argparse
import json
import os
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import psycopg
from psycopg.rows import dict_row


ACTIVITY_SQL = """
select
  count(*)::int as sessions,
  count(*) filter (where state = 'active')::int as active,
  count(*) filter (where state = 'idle')::int as idle,
  count(*) filter (where state = 'idle in transaction')::int as idle_in_transaction,
  count(*) filter (where wait_event_type is not null and wait_event_type <> 'Client')::int as non_client_waiting,
  coalesce(max(extract(epoch from now() - query_start)) filter (where state = 'active'), 0)::float as max_active_query_age_sec,
  coalesce(max(extract(epoch from now() - xact_start)) filter (where xact_start is not null), 0)::float as max_xact_age_sec
from pg_stat_activity
where datname = current_database()
"""

DATABASE_SQL = """
select
  numbackends::int,
  xact_commit::bigint,
  xact_rollback::bigint,
  blks_read::bigint,
  blks_hit::bigint,
  tup_returned::bigint,
  tup_fetched::bigint,
  tup_inserted::bigint,
  tup_updated::bigint,
  tup_deleted::bigint,
  conflicts::bigint,
  deadlocks::bigint,
  temp_files::bigint,
  temp_bytes::bigint
from pg_stat_database
where datname = current_database()
"""

LOCKS_SQL = """
select
  count(*)::int as locks,
  count(*) filter (where not granted)::int as waiting_locks
from pg_locks l
left join pg_database d on d.oid = l.database
where d.datname = current_database() or l.database is null
"""

TABLE_SQL = """
select
  relname,
  n_live_tup::bigint,
  n_dead_tup::bigint,
  seq_scan::bigint,
  idx_scan::bigint,
  n_tup_ins::bigint,
  n_tup_upd::bigint,
  n_tup_del::bigint
from pg_stat_user_tables
where relname in ('games', 'game_events', 'live_view_sessions', 'team_records')
order by relname
"""


def _normalize_url(url: str) -> str:
    if url.startswith("postgresql+psycopg://"):
        return "postgresql://" + url.removeprefix("postgresql+psycopg://")
    return url


def _default_output_path() -> Path:
    stamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")
    return Path("Test") / f"staging_db_load_{stamp}.jsonl"


def _fetch_one(cur: psycopg.Cursor, sql: str) -> dict[str, Any]:
    cur.execute(sql)
    row = cur.fetchone()
    return dict(row or {})


def _fetch_all(cur: psycopg.Cursor, sql: str) -> list[dict[str, Any]]:
    cur.execute(sql)
    return [dict(row) for row in cur.fetchall()]


def _sample(conn: psycopg.Connection) -> dict[str, Any]:
    with conn.cursor(row_factory=dict_row) as cur:
        return {
            "at": datetime.now(UTC).isoformat(),
            "activity": _fetch_one(cur, ACTIVITY_SQL),
            "database": _fetch_one(cur, DATABASE_SQL),
            "locks": _fetch_one(cur, LOCKS_SQL),
            "tables": _fetch_all(cur, TABLE_SQL),
        }


def _print_line(sample: dict[str, Any]) -> None:
    activity = sample.get("activity", {})
    database = sample.get("database", {})
    locks = sample.get("locks", {})
    print(
        "{at} sessions={sessions} active={active} non_client_waiting={waiting} "
        "idle_tx={idle_tx} backends={backends} locks_waiting={locks_waiting} "
        "max_active_query_age={max_active_age:.1f}s "
        "max_xact_age={max_xact_age:.1f}s".format(
            at=sample["at"],
            sessions=activity.get("sessions", 0),
            active=activity.get("active", 0),
            waiting=activity.get("non_client_waiting", 0),
            idle_tx=activity.get("idle_in_transaction", 0),
            backends=database.get("numbackends", 0),
            locks_waiting=locks.get("waiting_locks", 0),
            max_active_age=float(activity.get("max_active_query_age_sec") or 0),
            max_xact_age=float(activity.get("max_xact_age_sec") or 0),
        ),
        flush=True,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Monitor staging Postgres while load testing")
    parser.add_argument("--database-url", default=os.environ.get("STAGING_POSTGRES_URL"))
    parser.add_argument("--duration-sec", type=float, default=300.0)
    parser.add_argument("--interval-sec", type=float, default=5.0)
    parser.add_argument("--output", default=str(_default_output_path()))
    args = parser.parse_args()

    if not args.database_url:
        raise SystemExit("Set STAGING_POSTGRES_URL or pass --database-url")
    if args.interval_sec <= 0:
        raise SystemExit("--interval-sec must be > 0")

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    deadline = time.monotonic() + args.duration_sec
    print(f"DB monitor writing samples to {output_path}")

    with psycopg.connect(_normalize_url(args.database_url), connect_timeout=10) as conn:
        with output_path.open("a", encoding="utf-8") as out:
            while time.monotonic() < deadline:
                started = time.monotonic()
                try:
                    sample = _sample(conn)
                except Exception as exc:
                    conn.rollback()
                    sample = {
                        "at": datetime.now(UTC).isoformat(),
                        "error": f"{exc.__class__.__name__}: {str(exc)[:240]}",
                    }
                    print(f"{sample['at']} ERROR {sample['error']}", flush=True)
                else:
                    _print_line(sample)

                out.write(json.dumps(sample, ensure_ascii=False, sort_keys=True) + "\n")
                out.flush()

                sleep_for = args.interval_sec - (time.monotonic() - started)
                if sleep_for > 0:
                    time.sleep(sleep_for)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
