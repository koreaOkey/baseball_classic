#!/bin/sh
set -e

# --enable-preview-lineup-precheck: NAVER /preview API 의 previewData.{home,away}TeamLineUp.fullLineUp
# 또는 {home,away}Starter 가 노출되면 dispatcher 가 crawler 를 spawn. 라인업이 /relay 의 textRelays
# 가 비어있는 상태(경기 시작 전 라인업 공개 ~30분 전)에서도 잡힌다.
# crawler.py:496-502 가 이미 /preview 를 fetch 해서 relays_by_inning[1] 에 라인업을 주입하므로,
# spawn 만 발생하면 backend_sender 가 자동으로 lineupSlots 를 채워 백엔드에 ingest 한다.

set -- python crawler/live_baseball_dispatcher.py \
  --backend-base-url "${BACKEND_BASE_URL}" \
  --backend-api-key "${BACKEND_API_KEY}" \
  --schedule-target "${SCHEDULE_TARGET:-kbo}" \
  --dispatch-interval-sec "${DISPATCH_INTERVAL_SEC:-15}" \
  --crawler-interval-sec "${CRAWLER_INTERVAL_SEC:-15}" \
  --crawler-backend-timeout-sec "${CRAWLER_BACKEND_TIMEOUT_SEC:-8}" \
  --crawler-backend-retries "${CRAWLER_BACKEND_RETRIES:-1}" \
  --schedule-refresh-interval-sec "${SCHEDULE_REFRESH_INTERVAL_SEC:-21600}" \
  --schedule-import-days "${SCHEDULE_IMPORT_DAYS:-30}" \
  --import-retry-interval-sec "${IMPORT_RETRY_INTERVAL_SEC:-1800}" \
  --backend-sync-timeout-sec "${BACKEND_SYNC_TIMEOUT_SEC:-10}" \
  --backend-sync-retries "${BACKEND_SYNC_RETRIES:-1}" \
  --enable-preview-lineup-precheck \
  --log-dir log

if [ -n "${SCHEDULE_IMPORT_START_DATE:-}" ]; then
  set -- "$@" --schedule-import-start-date "${SCHEDULE_IMPORT_START_DATE}"
fi

if [ -n "${SCHEDULE_IMPORT_UNTIL:-}" ]; then
  set -- "$@" --schedule-import-until "${SCHEDULE_IMPORT_UNTIL}"
fi

if [ -n "${SCHEDULE_REFRESH_START_DATE:-}" ]; then
  set -- "$@" --schedule-refresh-start-date "${SCHEDULE_REFRESH_START_DATE}"
fi

if [ -n "${SCHEDULE_REFRESH_UNTIL:-}" ]; then
  set -- "$@" --schedule-refresh-until "${SCHEDULE_REFRESH_UNTIL}"
fi

# 파일 로그는 기본 비활성: Railway 가 stdout 을 수집한다.
# 로컬 디버깅 시 DISPATCHER_ENABLE_FILE_LOG=1 로 켜면 log/ 아래에 일 단위 회전(7일 보관) 파일이 남는다.
if [ -n "${DISPATCHER_ENABLE_FILE_LOG:-}" ]; then
  set -- "$@" --enable-file-log
fi

exec "$@"
