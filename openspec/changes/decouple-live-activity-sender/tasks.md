# Tasks

## 1. 백엔드 발송기 모듈 (신규)

- [x] 1.1 `redis_bus.py` — 경기별 단기 락 헬퍼(`SET NX PX`, 소유 토큰 검증 후 해제)와 last-state 키 SCAN 헬퍼 추가. Redis 미가용 시 락은 "획득" 으로, SCAN 은 빈 목록으로 저하
- [x] 1.2 `live_activity_sender.py` — last-state 레코드 확장(`seq`·`ts`·`ingestAt`·`lastEvent`, 구 레코드 호환), 경기별 `asyncio.Lock` + 최신 pending 슬롯 drain, Redis 락, seq 기반 `skip_stale`, 기존 `skip_unchanged`/`skip_coalesce`/significant 게이트 이관, APNs timestamp 단조 증가(`max(now, last.ts+1)`), stale-date = ts+180
- [x] 1.3 코얼레싱 trailing flush — routine 스킵 시 남은 간격 뒤 pending 최신 상태를 재제출하는 경기당 단일 지연 태스크
- [x] 1.4 타이머 heartbeat 루프 — 15s 틱, last-state SCAN, LIVE 이고 `ingestAt` 10분 이내이고 `sentAt` 60s 경과 시 락 획득 후 같은 상태를 새 ts/stale-date 로 재발송(`heartbeat` 사유)
- [x] 1.5 결과 로그 한 줄 형식(`result=` 사유, `sent=n/m`, `significant`, `event`, `lag_ms`, `ts`)과 fire-and-forget 태스크 집합 관리·예외 로깅

## 2. main.py 통합

- [x] 2.1 ingest 후처리에서 체인 끝 `_send_live_activity_update` add_task 를 제거하고, 체인 첫 항목으로 발송 태스크를 띄우는 디스패처를 추가(seq·ingestAt = 커밋 직후 벽시계 캡처, `end` 이벤트 유지)
- [x] 2.2 lifespan 에 heartbeat 루프 등록·종료 시 취소
- [x] 2.3 `_la_is_significant`·상수·`_send_live_activity_update` 를 새 모듈로 이관하고 main.py 에 호환 별칭 유지(기존 import·테스트 경로 보존)

## 3. 테스트

- [x] 3.1 기존 `test_send_live_activity_update_coalesce_dedupe_heartbeat` 를 새 경로 기준으로 개정
- [x] 3.2 신규 테스트: 오래된 seq 차단, trailing flush, heartbeat 재발송·ts 단조 증가, 락 경합 시 단일 발송, ingest 10분 초과 시 heartbeat 중단, 종료 경기 heartbeat 제외, 토큰 없음
- [x] 3.3 backend pytest 전체 통과 (143 passed, 2026-09-09)

## 4. 플랫폼 영향 확인

- [x] 4.1 iOS 폰: liveactivity 페이로드 필드·priority 10·stale-date 180s 가 동일해 배포된 위젯 ContentState 디코딩에 영향 없음 확인. 폰·워치 사일런트 푸시 경로(`_send_push_for_game_events`) 무변경 확인
- [x] 4.2 Android 모바일·Wear OS: 이 경로를 쓰지 않음(FCM ongoing 노티·워치 데이터 푸시 무영향) 확인
- [x] 4.3 DB 마이그레이션 없음 확인(Redis 키 `live_activity_lock:*` 추가만)

## 5. 배포·검증

- [ ] 5.1 경기 없는 시간대에 staging 푸시(= 프로덕션) → Railway 배포 SUCCESS, `/health` 정상
- [ ] 5.2 다음 경기 Railway `[APNs-LA]` 로그로 검증: `lag_ms` 수 초 이내, `skip_stale`·`lock_busy` 빈도, 180s 초과 발송 공백 소멸, 이닝 교대 구간 `heartbeat` 발송 확인
- [ ] 5.3 실기기: 잠금 1시간+ 방치 시 이닝 교대·투수 교체 구간에서 "동기화 지연" 미표시, 스코어·이닝 최신 유지 확인
- [ ] 5.4 검증 후 openspec archive + 8/18 change 의 heartbeat 조항을 이 change 기준으로 main spec 동기화
