## 1. 워치 동기화 플로우 전환
- [x] 1.1 모바일: 관심 팀 LIVE 전환 시 /watch/prompt/current 경로로 프롬프트 전송
- [x] 1.2 워치: "관람하겠습니까?" 동기화 동의 팝업 구현
- [x] 1.3 워치: 수락/거부 응답을 /watch/sync-response/{timestamp}로 모바일에 전송
- [x] 1.4 모바일: 수락 시 syncedGameId 저장 후 실시간 동기화 시작

## 2. Data Layer 양방향 통신
- [x] 2.1 MobileDataLayerListenerService 구현
- [x] 2.2 WearWatchSyncBridge pending 상태 관리/consume 처리
- [x] 2.3 WatchSyncResponseSender 구현

## 3. 워치 반응형 UI
- [x] 3.1 WatchUiProfile 데이터 클래스 정의 (40+ 디멘션 파라미터)
- [x] 3.2 3종 디바이스 프로파일 적용 (wearos_small_round, wearos_large_round, wearos_square)
- [x] 3.3 LiveGameScreen, NoGameScreen, 동기화 팝업, 이벤트 오버레이에 프로파일 적용

## 4. 빌드 검증
- [x] 4.1 :mobile:compileDebugKotlin 성공
- [x] 4.2 :watch:compileDebugKotlin 성공
- [x] 4.3 QA 체크리스트 작성 (SCREEN_QA.md)
