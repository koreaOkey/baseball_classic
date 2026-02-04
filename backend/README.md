# Backend

크롤러가 적재한 데이터를 기반으로 앱(스마트폰/워치)에 **조회 API / 실시간 이벤트**를 제공하는 백엔드 영역입니다.

## 목표(예정)
- **REST API**: 게임 상태/최신 이벤트 조회
- **실시간 전송**: WebSocket 또는 푸시(FCM) 연동
- **인증/권한**: 사용자 토큰(JWT 등) + 크롤러 서비스 키 분리

## 폴더
- `api/`: 백엔드 API 서버(추가 예정)

## MVP 최소 API 예시(초안)
- `GET /health`
- `GET /games/{gameId}`
- `GET /games/{gameId}/state`
- `GET /games/{gameId}/events?after=<cursor>`

