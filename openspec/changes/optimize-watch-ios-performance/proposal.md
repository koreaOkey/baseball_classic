## Why

watchOS 앱 성능 감사(2026-07-16)에서 배터리·메모리·CPU를 낭비하는 구조적 문제가 확인됐다. 핵심: (1) 펭귄 애니메이션 프레임 425개를 `UIImage(named:)`로 로드해 수동 evict가 무효화되고 시스템 캐시에 최대 ~64MB가 쌓여 jetsam 강제 종료 위험, (2) 경기 시작 감지 폴링이 gameId를 알고 있어도 매 30초 하루 전체 경기 목록(limit=100)을 재다운로드, (3) 매 투구 push마다 값이 같아도 @Published 재할당으로 전체 뷰 트리 무효화 + 비활성화된 ongoing 노티 제거 XPC 2회 호출, (4) release 빌드에 print 38곳(push 페이로드 전체 직렬화 포함).

실시간성·앱 유지에는 영향이 없는 순수 낭비 제거만 포함한다. Extended Runtime Session 무한 연장 문제는 별도 change(`bound-watch-ios-extended-runtime-session`)로 분리했다.

## What Changes

- 애니메이션 프레임 425개를 asset catalog에서 플레인 번들 리소스 폴더(`AnimationFrames/`)로 이동, `UIImage(contentsOfFile:)` 로드로 시스템 캐시 우회 (신규 `AnimationFrameLoader`, 파일 로드는 off-main). 기존 prefetch-3/evict 로직 유지 → evict가 실제로 메모리를 해제.
- 폴링: 초기 일정 조회에서 내 팀 gameId 확보 후 `GET /games/{gameId}` 단건 조회로 전환(파싱 실패 시 목록 폴백), scenePhase 비활성 시 폴링 중단·활성 시 즉시 재개, DateFormatter static 캐시.
- @Published 중복 할당 제거: `GameData` Equatable 채택 후 값 변경 시에만 할당, `syncedTeamName`/`storeThemeId` 동일 가드 (햅틱·이벤트 처리 경로는 불변).
- ongoing 노티 제거 호출을 매 game_data → 세션 활성화 1회 + 경기 종료 1회로 축소.
- `stopExtendedSession()`이 scheduled/notStarted 세션도 invalidate하도록 수정 (재시작 정책은 불변).
- VICTORY 햅틱 13회를 취소 가능한 단일 Task로 전환 (타이밍 동일).
- 이닝 정규식 static 1회 컴파일, print 38곳 전부 `wlog`(#if DEBUG 전용)로 전환.

## Capabilities

### Modified Capabilities

- `watch-ios`: 라이브 관전 성능 정책(애니메이션 메모리, 폴링 페이로드, 렌더 무효화 최소화)을 명확히 한다.

## Impact

- `ios/watch/BaseHapticWatch/AnimationFrameLoader.swift` (신규), `WatchLog.swift` (신규)
- `ios/watch/BaseHapticWatch/AnimationFrames/` (신규, 425 JPG) / `Assets.xcassets` penguin imageset 425개 삭제
- `ios/watch/BaseHapticWatch/Screens/{HomeRun,Hit,Score,DoublePlay,Victory}TransitionScreen.swift`
- `ios/watch/BaseHapticWatch/WatchSync/WatchConnectivityManager.swift`, `WatchGamePoller.swift`, `WatchTokenRegistrar.swift`
- `ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift`, `WatchAppDelegate.swift`, `Models/GameData.swift`
- `ios/project.yml` + xcodegen 재생성
- 백엔드 변경 없음 (`/games/{game_id}` 기존 엔드포인트 사용)
