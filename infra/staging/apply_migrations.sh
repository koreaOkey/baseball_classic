#!/bin/sh
set -eu

if [ -z "${STAGING_POSTGRES_URL:-}" ]; then
  echo "STAGING_POSTGRES_URL is required." >&2
  echo "Use a psql-compatible staging Supabase URL, not postgresql+psycopg://." >&2
  exit 1
fi

case "$STAGING_POSTGRES_URL" in
  *snrafqoqpmtoannnnwdq*|*baseballclassic-production*)
    echo "Refusing to run against a known production database reference." >&2
    exit 1
    ;;
  postgresql+psycopg://*)
    echo "Use postgresql:// for psql migrations. postgresql+psycopg:// is for the backend app." >&2
    exit 1
    ;;
esac

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

for migration in "$REPO_ROOT"/db/migrations/*.sql; do
  echo "Applying $(basename "$migration")"
  psql "$STAGING_POSTGRES_URL" -v ON_ERROR_STOP=1 -f "$migration"
done

echo "Staging migrations applied."
