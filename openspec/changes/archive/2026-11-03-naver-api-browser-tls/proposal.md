# 네이버 스포츠 API 브라우저 TLS 전환 (크롤링 전면 정지 복구)

## Why

2026-10-26 00:05 KST 무렵부터 야구봄 경기 데이터가 전혀 갱신되지 않았다. 11/3에 확인할 때까지 플레이오프 2~5차전(삼성-KIA)과 한국시리즈 1차전(KT-삼성)이 앱에 들어오지 않았다.

- 백엔드·DB·Redis와 Railway 3개 서비스는 모두 정상이었다. 크롤러 배포도 9/11 그대로였다.
- 크롤러 로그(Railway 7일 보관, 10/27 이후)에서 `api-gw.sports.naver.com` 요청이 전부 Read timed out이거나 RemoteDisconnected로 끝났다. 마지막 5,000줄 기준 타임아웃 2,970건, 연결 끊김 213건이다. 일정 import가 매번 incomplete로 끝나 오늘 경기 0건으로 판정했고, 그래서 중계 크롤러가 뜨지 않았다.
- 맥(국내 IP)에서 curl로 호출해도 TLS 핸드셰이크 뒤 무응답이었다. `naver.com`과 `m.sports.naver.com`은 정상이었다. 11/3에 빌드된 네이버 웹 번들도 같은 `api-gw` 호스트를 쓴다. 엔드포인트 이전이 아니라 브라우저가 아닌 TLS 지문을 차단한 것으로 판단했다.
- 사용자가 직접 Chrome TLS 위장(curl_cffi) 요청을 보냈을 때는 200과 정상 JSON이 돌아왔다(한국시리즈 1차전 KT 8-7 삼성).
- 대체 소스·유료 데이터·대기 중에서 사용자가 브라우저 TLS 위장을 선택했다. 네이버 이용 약관 위반 위험은 사용자가 감수하기로 했다.

## What Changes

- `crawler/naver_http.py` 추가: curl_cffi `impersonate="chrome"` 세션으로 JSON GET을 보낸다. 타임아웃·연결 실패·HTTP 4xx/5xx·JSON 파싱 실패는 각각 requests 예외로 바꿔 올린다. 기존 호출부의 `except requests.RequestException` 처리를 그대로 유지하기 위해서다.
- `crawler/crawler.py` `fetch_json`(경기·중계·프리뷰)과 `crawler/live_wbc_dispatcher.py`의 네이버 GET 5곳(`_safe_json_get`, 일정, 캘린더, 캘린더 경기 상세, 팀 순위)을 `naver_http.get_json`으로 교체했다. 자체 UA 헤더(`BaseballDispatcher/1.0`, `BaseballClassicCrawler/1.0`)는 빼고 위장 브라우저 UA를 쓴다.
- 백엔드로 보내는 GET·POST(`/games` 조회, ingest, 일정 스냅샷)는 기존 requests를 그대로 쓴다. 단 `_safe_json_get`을 거치는 백엔드 `/games` 조회 1곳은 같은 클라이언트를 공유한다.
- `crawler/requirements.txt`에 `curl_cffi==0.16.2` 추가(manylinux x86_64 abi3 휠 확인).
- 조회 주기(일정 6시간, 중계 15초)는 바꾸지 않았다.

## Capabilities

### crawling

- 크롤러는 네이버 스포츠 API가 브라우저가 아닌 클라이언트를 차단해도 경기 일정·중계·프리뷰·팀 순위를 계속 수집한다.
- 네이버 요청이 실패하면 변경 전과 같은 경로로 재시도하고 로그를 남긴다.

## Impact

- Railway `crawler_staging` 서비스(Dockerfile 재빌드). 백엔드·DB 스키마는 바꾸지 않았다.
- 모바일/워치 영향 없음: API·WS 프로토콜을 바꾸지 않았다. 크롤러가 복구되면 기존 앱이 그대로 데이터를 받는다.
- 위험: 네이버가 차단 기준을 더 올리면 다시 멈춘다. 이번처럼 헬스체크는 정상인데 데이터만 멈추는 장애는 기존 모니터에 잡히지 않는다.
- 롤백: 이 커밋을 되돌리고 staging에 푸시한다. 다만 되돌리면 차단 상태로 돌아간다.
