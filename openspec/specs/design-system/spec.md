# Design System Specification

## Purpose
BaseHaptic 서비스의 크로스 플랫폼(iPhone/Apple Watch/Android/Wear OS/웹) 공통 디자인 기반. 컬러·타이포그래피·스페이싱·반경·이벤트 색상·팀 테마를 토큰으로 정의하여 모든 UI가 동일한 기준을 따르게 한다. 이 문서가 모든 디자인 의사결정의 Single Source of Truth이며, Figma Variables, SwiftUI `App*` 토큰, Android Material Theme, 웹 Tailwind 설정이 모두 이 정의를 따른다.

## Requirements

### Requirement: 컬러 팔레트 (중립 스케일)
모든 플랫폼은 Zinc 계열의 중립 스케일 10단계를 공통으로 사용해야 한다(MUST).

#### Scenario: 다크 테마 기본 팔레트
- GIVEN BaseHaptic 앱이 실행될 때
- WHEN 기본 테마가 적용되면
- THEN 아래 Zinc 스케일을 배경·카드·보더·텍스트에 사용한다

| 토큰 | Hex | 주 용도 |
|---|---|---|
| gray950 | `#0A0A0B` | 최하위 배경 |
| gray900 | `#18181B` | 메인 배경, 카드 |
| gray800 | `#27272A` | 보조 카드, 구분선 |
| gray700 | `#3F3F46` | 보더, 비활성 배경 |
| gray600 | `#52525B` | 비활성 텍스트 |
| gray500 | `#71717A` | 보조 텍스트 |
| gray400 | `#A1A1AA` | 라벨 |
| gray300 | `#D4D4D8` | 플레이스홀더 |
| gray200 | `#E4E4E7` | 밝은 보더 |
| gray100 | `#F4F4F5` | 밝은 배경 |

#### Scenario: 시맨틱 컬러 스케일
- GIVEN UI에서 상태·이벤트를 색으로 표현할 때
- WHEN 시맨틱 컬러가 필요하면
- THEN 아래 팔레트를 사용한다

| 토큰 | Hex | 시맨틱 용도 |
|---|---|---|
| blue500 | `#3B82F6` | 기본 primary, 정보 |
| blue600 | `#2563EB` | primary dark, 버튼 |
| blue700 | `#1D4ED8` | 강조 |
| blue400 | `#60A5FA` | accent |
| blue200 | `#BFDBFE` | 옅은 강조 |
| cyan500 | `#06B6D4` | STEAL 이벤트 (워치 전용) |
| green500 | `#22C55E` | 성공, HIT·WALK·STEAL 그룹 |
| green400 | `#4ADE80` | 볼 카운트 활성, 가벼운 긍정 |
| yellow500 | `#EAB308` | SCORE·HOMERUN·VICTORY 그룹 |
| yellow400 | `#FACC15` | 스트라이크 카운트, 경고 |
| orange500 | `#F97316` | DOUBLE_PLAY·TRIPLE_PLAY·STRIKE 그룹 |
| red500 | `#EF4444` | OUT, 실패 |
| red400 | `#F87171` | 아웃 카운트, 부드러운 경고 |
| red600 | `#DC2626` | 위험 강조 (워치 전용) |

#### Scenario: 웹 다크 모드
- GIVEN 웹 앱에서 다크 모드가 활성화될 때
- WHEN `--dark` 클래스가 적용되면
- THEN OKLCH 컬러 스페이스 기반의 반전된 색상 스케일을 적용한다

### Requirement: 팀 테마
10개 KBO 구단별로 7개 슬롯(primary/primaryDark/secondary/accent/gradientStart/gradientEnd/navIndicator)을 정의하여 모든 플랫폼이 동일한 구단 컬러를 사용해야 한다(MUST).

#### Scenario: 구단별 컬러 매핑
- GIVEN 사용자가 응원 팀을 선택했을 때
- WHEN 테마가 적용되면
- THEN 아래 매핑에 따라 primary 계열 색상을 UI 전역에 반영한다

| 팀 | primary | primaryDark | secondary | accent |
|---|---|---|---|---|
| 두산 | `#131230` | `#0A0918` | `#EF4444` | `#60A5FA` |
| LG | `#C30452` | `#8E023B` | black | `#F472B6` |
| 키움 | `#820024` | `#5C001A` | `#D4A843` | `#FCA5A5` |
| 삼성 | `#074CA1` | `#053678` | white | `#93C5FD` |
| 롯데 | `#041E42` | `#021230` | `#E31B23` | `#93C5FD` |
| SSG | `#CE0E2D` | `#960A20` | `#FFD700` | `#FCA5A5` |
| KT | black | `#1A1A1A` | `#ED1C24` | `#A3A3A3` |
| 한화 | `#FF6600` | `#CC5200` | black | `#FDBA74` |
| 기아 | `#EA0029` | `#B5001F` | black | `#FCA5A5` |
| NC | `#315288` | `#213A61` | `#CFB53B` | `#93C5FD` |

#### Scenario: 기본 테마
- GIVEN 응원 팀이 선택되지 않았을 때 (Team.none)
- WHEN 앱이 렌더링되면
- THEN blue500/blue700 계열을 기본 primary로 사용한다

### Requirement: 타이포그래피 (iPhone / Android Mobile)
iPhone·Android Mobile 앱은 SF Pro/Default system font 기반의 의미 있는 이름을 가진 스케일을 사용해야 한다(MUST). 두 플랫폼은 동일한 이름·크기 매핑을 가진다.

#### Scenario: iPhone 타이포 스케일 적용
- GIVEN SwiftUI 뷰에서 텍스트를 표시할 때
- WHEN 폰트를 지정하면
- THEN `AppFont` 토큰을 사용한다 (raw `.system(size:)` 호출 금지)

#### Scenario: Android 타이포 스케일 적용
- GIVEN Jetpack Compose 화면에서 텍스트를 표시할 때
- WHEN 폰트를 지정하면
- THEN `AppFont` object의 토큰을 사용한다 (raw `fontSize = N.sp` 사용 금지, Material3 `Typography` 슬롯만으로는 스케일이 부족하므로 별도 `AppFont` 사용)

| 계층 | 크기 | weight | 주 용도 |
|---|---|---|---|
| display | 56 | regular | 온보딩 이모지/심볼 |
| h1 | 36 | bold | 온보딩 대제목 |
| h2 | 28 | bold | 섹션 대제목, 스코어 |
| h3 | 24 | bold | 섹션 제목 |
| h4 | 20 | bold | 서브 제목 |
| h5 | 18 | bold | 카드 제목 |
| bodyLg | 16 | regular/medium/bold | 본문 강조, 버튼 라벨 |
| label | 15 | regular/medium/bold | 선택 가능 라벨 |
| body | 14 | regular/medium/semibold/bold | 본문 |
| caption | 13 | regular/medium/semibold/bold | 상태 라벨 |
| micro | 12 | regular/medium/bold | 캡션 |
| tiny | 11 | regular/bold | 미니 캡션 |

#### Scenario: LiveActivity 전용 폰트
- GIVEN iOS LiveActivity 위젯에서 텍스트를 표시할 때
- WHEN 공간이 제한적이면
- THEN 아래 전용 토큰을 사용한다 (일반 스케일 남용 금지)

| 토큰 | 크기 | weight | 위치 |
|---|---|---|---|
| liveActivity9 | 9 | regular/bold | BSO 라벨 |
| liveActivity10 | 10 | regular/bold | 이벤트 라벨 |
| liveActivity11 | 11 | regular | 투수/타자명 |
| liveActivity15 | 15 | bold | 팀명 |
| liveActivity24 | 24 | heavy | 점수 숫자 |

### Requirement: 타이포그래피 (Apple Watch)
워치 앱은 화면 크기 3단계(small/medium/large)에 따라 타이포를 자동 조정하는 반응형 프로파일을 사용해야 한다(MUST).

#### Scenario: 워치 디바이스별 프로파일
- GIVEN 워치 화면 크기가 감지되었을 때 (41mm 이하/45mm/49mm Ultra)
- WHEN UI가 렌더링되면
- THEN `WatchUiProfile`의 크기별 프로파일을 사용한다 (scoreValueSize, teamNameSize, inningSize, playerInfoSize, countLabelSize)

#### Scenario: 워치 정적 텍스트
- GIVEN 워치 화면에서 프로파일과 무관한 정적 텍스트를 표시할 때 (NoGameScreen, EventOverlay 등)
- WHEN 폰트를 지정하면
- THEN 11pt/12pt/13pt 등 작은 고정 사이즈를 사용한다

### Requirement: 스페이싱
모든 플랫폼은 4의 배수 기반의 공통 스페이싱 스케일을 사용해야 한다(MUST).

#### Scenario: 스페이싱 토큰 적용
- GIVEN UI에서 패딩·마진·spacing을 지정할 때
- WHEN 값이 필요하면
- THEN 아래 스케일에서 선택한다 (raw 숫자 직접 사용 금지)

| 토큰 | 값 | 주 용도 |
|---|---|---|
| xxs | 2 | 미세 조정 (점수 VStack 등) |
| xs | 4 | 아이콘 gap, 라벨 간격 |
| sm | 8 | 기본 요소 간격, 작은 버튼 |
| md | 12 | 카드 내부 보조 여백 |
| lg | 16 | 카드 내부 기본 여백 |
| xl | 20 | 큰 카드 여백 |
| xxl | 24 | 화면 가장자리 여백 |
| xxxl | 32 | 섹션 간격 |

#### Scenario: 예외 허용
- GIVEN 디자인상 명확한 이유로 스케일 밖 값이 필요할 때
- WHEN 해당 위치에 직접 수치를 사용하면
- THEN 바로 위 줄에 `// Reason:` 주석으로 사유를 명시한다

#### Scenario: Outlier 정리
- GIVEN 기존 코드에 8의 배수가 아닌 패딩이 존재할 때 (5/6/10/14 등)
- WHEN 토큰화 리팩터링을 수행하면
- THEN 가장 가까운 스케일 값으로 정규화한다 (6→8, 14→16, 5→4, 10→8 또는 12)

### Requirement: 반경 시스템
모든 플랫폼은 일관된 모서리 반경 스케일을 사용해야 한다(MUST).

#### Scenario: 반경 토큰 적용
- GIVEN UI 요소에 cornerRadius를 적용할 때
- WHEN 값을 지정하면
- THEN 아래 스케일에서 선택한다

| 토큰 | 값 | 주 용도 |
|---|---|---|
| sm | 8 | 작은 버튼, 태그, 칩 |
| md | 12 | 카드 기본 |
| lg | 16 | 큰 카드, 모달 |
| xl | 20 | 강조 컨테이너 |
| pill | 999 | 완전히 둥근 배지 |

#### Scenario: 14pt 반경 통일
- GIVEN 기존 코드에 `cornerRadius(14)`가 존재할 때
- WHEN 토큰화하면
- THEN `AppRadius.md` (12)로 통일한다 (시각 차이 미미)

### Requirement: 이벤트 색상 시맨틱 매핑
게임 이벤트는 플랫폼·화면에 관계없이 동일한 색상 그룹으로 표시해야 한다(MUST).

#### Scenario: 이벤트 타입별 색상
- GIVEN 화면에 이벤트 라벨/버튼/아이콘을 표시할 때
- WHEN 이벤트 타입을 색상으로 구분하면
- THEN 아래 매핑을 사용한다

| 이벤트 그룹 | 포함 타입 | 색상 토큰 |
|---|---|---|
| 득점 그룹 | HOMERUN, SCORE, SAC_FLY_SCORE, VICTORY | yellow500 |
| 안전 그룹 | HIT, WALK, STEAL | green500 |
| 경계 그룹 | DOUBLE_PLAY, TRIPLE_PLAY, STRIKE | orange500 |
| 실패 그룹 | OUT, TAG_UP_ADVANCE | red500 |
| 중립 | BALL | gray400 |

#### Scenario: 햅틱 그룹과의 대응
- GIVEN 이벤트 색상이 시각 표현이고 햅틱은 진동 패턴일 때
- WHEN 두 체계가 같은 이벤트를 다룰 때
- THEN 햅틱 그룹(HOMERUN=SCORE, HIT=WALK=STEAL 등)과 색상 그룹이 의미적으로 일치한다

### Requirement: 워치 반응형 프로파일
워치 UI는 3단계 크기 프로파일과 화면 형태를 기준으로 반응형 레이아웃을 적용해야 한다(MUST).

#### Scenario: 프로파일 기반 레이아웃
- GIVEN 워치 화면 크기가 감지되었을 때
- WHEN UI가 렌더링되면
- THEN 40개 이상의 디멘션 파라미터(패딩, 폰트, 위젯 크기)를 프로파일에 맞게 자동 조정한다

### Requirement: 접근성 기반 컴포넌트 (웹)
웹 UI 컴포넌트는 접근성 표준을 준수해야 한다(MUST).

#### Scenario: Radix UI 프리미티브 사용
- GIVEN 웹 앱에서 인터랙티브 컴포넌트를 구현할 때
- WHEN 컴포넌트를 생성하면
- THEN Radix UI 프리미티브를 기반으로 키보드 내비게이션, 스크린 리더 호환성을 보장한다

### Requirement: 토큰 준수 원칙
모든 새 UI 코드는 디자인 토큰을 통해서만 컬러·타이포·스페이싱·반경을 지정해야 한다(MUST).

#### Scenario: 신규 코드 작성 (iOS)
- GIVEN SwiftUI에서 새 화면이나 컴포넌트를 구현할 때
- WHEN 스타일을 지정하면
- THEN `Color(hex:)`, `.system(size:)`, `.padding(N)`, `.cornerRadius(N)` 등의 raw 호출을 사용하지 않고 `AppColors`/`AppFont`/`AppSpacing`/`AppRadius`/`AppEventColors` 토큰만 사용한다

#### Scenario: 신규 코드 작성 (Android Compose)
- GIVEN Jetpack Compose에서 새 화면이나 컴포넌트를 구현할 때
- WHEN 스타일을 지정하면
- THEN `Color(0x...)`, raw `fontSize = N.sp`, `padding(N.dp)`, `RoundedCornerShape(N.dp)` 등의 raw 호출을 사용하지 않고 color object(`Gray950` 등)·`AppFont`·`AppSpacing`·`AppShapes`·`eventColor()` 토큰을 사용한다

#### Scenario: 예외 허용
- GIVEN 디자인상 토큰에 없는 값이 명확히 필요할 때 (one-off 애니메이션, 디바이스별 보정 등)
- WHEN raw 값을 사용하면
- THEN 해당 줄 바로 위에 `// Reason:` 주석으로 사유를 명시하여 의도를 드러낸다

#### Scenario: 토큰 추가 기준
- GIVEN 같은 raw 값이 두 곳 이상에서 반복될 때
- WHEN 세 번째 사용처가 발생하면
- THEN 해당 값을 토큰으로 승격시키고 모든 호출처를 치환한다
