# Tasks — harden-rewarded-ad-gate-reliability

## 1. iOS
- [x] 1.1 `AdDelegate` 1회 발화 가드(`hasFired`) — 이중 콜백/continuation 이중 resume 차단
- [x] 1.2 `isPresenting` busy 가드 — 광고 표시 중 delegate 덮어쓰기 차단
- [x] 1.3 로드 워치독(60초) + 세대 카운터 — 늦게 도착한 로드 폐기, isLoading 영구 잔존 방지
- [x] 1.4 완파 화면 denied 안내 캡션("광고를 끝까지 보면 재도전할 수 있어요", 재요청 시 해제)

## 2. Android
- [x] 2.1 `complete` 멱등화(`settled`) — 전 게이트 이중 지급 차단
- [x] 2.2 로드 워치독(60초, main handler) + `markLoadFinished` — 전역 오버레이 영구 잔존 방지
- [x] 2.3 완파 화면 denied Toast 안내

## 3. 검증
- [x] 3.1 iOS Release 빌드 통과 (BaseHaptic 스킴, generic/platform=iOS)
- [x] 3.2 Android `:mobile:assembleRelease` 통과
- [ ] 3.3 실기기(DEBUG=테스트 광고): 광고 중도 이탈 시 안내 노출·재시도 가능, 정상 시청 시 룸 재진입 회귀 확인
- [ ] 3.4 실기기: 비행기 모드에서 60초 내 LOAD_FAILED 폴백(무료 허용) 동작 확인
