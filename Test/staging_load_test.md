# Staging Load Test Runbook

Target staging backend:

- `https://baseballclassic-production-4796.up.railway.app`

Run from the repository root with the checked-in `.venv`.

## 1. Baseline Health

```bash
curl https://baseballclassic-production-4796.up.railway.app/health
curl https://baseballclassic-production-4796.up.railway.app/ready
curl https://baseballclassic-production-4796.up.railway.app/debug/relay-stats
```

`/ready` must report both DB and Redis as connected before any load test.

## 2. DB Monitoring

Start this in a separate terminal before load starts. Use the staging Supabase
session pooler URL, not production.

```bash
STAGING_POSTGRES_URL='postgresql://postgres.egcsxoxqfcwjjcvjycry:<password>@aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres' \
  .venv/bin/python scripts/monitor_staging_db.py \
  --duration-sec 600 \
  --interval-sec 5
```

The monitor writes JSONL samples under `Test/staging_db_load_*.jsonl`.

Primary DB signals:

- active sessions and total backends
- waiting sessions and waiting locks
- idle-in-transaction sessions
- max query age
- transaction, rollback, block read/hit, and row operation deltas

## 3. Read-Only API Load

This simulates mobile/watch read traffic without writes.

```bash
.venv/bin/python scripts/load_read_api.py \
  --base-url https://baseballclassic-production-4796.up.railway.app \
  --steps 10,25,50,100,200 \
  --duration-sec 60 \
  --max-error-rate 0.01 \
  --max-p95-ms 1000
```

Stop at the first step where p95 latency or error rate exceeds the service
threshold, then inspect the DB monitor output for correlation.

## 4. WebSocket + Ingest Load

This uses the existing mutation test and requires the staging crawler API key.

```bash
.venv/bin/python scripts/test_ws_load.py \
  --backend-base-url https://baseballclassic-production-4796.up.railway.app \
  --backend-api-key '<staging-crawler-api-key>' \
  --clients-per-game 20 \
  --events-per-game 5 \
  --max-games 5 \
  --listen-sec 15
```

Scale `--clients-per-game` gradually. Total WebSocket clients are
`clients-per-game * max-games`.

Suggested progression:

- 10 clients/game = 50 total
- 20 clients/game = 100 total
- 50 clients/game = 250 total
- 100 clients/game = 500 total
- 200 clients/game = 1000 total

## 5. Capacity Verdict

Report the largest step that satisfies all criteria:

- API p95 latency within the chosen threshold
- HTTP error rate within the chosen threshold
- WebSocket connect success near 100%
- broadcast receipt near 100%
- DB has no sustained lock waits, runaway active sessions, or idle transactions

Use a lower operating number than the first failing step. For example, if
500 users pass and 1000 users starts dropping broadcasts, treat 500 as the
stable capacity unless the infrastructure is scaled.
