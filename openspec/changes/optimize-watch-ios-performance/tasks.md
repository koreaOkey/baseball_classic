# Tasks

## 코드 변경
- [x] 애니메이션 프레임 425개 번들 폴더 이동 + `AnimationFrameLoader`(contentsOfFile, off-main 로드) + 5개 전환 화면 교체
- [x] `GameData` Equatable + @Published 변경-시에만 할당 (`gameData`/`syncedTeamName`/`storeThemeId` 전 경로)
- [x] ongoing 노티 제거 호출 축소 (활성화 1회 + 종료 1회)
- [x] 폴링 단건 조회 전환 + scenePhase 연동 + DateFormatter/정규식 static 캐시
- [x] VICTORY 햅틱 취소 가능한 단일 Task 전환
- [x] print 38곳 → `wlog`(#if DEBUG) 전환
- [x] `stopExtendedSession` scheduled/notStarted invalidate 수정

## 영향 확인
- [x] 실시간성 회귀 없음: 데이터 변경 전파·햅틱·이벤트 경로 불변, 동일 값 재할당만 스킵
- [x] Extended Session 재시작 정책 불변 (별도 change로 유예)
- [x] Wear OS 영향 없음 (iOS 전용)
- [x] 백엔드 변경 불필요 확인 (`/games/{game_id}` 기존 존재)

## 검증
- [x] `xcodebuild` 워치 앱 + iOS 앱 빌드 통과
- [x] 워치 앱 번들에 AnimationFrames 425개 포함 확인
- [ ] 실기기: 애니메이션 재생 중 메모리 확인 + 라이브 경기 회귀 확인
