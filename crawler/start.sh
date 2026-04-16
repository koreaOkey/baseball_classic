#!/bin/sh
set -e

exec python crawler/live_baseball_dispatcher.py \
  --backend-base-url "${BACKEND_BASE_URL}" \
  --backend-api-key "${BACKEND_API_KEY}" \
  --schedule-target "${SCHEDULE_TARGET:-kbo}" \
  --dispatch-interval-sec "${DISPATCH_INTERVAL_SEC:-15}" \
  --crawler-interval-sec "${CRAWLER_INTERVAL_SEC:-15}" \
  --schedule-import-days "${SCHEDULE_IMPORT_DAYS:-7}" \
  --log-dir log
