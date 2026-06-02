## ADDED Requirements

### Requirement: 라이브 경기 상세 워치 동기화 토글
iOS 모바일 앱은 라이브 경기 상세 화면 상단바에서 사용자가 워치 동기화를 직접 켜고 끌 수 있는 슬라이드 토글을 제공해야 한다(MUST).

#### Scenario: 토글 노출 위치
- **GIVEN** 사용자가 진행 중인 경기의 상세 화면에 진입했을 때
- **WHEN** 화면이 렌더링되면
- **THEN** 상단바의 LIVE 뱃지와 워치 동기화 상태 캡슐 뱃지 옆에 SwiftUI Toggle 형태의 슬라이드 토글이 표시된다

#### Scenario: 토글 초기 상태
- **GIVEN** 현재 `syncedGameId` 가 해당 경기 ID 와 일치할 때
- **WHEN** 토글이 렌더링되면
- **THEN** 토글은 ON 상태로 표시된다

#### Scenario: 토글 초기 상태 — 미동기화
- **GIVEN** 현재 `syncedGameId` 가 nil 이거나 다른 경기 ID 일 때
- **WHEN** 토글이 렌더링되면
- **THEN** 토글은 OFF 상태로 표시된다

### Requirement: 토글 OFF→ON 광고 게이트
iOS 모바일 앱은 토글을 OFF→ON 으로 전환할 때 AdMob Rewarded 광고 시청을 의무화해야 한다(MUST). 단 같은 경기에 대해 이미 광고를 본 적이 있으면 광고를 생략한다.

#### Scenario: 광고 확인 팝업 표시
- **GIVEN** 토글이 OFF 상태일 때
- **WHEN** 사용자가 토글을 탭하면
- **THEN** 토글이 즉시 ON 위치로 슬라이드되고 "워치로 보시겠습니까? / 광고 관람 후 동기화됩니다" 메시지의 alert 가 표시된다
- **AND** alert 의 좌측 버튼은 [확인], 우측 버튼은 [취소] 로 배치된다

#### Scenario: 광고 시청 후 동기화
- **GIVEN** OFF→ON 확인 팝업에서 사용자가 [확인] 을 탭했고 해당 경기 ID 에 대한 광고 시청 이력이 없을 때
- **WHEN** AdMob Rewarded 광고가 재생되고 시청이 완료되면
- **THEN** `syncedGameId` 가 해당 경기 ID 로 갱신되고 광고 시청 플래그가 영구 저장된다

#### Scenario: 광고 생략 — 같은 경기 재시청
- **GIVEN** OFF→ON 확인 팝업에서 사용자가 [확인] 을 탭했고 해당 경기 ID 에 대한 광고 시청 이력이 이미 있을 때
- **WHEN** 동작이 실행되면
- **THEN** 광고는 재생되지 않고 즉시 `syncedGameId` 가 해당 경기 ID 로 갱신된다

#### Scenario: 광고 실패 시 폴백
- **GIVEN** OFF→ON 확인 팝업에서 사용자가 [확인] 을 탭했을 때
- **WHEN** AdMob Rewarded 광고 로드 또는 표시가 실패하면
- **THEN** `syncedGameId` 는 해당 경기 ID 로 갱신되며 광고 시청 플래그는 저장되지 않는다

#### Scenario: 사용자 취소 — 토글 슬라이드 백
- **GIVEN** OFF→ON 확인 팝업이 표시됐을 때
- **WHEN** 사용자가 [취소] 를 탭하거나 alert 를 dismiss 하면
- **THEN** 토글은 슬라이드 백 애니메이션으로 OFF 위치로 복귀하며 `syncedGameId` 는 변경되지 않는다

### Requirement: 토글 ON→OFF 즉시 해제
iOS 모바일 앱은 토글을 ON→OFF 로 전환할 때 광고 없이 확인 팝업만 거쳐 동기화를 해제해야 한다(MUST).

#### Scenario: 해제 확인 팝업
- **GIVEN** 토글이 ON 상태일 때
- **WHEN** 사용자가 토글을 탭하면
- **THEN** 토글이 즉시 OFF 위치로 슬라이드되고 "워치 동기화를 끄시겠습니까?" 메시지의 alert 가 표시된다
- **AND** alert 의 좌측 버튼은 [확인], 우측 버튼은 [취소] 로 배치된다

#### Scenario: 해제 확정
- **GIVEN** ON→OFF 확인 팝업이 표시됐을 때
- **WHEN** 사용자가 [확인] 을 탭하면
- **THEN** `syncedGameId` 가 nil 로 갱신되고 광고는 재생되지 않는다

#### Scenario: 해제 취소 — 토글 슬라이드 백
- **GIVEN** ON→OFF 확인 팝업이 표시됐을 때
- **WHEN** 사용자가 [취소] 를 탭하거나 alert 를 dismiss 하면
- **THEN** 토글은 슬라이드 백 애니메이션으로 ON 위치로 복귀하며 `syncedGameId` 는 변경되지 않는다

### Requirement: 워치 동기화 광고 단위 분리
iOS 모바일 앱은 워치 동기화 진입점에 사용되는 AdMob Rewarded 광고 단위를 테마 스토어용 광고 단위와 분리해야 한다(MUST).

#### Scenario: 광고 단위 ID 사용
- **GIVEN** 라이브 경기 상세 토글에서 광고를 호출할 때
- **WHEN** `RewardedAdManager` 가 광고를 로드하면
- **THEN** "Watch Sync Rewarded - iOS" 전용 광고 단위 ID 가 사용되며 테마 스토어 광고 단위 ID 와 다른 값이다
