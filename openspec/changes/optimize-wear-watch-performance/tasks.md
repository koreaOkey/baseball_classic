# Tasks

## 코드 변경
- [x] FGS 실행 가드 + 30분 무수신 워치독
- [x] ongoing 알림 단일 빌드 + OngoingActivity 메타데이터 게시 버그 수정 + 내용 diff 재게시 생략
- [x] `ACTION_GAME_UPDATED` 리시버 1개로 통합
- [x] 앰비언트 3분 폴링 + 해제 시 즉시 재개 + `/games/{gameId}` 단건 조회 전환
- [x] SCREEN_BRIGHT 웨이크락 제거 (setTurnScreenOn 위임)
- [x] ExoPlayer 지연 생성 + 클립 종료 시 stop()/clearMediaItems()
- [x] `KEY_IS_LIVE` 실제 기록으로 "이미 관람 중" 가드 복원
- [x] 설정 prefs 단일 Editor 배칭
- [x] 미참조 팀 로고 PNG 10개 삭제 (전체 grep으로 미참조 확인)
- [x] 의존성 없는 tiles/protolayout ProGuard keep 제거

## 영향 확인
- [x] 실시간성 회귀 없음: 데이터 전파·진동·브로드캐스트 경로 불변, 동일 내용 재게시/재시작만 스킵
- [x] `FLAG_KEEP_SCREEN_ON`·stale isLive 정책 미변경 (별도 change로 유예)
- [x] iOS 영향 없음 (Wear 전용)
- [x] 백엔드 변경 불필요 확인

## 검증
- [x] `:app:assembleDebug` 빌드 통과
- [x] `:app:assembleRelease` (R8+shrinkResources) 빌드 통과
- [ ] 실기기: 화면 꺼진 상태에서 이벤트 수신 시 화면 웨이크 확인 (웨이크락 제거 검증)
- [ ] 실기기: 라이브 경기 회귀 확인 (ongoing 칩, FGS 수명, 더블헤더 폴링 복귀)
