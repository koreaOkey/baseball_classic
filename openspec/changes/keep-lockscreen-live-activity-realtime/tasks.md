# Tasks

## 1. iOS frequent updates + stale 가시화

- [x] 1.1 위젯 확장 Info.plist 에 `NSSupportsLiveActivitiesFrequentUpdates=YES` (`ios/liveactivity/BaseHapticLiveActivity/Info.plist`)
- [x] 1.2 위젯 `context.isStale` 전파 (ActivityContentView → SupplementalAware → LockScreen 뷰) + 이벤트 라벨 자리에 주황 "동기화 지연" 표시
- [x] 1.3 `LiveActivityManager` staleDate 상수화(180s) — start/update/preview/highlight-clear 전 경로 통일 (기존 nil/60s 혼재)

## 2. 백엔드 발송 정책

- [x] 2.1 `apns.py` — `send_live_activity_push(_with_result)` 에 `priority`(기본 10)·`stale_seconds`(기본 180) 파라미터, `aps["stale-date"]` 포함
- [x] 2.2 `main.py` — `_la_is_significant`: 볼/스트라이크/타자/이벤트타입만 변한 경우 routine, 그 외 필드 변화 또는 주요 이벤트 타입이면 significant
- [x] 2.3 `main.py` — `_send_live_activity_update`: Redis last-state 로 동일 상태 스킵, heartbeat(60s) 경과 시 저우선 재전송, significant→10 / routine→5, `end`→10, 발송 후 last-state 기록 (TTL 6h)
- [x] 2.4 Redis 미가용 시 우아한 저하 확인 — get_cache None → 매번 significant p10 (기존 동작과 동일)

## 3. 검증

- [x] 3.1 백엔드 테스트: `_la_is_significant` 분류 + priority/중복스킵/heartbeat/end 시나리오 (`test_api.py` 2건 추가, 전체 115 passed)
- [x] 3.2 iOS 시뮬레이터 빌드 통과 (BaseHaptic 스킴 = 앱 + LiveActivity 확장)

## 4. 배포·실기기 검증

- [x] 4.1 백엔드 staging 푸시 (= 프로덕션 배포) — d7ecde5f 배포 완료 (8/18 21:46 KST)
- [ ] 4.2 iOS 앱 업데이트 배포 (frequent updates 엔타이틀먼트는 앱 배포 후 유효)
- [ ] 4.3 실기기: 잠금 상태로 1시간+ 방치 시 스코어·주자·아웃 계속 갱신 확인
- [ ] 4.4 실기기: 네트워크 차단 등으로 push 중단 시 180초 후 "동기화 지연" 라벨 확인
- [ ] 4.5 Railway 로그 `[APNs-LA]` 발송 라인으로 코얼레싱·heartbeat 동작 확인

## 6. 실기기 1차 검증 반영 (2026-08-18 밤)

- [x] 6.1 발견: p5 일상 갱신·heartbeat 가 잠금 상태에서 전달 지연/유실 → p10(득점·아웃 등) 사이 간격이 180s 를 넘을 때마다 "동기화 지연" 빈발. APNs 발송 실패는 0건 (전달 문제는 기기/우선순위 단)
- [x] 6.2 정책 개정: 전부 priority 10 + 볼카운트성 갱신 최소 간격 20s 코얼레싱 (엔타이틀먼트 빌드 전제, 구버전 앱은 기존 스로틀링과 동일 수준)
- [x] 6.3 `[APNs-LA] game=... sent=n/m significant=... event=...` 발송 로깅 추가 (4.5 검증용)
- [x] 6.4 테스트 개정 (코얼레싱 스킵/간격 경과 발송) — 115 passed

## 5. 후속 change 후보

- [ ] 5.1 `frequentPushesEnabled` 플래그를 토큰 등록에 포함 → OFF 토큰은 주요 이벤트만 발송 (live_activity_tokens 컬럼 추가 필요)
