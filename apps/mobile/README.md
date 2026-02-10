# Mobile App (Android)

스마트폰 앱 영역입니다. (Hub 역할)

## 기술 스택
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM (향후 적용)
- **Navigation**: Compose Navigation
- **Preferences**: DataStore (향후 적용)

## 주요 화면
- **OnboardingScreen**: 팀 선택 및 초기 설정
- **HomeScreen**: 오늘의 경기 목록 및 팀 정보
- **LiveGameScreen**: 실시간 경기 중계 및 이벤트
- **CommunityScreen**: 팀 커뮤니티 (준비중)
- **ThemeStoreScreen**: 테마 상점 (준비중)
- **SettingsScreen**: 앱 설정

## 리소스

### 팀 로고
10개 구단 로고 이미지 포함 (`app/src/main/res/drawable/`)

| 파일명 | 팀 | 권장 사이즈 | 포맷 |
|--------|-----|------------|------|
| `dosan.png` | 두산 베어스 | 512x512px | PNG (투명) |
| `lg.png` | LG 트윈스 | 512x512px | PNG (투명) |
| `kiwoom.png` | 키움 히어로즈 | 512x512px | PNG (투명) |
| `samsung.png` | 삼성 라이온즈 | 512x512px | PNG (투명) |
| `lotte.png` | 롯데 자이언츠 | 512x512px | PNG (투명) |
| `ssg.png` | SSG 랜더스 | 512x512px | PNG (투명) |
| `kt.png` | KT 위즈 | 512x512px | PNG (투명) |
| `hanwha.png` | 한화 이글스 | 512x512px | PNG (투명) |
| `kia.png` | KIA 타이거즈 | 512x512px | PNG (투명) |
| `nc.png` | NC 다이노스 | 512x512px | PNG (투명) |

### 테마 리소스 (향후 추가 예정)

#### 팀별 마스코트 이미지
- **위치**: `app/src/main/res/drawable/`
- **파일명**: `mascot_{팀명}.png` (예: `mascot_doosan.png`)
- **사이즈**: 1024x1024px (고해상도)
- **포맷**: PNG (투명 배경)
- **용도**: LiveGameScreen, 테마 상점에서 사용

#### 테마 배경 이미지
- **위치**: `app/src/main/res/drawable/`
- **파일명**: `theme_bg_{팀명}.png` 또는 `theme_bg_{팀명}.webp`
- **사이즈**: 1080x1920px (Full HD)
- **포맷**: WebP (권장) 또는 PNG
- **용도**: 커스텀 테마 배경

#### 애니메이션 리소스 (Lottie)
- **위치**: `app/src/main/res/raw/`
- **파일명**: `anim_{이벤트타입}.json` (예: `anim_homerun.json`)
- **포맷**: Lottie JSON
- **용도**: 홈런, 득점 등 이벤트 발생 시 애니메이션
- **추천 툴**: LottieFiles, Adobe After Effects

#### 앱 아이콘
- **위치**: `app/src/main/res/mipmap-{density}/`
- **파일명**: `ic_launcher.png`, `ic_launcher_round.png`
- **사이즈 (density별)**:
  - `mdpi`: 48x48px
  - `hdpi`: 72x72px
  - `xhdpi`: 96x96px
  - `xxhdpi`: 144x144px
  - `xxxhdpi`: 192x192px
- **Adaptive Icon**: `mipmap-anydpi-v26/ic_launcher.xml`
  - Foreground: 108x108dp (중앙 72dp만 사용)
  - Background: 108x108dp 단색 또는 이미지

### 테마 에셋 추가 방법

1. **팀 로고 추가/변경**
```bash
# drawable 폴더에 이미지 복사
cp {팀명}.png app/src/main/res/drawable/
```

2. **코드에서 참조** (`TeamLogo.kt`)
```kotlin
val logoResource = when (team) {
    Team.{팀명} -> R.drawable.{팀명}
    // ...
}
```

3. **테마 색상 정의** (`TeamTheme.kt`)
```kotlin
val {팀명} = TeamTheme(
    team = Team.{팀명},
    primary = Color(0xFF...),
    primaryDark = Color(0xFF...),
    // ...
)
```

## 빌드 방법

### 필요 사항
- Android Studio Hedgehog (2023.1.1) 이상
- JDK 17
- Android SDK 26 이상
- Gradle 8.7 (프로젝트에 포함)

### 빌드 및 실행
1. Android Studio에서 프로젝트 열기
2. Gradle Sync 완료 대기
3. 앱 실행 (Run > Run 'app')

```bash
# 또는 명령줄에서
./gradlew assembleDebug
```

## MVP 목표
- [x] 팀 선택 (Onboarding)
- [x] 경기 목록 표시
- [x] 실시간 경기 화면
- [x] 기본 네비게이션
- [x] 팀 로고 이미지 통합
- [x] 앱 아이콘 (임시)
- [x] 동적 테마 시스템 (팀별 색상 자동 전환)
- [x] 설정에서 응원팀 변경
- [ ] 백엔드 연동 (API 통신)
- [ ] 워치 앱 연동 (Data Layer)
- [ ] 워치 햅틱 피드백 전송
- [ ] 실시간 데이터 업데이트
- [ ] 커스텀 테마 상점

## 구조

```
app/src/main/java/com/basehaptic/mobile/
├── MainActivity.kt              # 메인 액티비티 및 앱 진입점
├── data/
│   └── model/                   # 데이터 모델
│       ├── Team.kt
│       ├── Game.kt
│       ├── GameEvent.kt
│       └── ThemeData.kt
└── ui/
    ├── components/              # 재사용 가능한 UI 컴포넌트
    │   └── TeamLogo.kt          # 팀 로고 (실제 이미지 사용)
    ├── screens/                 # 화면 컴포저블
    │   ├── OnboardingScreen.kt
    │   ├── HomeScreen.kt
    │   ├── LiveGameScreen.kt
    │   ├── CommunityScreen.kt
    │   ├── ThemeStoreScreen.kt
    │   └── SettingsScreen.kt
    └── theme/                   # 테마 및 스타일
        ├── Color.kt
        ├── Type.kt
        └── Theme.kt

app/src/main/res/
├── drawable/                    # 이미지 리소스
│   ├── dosan.png               # 두산 베어스 로고
│   ├── lg.png                  # LG 트윈스 로고
│   ├── kiwoom.png              # 키움 히어로즈 로고
│   ├── samsung.png             # 삼성 라이온즈 로고
│   ├── lotte.png               # 롯데 자이언츠 로고
│   ├── ssg.png                 # SSG 랜더스 로고
│   ├── kt.png                  # KT 위즈 로고
│   ├── hanwha.png              # 한화 이글스 로고
│   ├── kia.png                 # KIA 타이거즈 로고
│   └── nc.png                  # NC 다이노스 로고
└── values/                      # 문자열 및 색상 리소스
    ├── strings.xml
    ├── colors.xml
    └── themes.xml
```

## 다음 단계
1. ViewModel 및 Repository 패턴 적용
2. Retrofit을 통한 백엔드 API 연동
3. **Wearable Data Layer API를 통한 워치 앱 연동**
   - 경기 데이터 실시간 전송 (점수, 이닝, BSO)
   - 이벤트 발생 시 워치 햅틱 트리거
   - 팀 테마 동기화
4. 실시간 데이터 업데이트 (WebSocket 또는 FCM)
5. 로컬 데이터 저장 (DataStore/Room)
6. 커스텀 테마 상점 구현
   - 팀별 프리미엄 테마 (마스코트 애니메이션 포함)
   - 테마 구매 및 적용 시스템
