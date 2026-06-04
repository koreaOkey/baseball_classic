#!/bin/sh
set -e

# --enable-preview-lineup-precheck: NAVER /preview API 의 previewData.{home,away}TeamLineUp.fullLineUp
# 또는 {home,away}Starter 가 노출되면 dispatcher 가 crawler 를 spawn. 라인업이 /relay 의 textRelays
# 가 비어있는 상태(경기 시작 전 라인업 공개 ~30분 전)에서도 잡힌다.
# crawler.py:496-502 가 이미 /preview 를 fetch 해서 relays_by_inning[1] 에 라인업을 주입하므로,
# spawn 만 발생하면 backend_sender 가 자동으로 lineupSlots 를 채워 백엔드에 ingest 한다.
exec python crawler/live_baseball_dispatcher.py \
  --backend-base-url "${BACKEND_BASE_URL}" \
  --backend-api-key "${BACKEND_API_KEY}" \
  --schedule-target "${SCHEDULE_TARGET:-kbo}" \
  --dispatch-interval-sec "${DISPATCH_INTERVAL_SEC:-15}" \
  --crawler-interval-sec "${CRAWLER_INTERVAL_SEC:-15}" \
  --schedule-import-days "${SCHEDULE_IMPORT_DAYS:-7}" \
  --enable-preview-lineup-precheck \
  --log-dir log
