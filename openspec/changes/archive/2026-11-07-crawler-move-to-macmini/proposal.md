# 크롤러를 Railway에서 맥미니(집 IP)로 이전

## Why

11/3에 브라우저 TLS 위장으로 복구한 크롤러가 11/5 18:05 KST부터 다시 멈췄다. 한국시리즈 3차전(11/6)과 4차전(11/7)이 '경기 전'으로 남았다. 최신성 모니터가 11/7 02:00부터 텔레그램으로 알렸다.

- Railway 크롤러 로그에서 11/5 03:05Z 마지막 성공 이후 네이버 요청이 전부 `curl: (28) Operation timed out ... 0 bytes received`였다. 위장 요청까지 막혔다.
- 같은 위장 요청을 사용자가 집 네트워크(공인 121.138.76.13)의 맥북에서 실행하자 `game: OK 0.07s`, `schedule: OK 0.05s`로 정상 응답했다. 위장이 들킨 것이 아니라 네이버가 Railway 서버 IP(클라우드 대역)를 막은 것으로 판단했다.
- 일반 curl 요청은 집 IP에서도 여전히 무응답이다(10/26 이후 TLS 지문 차단은 그대로).
- 이전 대상은 spark와 맥미니를 비교해 맥미니로 정했다.
  - 맥미니는 위 확인과 같은 공인 IP라 이미 검증됐다.
  - 최신성 모니터가 도는 spark와 기기가 분리돼, 크롤러 기기가 멈춰도 알림이 온다.

## What Changes

- 맥미니(macOS 26.5, 시스템 잠자기 꺼짐)에서 크롤러를 실행한다.
  - 코드는 `~/baseball_crawler/crawler`(저장소 `crawler/` rsync)에 두고, Python 3.14 venv(`requests`, `curl_cffi`)로 실행한다.
  - launchd 사용자 에이전트 `com.basehaptic.crawler`(RunAtLoad, KeepAlive)로 상시 실행한다.
  - 래퍼 `run.sh`가 `.env`(Railway crawler_staging 변수 복사, 권한 600)를 읽고 `crawler/start.sh`를 실행한다.
  - 로그는 크롤러 자체 파일 로그(`log/`, 디스패처는 일 단위 7일, 경기별 파일은 14일 지나면 삭제)를 쓴다.
- 백엔드는 그대로 Railway(`-4796`)를 쓴다. 크롤러는 공개 URL로 ingest한다.
- `crawler/start.sh`에 `CRAWLER_DISABLED` 가드를 추가했다. Railway crawler_staging에 `CRAWLER_DISABLED=1`을 두면 staging 푸시로 재배포돼도 네이버를 호출하지 않고 대기만 한다. 크롤러 두 개가 동시에 도는 것을 막기 위해서다.
- 네이버 relay 차단 대응(라이브 스코어 유지 모드). 맥미니(집 IP)에서도 `/schedule/games/{id}/relay`만 0바이트 타임아웃이 났다. `event`·`boxscore`는 403이었고, 경기 상세·`preview`·`record`는 정상이었다. 브라우저에서는 문자중계가 보인다.
  - 크롤러: relay가 실패하면 그 폴은 relay 없이 경기 상세만으로 스냅샷을 보낸다. 실패 뒤 5분 동안은 relay를 다시 부르지 않는다.
  - 디스패처: relay를 못 받아도 경기 상세와 preview로 판정해 크롤러를 띄운다. 이전에는 fetch_failed로 띄우지 않았다.
  - 스냅샷: relay가 없을 때 안타·실책은 `{side}TeamRheb`, 라인스코어는 `{side}TeamScoreByInning`, 현재 투수는 "N회초/말" 기준 `{side}CurrentPitcherName`으로 채운다.
  - 이 모드에서는 투구·타석 이벤트(햅틱·이벤트 푸시)가 없다. 앱에는 스코어·이닝·상태·라인스코어만 실시간으로 반영된다.
- relay 브라우저 브리지(사용자 선택 3번).
  - 맥미니에 실제 Chromium(Playwright, 창 표시)을 launchd `com.basehaptic.relay-bridge`로 상주시킨다.
  - 브리지는 네이버 중계 페이지를 띄워 두고 `127.0.0.1:8790/fetch?url=`로 받은 api-gw URL을 그 페이지 안에서 fetch해 응답을 그대로 돌려준다. 허용하는 주소는 api-gw https만이다.
  - 크롤러는 `NAVER_RELAY_BRIDGE_URL`이 있으면 relay URL만 브리지로 보낸다. 받은 JSON은 예전 relay와 같아서 기존 이벤트 분류·햅틱 경로가 그대로 동작한다.
  - 브리지가 실패하면 위 스코어 유지 모드로 내려간다.
  - 네이버가 일부러 브라우저만 통과시키는 경로를 자동화한 브라우저로 넘는 방식이라, 약관 위반 위험이 이전 단계보다 크다(사용자 감수).
- spark `~/baseball_crawler`에도 같은 준비(venv, systemd unit 파일)가 있다. 켜지 않은 예비로 둔다.

## Capabilities

### crawling

- 크롤러는 클라우드 IP 차단과 관계없이 국내 가정용 IP에서 경기 일정·중계를 수집한다.
- 크롤러를 여러 곳에 배포해도 지정된 한 곳에서만 수집한다.

## Impact

- 맥미니 상주 프로세스 1개 추가. 네이버 요청량은 Railway에서 돌 때와 같다(중계 15초, 일정 6시간).
- 맥미니가 꺼지거나 Wi-Fi가 끊기면 수집이 멈춘다. 이때는 spark 최신성 모니터가 알린다.
  - 정전 뒤 자동 켜짐은 꺼져 있다(`autorestart 0`).
  - launchd 사용자 에이전트는 로그인 세션에서 돈다. 재부팅 뒤 자동 로그인이 안 되면 로그인할 때까지 멈춘다.
- 집 공인 IP를 같이 쓴다. 네이버가 이 IP를 막으면 집에서 쓰는 네이버 스포츠에도 영향이 갈 수 있다.
- 코드 배포 경로가 둘로 나뉜다: Railway는 staging 푸시, 맥미니는 rsync 후 `launchctl kickstart -k gui/501/com.basehaptic.crawler`.
- 모바일/워치 영향 없음: 백엔드 API·WS 무변경.
- 롤백: 맥미니 에이전트 중지(`launchctl bootout`) → Railway `CRAWLER_DISABLED` 삭제 후 재배포.
