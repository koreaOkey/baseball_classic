# 백엔드 메모리 비용 추가 절감 (야간 재배포 + malloc_trim)

## Why

워커 1개 + `MALLOC_ARENA_MAX=2`(2026-09-09) 적용 후 백엔드 RAM 플래토는 ~1.0 GB에서 ~0.59 GB로 내려갔으나, 여전히 경기 밤마다 한 계단 오르고 내려오지 않는다(FCM 재배포 리셋 후 낮 0.1 GB → 경기 뒤 0.59 GB 고착). 24시간 평균 0.51 GB로 Hobby 포함분 0.35 GB 목표를 넘어, 월 usage가 ~$6~7(변경 전 ~$9.2 대비 약 33% 절감)에 머문다.

코드 정적 점검 결과 경기별로 무한 누적되는 인메모리 구조(LA 발송 dict·WS 연결·캐시 락·날씨 캐시)는 모두 정리/상한이 있어 논리적 누수가 없다. 즉 고착의 실체는 경기 중 대량 스레드·DB 처리로 생긴 **glibc 힙 단편화**가 RSS로 남아 OS에 반환되지 않는 것이다.

$6~7 수준은 수용하되(변경 전 대비 1/3 절감), 두 가지로 추가 개선한다.

## What Changes

### 1) 야간 재배포 (인프라, spark cron)

- 매일 05:00 KST에 Railway `serviceInstanceRedeploy`로 백엔드를 재기동해 메모리를 바닥으로 리셋한다. 빌드 없이 기존 이미지로 새 컨테이너를 띄우고 Railway가 `/health` 통과 후 트래픽을 넘겨 무중단이다.
- 04:00 데이터 정리와 겹치지 않고, 05:00엔 경기·ingest·WS 접속이 없다. HTTP 캐시·LA 상태·DB 백오프는 Redis에, 그 외는 재시작 시 재구성(날씨 프리웜 10초)된다.
- spark cron이 `railway_redeploy.sh`를 호출한다. spark가 꺼져 있으면 그날만 건너뛰고 무해하다(멱등·안전).

### 2) 주기적 malloc_trim (백엔드 코드)

- 백그라운드 루프가 `memory_trim_interval_sec`(기본 600초)마다 glibc `malloc_trim(0)`을 호출해 반환되지 않은 힙 메모리를 OS로 돌려준다. Linux/glibc 전용이며 그 외 환경은 자동 no-op.
- trim 전후 RSS를 로그(`[mem-trim] rss_before/after/freed`)로 남겨 단편화 여부와 효과를 직접 확인한다(계측 겸용).
- `memory_trim_enabled`(기본 on)로 끌 수 있다.

## Capabilities

### backend-cost

- 경기 후 고착된 힙 메모리를 주기적으로 OS에 반환하고, 야간 재배포로 매일 바닥을 리셋하여 백엔드 RAM 사용량이 다일 누적되지 않는다.
- 메모리 반환 처리는 glibc/Linux 전용이며 다른 환경에서는 서비스 동작에 영향을 주지 않는다.

## Impact

- `backend/api/app/config.py` — `memory_trim_enabled`(기본 True), `memory_trim_interval_sec`(기본 600) 추가.
- `backend/api/app/main.py` — `_get_libc`/`_malloc_trim`/`_read_rss_bytes`/`_memory_trim_loop` 추가, lifespan에 태스크 등록. `ctypes`/`os`/`sys` 임포트.
- spark: `/home/lee/baseball_monitor/railway_redeploy.sh` + crontab `0 5 * * *`(KST). 저장소 외 인프라.
- Railway 백엔드 재배포 필요(코드 변경). 모바일/워치 영향 없음(API·프로토콜 무변경).
- 검증: pytest 143건 통과, malloc_trim/RSS 헬퍼 mac no-op 스모크, 재배포 토큰 유효성 확인.
