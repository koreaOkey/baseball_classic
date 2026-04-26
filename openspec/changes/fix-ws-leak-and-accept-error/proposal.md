## Why

2026-04-26 KST 14:04 ~ 17:25 (약 3시간 20분) 동안 baseball_classic API가 사실상 다운되어 안드로이드/iOS 앱 모두 라이브 경기 데이터를 받지 못함. 사용자 앱 재시작도 무용. Railway 메트릭 분석 결과 메모리가 600 MB 부근에서 평탄하게 한계에 닿아 있다가 17:25에 컨테이너가 강제 재시작되어 480 MB로 리셋되는 패턴 확인. CPU는 거의 0 — 메모리 누수가 원인.

코드 리뷰 결과 두 가지 결함 확인:

1. **WebSocket 연결 누수** — `app/main.py`의 `websocket_game_stream` / `websocket_team_record_stream` 핸들러가 `WebSocketDisconnect`만 except로 잡고 있어, 그 외 예외(RuntimeError, ConnectionResetError, CancelledError 등)가 발생하면 `event_bus.disconnect()`가 호출되지 않음. `_connections` set과 `_send_locks` dict에 좀비 WebSocket 객체가 영구 누적되어 메모리 누수.

2. **Accept 핸드셰이크 도중 클라이언트 끊김 시 RuntimeError 폭발** — `event_bus.connect()`의 `await websocket.accept()`가 클라이언트 조기 절단으로 실패하면 Starlette 라우팅 wrapper가 close()를 시도하다 `RuntimeError: WebSocket is not connected. Need to call "accept" first.`를 던짐. 동일 시간대 deploy log에 동일 에러 수십 건 발견.

## What Changes

- `event_bus.connect()` 시그니처를 `bool`로 변경: accept 실패 시 예외를 흡수하고 `False` 반환.
- 두 WebSocket 핸들러를 `try/finally` 구조로 재작성: 어떤 예외가 발생해도 `event_bus.disconnect()`가 반드시 호출되도록 보장. accept 실패(`connect()`가 False 반환) 시에는 register/disconnect 모두 건너뛰고 조기 return.
- `safe_send`의 클라이언트 끊김 경고를 WARNING → DEBUG로 격하: broadcast 도중 자연스럽게 발생하는 정상 케이스라 운영 로그 노이즈만 키움.

## Capabilities

### Modified Capabilities
- `live-game-websocket`: 핸드셰이크 실패와 핸들러 비정상 종료 시 항상 정리되도록 수정. 외부 동작은 동일.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| `try/finally`로 감싼 후 register 전 예외 발생 시 disconnect가 실패 처리될 수 있음 | `event_bus.disconnect()`가 `discard()` 기반이라 등록 안 된 ws에 대해서도 안전 |
| `safe_send`의 WARNING → DEBUG로 진짜 송신 실패 추적이 어려워짐 | `stats["send_fail"]` 카운터는 그대로 유지. `/healthz` 등에서 누적 통계로 모니터링 가능 |
| 워커 6개 유지 — 동일 부하 시 다시 OOM 가능성 | 누수 패치로 평소 메모리 증가가 잡힐 것으로 기대. 추후 24~48시간 모니터링 후 워커 수 조정 또는 플랜 업그레이드 검토 |

## Status

- [x] 구현 완료 (`event_bus.py`, `main.py`)
- [x] 문법 체크 (ast.parse)
- [ ] 커밋 및 Railway 배포
- [ ] 메모리 그래프 24~48시간 관찰 (재발 시 워커 수 ↓ 또는 플랜 업그레이드)
