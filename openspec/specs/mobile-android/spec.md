# Mobile Android Specification

## Purpose
Android(Jetpack Compose) 스마트폰에서 실시간 야구 경기를 확인하고 Wear OS 워치와 연동하는 모바일 앱.
## Requirements
### Requirement: 당일 경기 목록
앱은 오늘의 경기 목록을 표시해야 한다(MUST).

#### Scenario: 경기 목록 로드
- GIVEN 사용자가 앱을 실행했을 때
- WHEN 홈 화면이 표시되면
- THEN 당일 KBO 경기 목록을 백엔드에서 조회하여 카드 형태로 표시한다

### Requirement: 팀 선택 및 관심 경기
사용자는 관심 팀을 선택하고 해당 팀 경기에 집중할 수 있어야 한다(MUST).

#### Scenario: 관심 팀 경기 LIVE 전환
- GIVEN 사용자가 관심 팀을 설정했을 때
- WHEN 해당 팀 경기가 LIVE 상태로 전환되면
- THEN Wear OS 워치에 동기화 프롬프트를 전송한다

### Requirement: Wear OS 워치 동기화 플로우
모바일은 Wear OS 워치와 Data Layer API를 통해 양방향 동기화해야 한다(MUST).

#### Scenario: 워치 동기화 요청
- GIVEN 관심 팀 경기가 LIVE가 되었을 때
- WHEN 모바일이 /watch/prompt/current 경로로 프롬프트를 전송하면
- THEN Wear OS 워치에 동기화 동의 팝업이 표시된다

#### Scenario: 워치 동기화 응답 수신
- GIVEN 워치에서 동기화 응답이 전송되었을 때
- WHEN /watch/sync-response/{timestamp} 경로로 응답을 수신하면
- THEN 수락 시 광고 확인 절차를 거친 뒤 syncedGameId를 저장하고 실시간 동기화를 시작한다

### Requirement: 실시간 경기 데이터 수신
모바일은 선택된 경기의 실시간 데이터를 수신해야 한다(MUST).

#### Scenario: 경기 상태 폴링/WebSocket
- GIVEN 사용자가 경기를 선택했을 때
- WHEN 경기가 진행 중이면
- THEN WebSocket 또는 폴링으로 state/events를 실시간 수신하여 화면에 반영한다

#### Scenario: Debug 빌드 스테이징 백엔드 연동
- GIVEN 개발자가 Android Debug 빌드를 실행했을 때
- WHEN 앱이 경기 목록, 경기 상태, 이벤트, WebSocket URL을 구성하면
- THEN 스테이징 백엔드 주소를 사용한다
- AND Release 빌드는 운영 백엔드 주소를 사용한다

#### Scenario: Debug 빌드 스테이징 Supabase 연동
- GIVEN 개발자가 Android Debug 빌드를 실행했을 때
- WHEN 앱이 인증용 Supabase 클라이언트를 구성하면
- THEN 스테이징 Supabase 주소와 publishable key를 사용한다
- AND Release 빌드는 운영 Supabase 프로젝트를 사용한다

### Requirement: Data Layer 서비스
모바일은 워치와의 통신을 위한 Data Layer 리스너 서비스를 운영해야 한다(MUST).

#### Scenario: 워치 응답 수신 서비스
- GIVEN 앱이 실행 중일 때
- WHEN 워치로부터 Data Layer 메시지가 도착하면
- THEN MobileDataLayerListenerService가 수신하여 WearWatchSyncBridge로 전달한다

### Requirement: 라이브 경기 상세 워치 동기화 토글
Android 모바일 앱은 라이브 경기 상세 화면 상단바에서 사용자가 워치 동기화를 직접 켜고 끌 수 있는 슬라이드 토글을 제공해야 한다(MUST).

#### Scenario: 토글 노출 위치
- **GIVEN** 사용자가 진행 중인 경기의 상세 화면에 진입했을 때
- **WHEN** 화면이 렌더링되면
- **THEN** 상단바의 LIVE 뱃지와 워치 동기화 상태 캡슐 뱃지 옆에 Compose Switch 형태의 슬라이드 토글이 표시된다

#### Scenario: 토글 초기 상태
- **GIVEN** 현재 `syncedGameId` 가 해당 경기 ID 와 일치할 때
- **WHEN** 토글이 렌더링되면
- **THEN** 토글은 ON 상태로 표시된다

#### Scenario: 토글 초기 상태 — 미동기화
- **GIVEN** 현재 `syncedGameId` 가 비어있거나 다른 경기 ID 일 때
- **WHEN** 토글이 렌더링되면
- **THEN** 토글은 OFF 상태로 표시된다

### Requirement: 토글 OFF→ON 광고 게이트
Android 모바일 앱은 토글을 OFF→ON 으로 전환할 때 AdMob Rewarded 광고 시청을 의무화해야 한다(MUST). 단 같은 경기에 대해 이미 광고를 본 적이 있으면 광고를 생략한다.

#### Scenario: 광고 확인 팝업 표시
- **GIVEN** 토글이 OFF 상태일 때
- **WHEN** 사용자가 토글을 탭하면
- **THEN** 토글이 즉시 ON 위치로 슬라이드되고 "워치로 보시겠습니까? / 광고 관람 후 동기화됩니다" 메시지의 AlertDialog 가 표시된다
- **AND** AlertDialog 의 좌측 버튼은 [확인], 우측 버튼은 [취소] 로 배치된다

#### Scenario: 경기 시작 전 워치 동기화 차단
- **GIVEN** 경기 상태가 LIVE가 아닐 때
- **WHEN** 사용자가 홈 경기 카드 또는 경기 상세의 워치 동기화 토글을 ON으로 전환하려 하면
- **THEN** 워치 동기화를 시작하지 않고 "경기 시작 전입니다." AlertDialog 를 표시한다

#### Scenario: 푸시 진입점 광고 게이트
- **GIVEN** 사용자가 경기 시작 푸시에서 워치 관람을 시작할 때
- **WHEN** 워치 앱이 설치되어 있고 해당 경기가 아직 동기화되지 않았으면
- **THEN** LiveGame으로 바로 이동하지 않고 "워치로 보시겠습니까? / 광고 관람 후 동기화됩니다" 메시지의 AlertDialog 를 먼저 표시한다

#### Scenario: 홈 경기 카드 탭은 워치 광고 게이트를 표시하지 않음
- **GIVEN** 홈 화면에 LIVE 경기 카드가 표시되어 있을 때
- **WHEN** 사용자가 해당 경기 카드를 탭하면
- **THEN** 워치 동기화 AlertDialog 를 표시하지 않고 LiveGame으로 바로 이동한다

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
- **WHEN** 사용자가 [취소] 를 탭하거나 dialog 를 dismiss 하면
- **THEN** 토글은 슬라이드 백 애니메이션으로 OFF 위치로 복귀하며 `syncedGameId` 는 변경되지 않는다

### Requirement: 토글 ON→OFF 즉시 해제
Android 모바일 앱은 토글을 ON→OFF 로 전환할 때 광고 없이 확인 팝업만 거쳐 동기화를 해제해야 한다(MUST).

#### Scenario: 해제 확인 팝업
- **GIVEN** 토글이 ON 상태일 때
- **WHEN** 사용자가 토글을 탭하면
- **THEN** 토글이 즉시 OFF 위치로 슬라이드되고 "워치 동기화를 끄시겠습니까?" 메시지의 AlertDialog 가 표시된다
- **AND** AlertDialog 의 좌측 버튼은 [확인], 우측 버튼은 [취소] 로 배치된다

#### Scenario: 해제 확정
- **GIVEN** ON→OFF 확인 팝업이 표시됐을 때
- **WHEN** 사용자가 [확인] 을 탭하면
- **THEN** `syncedGameId` 가 비어있는 값으로 갱신되고 광고는 재생되지 않는다

#### Scenario: 해제 취소 — 토글 슬라이드 백
- **GIVEN** ON→OFF 확인 팝업이 표시됐을 때
- **WHEN** 사용자가 [취소] 를 탭하거나 dialog 를 dismiss 하면
- **THEN** 토글은 슬라이드 백 애니메이션으로 ON 위치로 복귀하며 `syncedGameId` 는 변경되지 않는다

### Requirement: 워치 동기화 광고 단위 분리
Android 모바일 앱은 워치 동기화 진입점에 사용되는 AdMob Rewarded 광고 단위를 테마 스토어용 광고 단위와 분리해야 한다(MUST).

#### Scenario: 광고 단위 ID 사용
- **GIVEN** 라이브 경기 상세 토글에서 광고를 호출할 때
- **WHEN** `RewardedAdManager` 가 광고를 로드하면
- **THEN** "Watch Sync Rewarded - Android" 전용 광고 단위 ID 가 사용되며 테마 스토어 광고 단위 ID 와 다른 값이다

### Requirement: 경기 상세 필드 프리뷰 야구장 배경 이미지
Android 모바일 앱의 경기 상세 필드 프리뷰는 야구장형 정적 배경 이미지를 4:3 비율로 표시해야 한다(MUST).

#### Scenario: 배경 이미지 표시
- **GIVEN** 사용자가 진행 중인 경기 상세 화면에 진입했을 때
- **WHEN** 두 번째 섹션 필드 카드가 렌더링되면
- **THEN** 카드 영역에 외야 잔디 + 다이아몬드 일러스트 drawable 이 4:3 비율로 표시된다

#### Scenario: 코드 다이아몬드 제거
- **GIVEN** 필드 카드가 렌더링될 때
- **WHEN** 화면이 그려지면
- **THEN** Compose `Canvas` 로 직접 그린 다이아몬드 라인은 더 이상 존재하지 않으며 시각 요소는 배경 이미지가 담당한다

### Requirement: 필드 프리뷰 포지션 좌표 정규화
Android 모바일 앱은 야구장 위 10개 포지션(LF, CF, RF, SS, 2B, 3B, 1B, P, C, B)에 대한 정규화 좌표(0.0~1.0) 를 정의해야 한다(MUST).

#### Scenario: 좌표 정의
- **GIVEN** 필드 카드가 초기화될 때
- **WHEN** 코드가 좌표를 참조하면
- **THEN** 10개 포지션 각각에 대해 `x`, `y` 정규화 값이 0.0~1.0 범위로 정의되어 있다

#### Scenario: 좌표 비례 배치
- **GIVEN** 카드 폭이 변할 때(폰 ↔ 폴더블 펼침)
- **WHEN** 좌표가 화면 좌표로 변환되면
- **THEN** 절대 위치는 카드 폭에 비례하여 자동 조정되며 배경 이미지 위 정확한 위치에 매핑된다

### Requirement: 필드 프리뷰 투수/타자 라벨
Android 모바일 앱은 경기 응답의 `pitcher` / `batter` 이름이 존재하면 P 와 B 좌표에 캡슐 라벨로 표시해야 한다(MUST).

#### Scenario: 투수 라벨 표시
- **GIVEN** 경기 상태에 `pitcher` 이름이 존재할 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** P 좌표 위치에 투수 이름이 작은 캡슐 라벨로 표시된다

#### Scenario: 타자 라벨 표시
- **GIVEN** 경기 상태에 `batter` 이름이 존재할 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** B 좌표 위치에 타자 이름이 작은 캡슐 라벨로 표시된다

#### Scenario: 이름 비어있을 때
- **GIVEN** `pitcher` 또는 `batter` 가 비어있거나 null 일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 해당 위치의 라벨은 표시되지 않는다(빈 캡슐 placeholder 없음)

#### Scenario: 문자열 null 이름 미표시
- **GIVEN** `pitcher` 또는 `batter` 값이 문자열 "null" 일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 해당 값을 선수 이름으로 표시하지 않는다

### Requirement: 필드 프리뷰 루상 점유 오버레이
Android 모바일 앱은 1·2·3루 점유 상태를 배경 이미지 위 오버레이 원으로 표시해야 한다(MUST).

#### Scenario: 루상 점유 표시
- **GIVEN** 경기 상태에 1루 점유가 true 일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 1B 좌표 위치에 점유 원이 표시된다

#### Scenario: 비점유 시 미표시
- **GIVEN** 경기 상태에 1루 점유가 false 일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 1B 좌표 위치에 점유 원이 표시되지 않는다

### Requirement: 필드 카드 수비 라벨이 백엔드 라인업으로 채워짐
Android 모바일 앱의 라이브 경기 필드 카드는 백엔드 `GameStateOut.homeLineup` / `awayLineup` 을 수비팀 기준으로 매핑하여 8 개 수비 슬롯 라벨을 자동 표시해야 한다(MUST).

#### Scenario: 라인업 도착 시 자동 표시
- **GIVEN** 백엔드 응답에 활성 라인업이 포함되어 있고 이닝 표기가 "N회초" 또는 "N회말" 일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 수비팀(이닝 "초"=홈수비, "말"=어웨이수비) 의 라인업 슬롯이 한글 positionName 으로 매핑되어 LF/CF/RF/SS/2B/3B/1B/C 8 개 라벨에 자동 표시된다

#### Scenario: 경기 전 라인업 미보유
- **GIVEN** 경기 상태가 LIVE 전이고 백엔드 응답에 라인업이 빈 배열일 때
- **WHEN** 필드 카드가 렌더링되면
- **THEN** 수비 슬롯과 P/B 라벨은 표시되지 않고 상단 중앙에 "경기 시작 전입니다." 문구가 표시된다

#### Scenario: 이닝 초/말 전환
- **GIVEN** 이닝이 "초" 에서 "말" 로 전환될 때
- **WHEN** 새 상태가 수신되면
- **THEN** 필드 카드 라벨이 새 수비팀의 라인업으로 교체된다

#### Scenario: positionName 매핑 미스
- **GIVEN** 라인업 슬롯의 한글 positionName 이 표준 9 개 포지션 명칭과 일치하지 않을 때
- **WHEN** 매핑 헬퍼가 동작하면
- **THEN** 해당 슬롯은 어느 라벨 위치에도 표시되지 않고 다른 슬롯 표시에 영향이 없다

### Requirement: 라이브 경기 상세 이닝 탭 필터
Android 모바일 앱의 라이브 경기 상세 화면은 1~9회 탭을 통해 해당 회 이벤트만 필터링해 표시해야 한다(MUST).

#### Scenario: 진입 시 현재 회 선택
- **GIVEN** 사용자가 라이브 경기 상세에 진입했을 때
- **WHEN** 화면이 렌더링되면
- **THEN** 선택된 이닝은 현재 `state.inning` 으로 초기화되고 해당 회 이벤트만 목록에 표시된다

#### Scenario: 이닝 탭 탭하여 필터
- **GIVEN** 사용자가 5회 탭을 탭했을 때
- **WHEN** 이벤트 목록이 갱신되면
- **THEN** `event.inning == "5회초"` 또는 `"5회말"` 인 이벤트만 표시된다

#### Scenario: 회별 이벤트 없음
- **GIVEN** 사용자가 선택한 회에 이벤트가 없을 때(또는 마이그레이션 이전 NULL)
- **WHEN** 이벤트 목록이 렌더링되면
- **THEN** "해당 회 이벤트가 없습니다" 빈 상태 카피가 표시된다

#### Scenario: 사용자가 manual 선택 후 이닝 전환
- **GIVEN** 사용자가 다른 회 탭을 탭한 상태에서
- **WHEN** 경기 진행으로 `state.inning` 이 새 회로 전환되면
- **THEN** 선택된 탭은 사용자가 선택한 값을 유지한다(자동 갱신되지 않는다)

#### Scenario: manual 선택 없이 이닝 전환
- **GIVEN** 사용자가 진입 후 어떤 탭도 직접 탭하지 않은 상태에서
- **WHEN** `state.inning` 이 새 회로 전환되면
- **THEN** 선택된 이닝이 새 값으로 자동 따라간다
