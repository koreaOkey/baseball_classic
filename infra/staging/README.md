# Staging Infrastructure

Staging mirrors production with separate Railway and Supabase resources. Do not copy
`backend/api` or `crawler` into staging-specific source folders. Deploy the same code
with staging-only variables.

```text
Naver relay
  -> Railway staging crawler
  -> Railway staging backend
  -> staging Supabase + staging Railway Redis
  -> iOS/watchOS Debug build
```

Production keeps its own Railway services, Supabase project, Redis service, and
Release app build settings.

## 1. Supabase

Create a new Supabase project for staging. Apply the schema to the fresh staging
database before starting crawler traffic.

Current staging Supabase project:

- ref: `egcsxoxqfcwjjcvjycry`
- API URL: `https://egcsxoxqfcwjjcvjycry.supabase.co`
- region: `ap-northeast-1`
- session pooler host: `aws-1-ap-northeast-1.pooler.supabase.com`

Use a psql-compatible connection string, not the SQLAlchemy
`postgresql+psycopg://` backend URL. The direct DB host resolves to IPv6 on this
machine, so use the session pooler host:

```bash
STAGING_POSTGRES_URL='postgresql://postgres.egcsxoxqfcwjjcvjycry:<password>@aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres' \
  infra/staging/apply_migrations.sh
```

The script refuses to run if the URL contains the known production project ref.

## 2. Railway

Create a persistent `staging` environment or a separate staging project.

Recommended services:

- `baseball_classic`: deploys `backend/api`
- `crawler_staging`: deploys `crawler`
- `Redis-GGli`: Railway Redis for staging fanout

Current active staging backend public URL:

- Railway project: `baseball-classic-staging`
- Railway project id: `7eafbc63-5b94-4943-a323-f6228033e555`
- Railway environment: `production` (Railway default environment inside the
  staging project; isolated from the real production project)
- backend URL: `https://baseballclassic-production-4796.up.railway.app`

The production Railway project `overflowing-solace` must not contain staging
services or staging variables.

Set service source/build configuration in the Railway dashboard:

`baseball_classic`

- Source repo: `koreaOkey/baseball_classic`
- Branch: `staging`
- Root directory: `/backend/api`
- Builder: Railpack/Nixpacks
- Start command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT --workers 1`
- Healthcheck path: `/health`

`crawler_staging`

- Source repo: `koreaOkey/baseball_classic`
- Branch: `staging`
- Root directory: `/crawler`
- Builder: Dockerfile
- Dockerfile path: `/crawler/Dockerfile`

Use the public backend URL for `BACKEND_BASE_URL` unless a private endpoint is
explicitly configured for `baseball_classic`.

Import variables from:

- `backend.env.example`
- `crawler.env.example`

Replace every placeholder with staging values. Never paste production database,
Redis, crawler key, or service role secrets into staging.

Sensitive variables such as DB password, service role key, and crawler API key
must be entered in the Railway dashboard or another approved secret manager. Do
not commit them to this repository.

## 3. iOS/watchOS/Android Debug

iOS/watchOS Debug builds read staging backend settings through Xcode build settings:

```bash
xcodebuild -project ios/BaseHaptic.xcodeproj \
  -scheme BaseHaptic \
  -configuration Debug \
  BASEHAPTIC_STAGING_BACKEND_BASE_URL=https://baseballclassic-production-4796.up.railway.app \
  BASEHAPTIC_STAGING_BACKEND_WS_URL=wss://baseballclassic-production-4796.up.railway.app \
  BASEHAPTIC_STAGING_SUPABASE_URL=https://egcsxoxqfcwjjcvjycry.supabase.co \
  BASEHAPTIC_STAGING_SUPABASE_ANON_KEY=<staging-anon-key>
```

If these values are not provided, the iOS/watchOS app falls back to
`http://localhost:8080`. Release builds keep the production Railway URL and
production Supabase project.

Android Debug builds use the staging Railway backend and staging Supabase project
through Gradle `debug` build type defaults. Override them through environment
variables or Gradle properties when needed:

```bash
BASEHAPTIC_STAGING_BACKEND_BASE_URL=https://baseballclassic-production-4796.up.railway.app \
BASEHAPTIC_STAGING_SUPABASE_URL=https://egcsxoxqfcwjjcvjycry.supabase.co \
BASEHAPTIC_STAGING_SUPABASE_ANON_KEY=<staging-publishable-key> \
./gradlew :mobile:assembleDebug
```

Equivalent `local.properties` keys are `stagingBackendBaseUrl`,
`stagingSupabaseUrl`, and `stagingSupabaseAnonKey`. Android Release builds keep
the production Railway URL and production Supabase project.

## 4. Verification

After deployment:

```bash
curl https://baseballclassic-production-4796.up.railway.app/health
python backend/api/scripts/simulate_crawler.py \
  --base-url https://baseballclassic-production-4796.up.railway.app \
  --api-key <staging-crawler-api-key> \
  --naver-pitch-detail-once \
  --game-id 20260607KTSK02026
curl "https://baseballclassic-production-4796.up.railway.app/games/20260607KTSK02026/events?inningNumber=5&limit=20"
```

Expected result:

- backend health returns OK
- fixture ingest returns inserted events
- event response includes `atBatId`, `seqno`, pitch speed, pitch stuff, BSO, and win probability fields
- production Supabase tables remain unchanged
