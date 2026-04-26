# Tasks

## 코드 변경
- [x] `backend/api/app/event_bus.py`: `connect()`에 try/except 추가, bool 반환
- [x] `backend/api/app/event_bus.py`: `safe_send` 클라이언트 끊김 로그 WARNING → DEBUG
- [x] `backend/api/app/main.py:websocket_game_stream`: try/finally + accept 실패 조기 return
- [x] `backend/api/app/main.py:websocket_team_record_stream`: 동일 패턴 적용

## 검증
- [x] `python3 -c "import ast; ast.parse(...)"` 문법 통과
- [ ] 로컬 ws 핸드셰이크 도중 끊김 재현 후 깔끔히 disconnect 되는지 확인 (생략 가능)
- [ ] Railway 배포 후 `RuntimeError: WebSocket is not connected` 에러 사라지는지 30분 관찰
- [ ] 24~48시간 메모리 그래프 관찰 — 평탄·완만 우상향 vs 600 MB 한계 도달 패턴 비교

## 후속 (별도 change)
- [ ] `healthcheckPath` 설정으로 죽은 컨테이너 빠른 감지
- [ ] 메모리 80% 알림 설정 (Railway)
- [ ] Sentry 등 에러 트래킹 도입 검토 (이번 같은 사건 즉시 알림)
