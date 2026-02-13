# Watch App (Wear OS)

스마트워치 앱 영역입니다. (Live Node)

## 기술 스택
- **Platform**: Wear OS 3.0+
- **Language**: Kotlin
- **UI**: Compose for Wear OS
- **Communication**: Wearable Data Layer API
- **Design**: Material for Wear OS

## 주요 화면
- **LiveGameScreen**: 실시간 경기 중계 화면
  - 점수, 이닝, BSO 카운트
  - 베이스 상황 다이아몬드
  - 타자/투수 정보
  - 팀 색상 테마 (동적 전환)
- **NoGameScreen**: 경기 없음 안내

## 리소스

### 워치 앱 아이콘
- **위치**: `app/src/main/res/mipmap-{density}/`
- **파일명**: `ic_launcher_foreground.xml` (Adaptive Icon)
- **사이즈**: 
  - 워치 아이콘은 일반적으로 더 단순한 디자인 권장
  - Adaptive Icon: 108x108dp
  - 중앙 안전 영역: 66x66dp (원형 워치 대응)
- **포맷**: Vector Drawable (XML) 권장 (확장성)

### 테마 리소스 (향후 추가 예정)

#### 팀별 컴팩트 로고 (워치용)
- **위치**: `app/src/main/res/drawable/`
- **파일명**: `team_{팀명}_compact.xml` 또는 `.png`
- **사이즈**: 48x48dp (워치 화면에 최적화)
- **포맷**: Vector Drawable (권장) 또는 PNG
- **특징**: 디테일 축소, 두꺼운 선, 높은 대비
- **용도**: LiveGameScreen 헤더, 점수차 배지

#### 햅틱 패턴 시각화 아이콘
- **위치**: `app/src/main/res/drawable/`
- **파일명**: `haptic_{이벤트}.xml`
- **사이즈**: 24x24dp
- **포맷**: Vector Drawable
- **용도**: 이벤트 수신 시 시각적 표현

### 워치 테마 에셋 추가 방법

1. **팀 테마 색상 정의** (`WatchTeamTheme.kt`)
```kotlin
val {팀명} = WatchTeamTheme(
    teamName = "{팀명}",
    primary = Color(0xFF...),
    gradientStart = Color(0xFF...),
    // ...
)
```

2. **컴팩트 아이콘 추가** (선택사항)
```xml
<!-- res/drawable/team_doosan_compact.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="..."/>
</vector>
```

3. **모바일 앱에서 테마 동기화**
   - 모바일 앱의 `DataLayerListenerService`가 자동으로 팀 정보 전송
   - 워치는 `teamName`을 기반으로 자동 테마 적용

### 워치 디자인 가이드라인

- **폰트 크기**: 최소 12sp (가독성)
- **터치 영역**: 최소 48x48dp (손가락 터치)
- **색상 대비**: WCAG AA 기준 이상 (4.5:1)
- **애니메이션**: 60fps, 최대 300ms
- **배터리 고려**: 어두운 색상 우선 (OLED 최적화)

## 빌드 방법

### 필요 사항
- Android Studio Hedgehog 이상
- Wear OS 에뮬레이터 또는 실제 Wear OS 디바이스
- JDK 17

### 빌드 및 실행
1. 루트(`baseball_classic/`)에서 Android Studio로 프로젝트 열기
2. Wear OS 에뮬레이터 생성 (API 30+)
3. Run > Run 'app'

```bash
# 루트에서 모노레포 빌드
./gradlew :apps:watch:app:assembleDebug
```

## MVP 목표
- [x] 워치 앱 UI 기본 구조
- [x] 실시간 경기 화면 (reference.png 기반)
- [x] BSO 카운터
- [x] 베이스 다이아몬드
- [x] 동적 팀 색상 테마 (모바일 연동)
- [x] DataLayerListenerService 구조
- [x] Data Layer API 실제 연동 (모바일 → 워치 경기/테마/햅틱 동기화)
- [ ] 포그라운드 서비스 (화면 꺼져도 유지)
- [x] 햅틱 피드백 구현 (HOMERUN, HIT, OUT, SCORE, STRIKE, BALL)
- [ ] BLE 원격 하이파이브

## 구조

```
app/src/main/java/com/basehaptic/watch/
├── MainActivity.kt                    # 메인 액티비티 (BroadcastReceiver로 실시간 갱신)
├── DataLayerListenerService.kt       # 데이터 수신 + 햅틱 피드백 서비스
├── data/
│   └── GameData.kt                   # 경기 데이터 모델
└── ui/
    ├── components/
    │   ├── LiveGameScreen.kt         # 실시간 경기 화면
    │   └── NoGameScreen.kt           # 경기 없음 화면
    └── theme/
        ├── Color.kt
        ├── TeamTheme.kt
        ├── Type.kt
        └── Theme.kt

app/src/main/res/
└── drawable/                          # KBO 10개 팀 로고 (워치용)
    ├── dosan.png, lg.png, kiwoom.png, samsung.png, lotte.png
    ├── ssg.png, kt.png, hanwha.png, kia.png, nc.png
```

## 완료된 기능

### 햅틱 피드백
`DataLayerListenerService.kt`에 `VibrationEffect.createWaveform()` 기반으로 구현 완료.

| 이벤트 | 진동 패턴 | 강도 (amplitude) |
|--------|-----------|-------------------|
| HOMERUN | 강한 진동 3회 (200ms on, 150ms off) | 255 |
| HIT | 중간 진동 2회 (150ms on, 100ms off) | 180 |
| OUT | 짧은 진동 1회 (100ms) | 150 |
| SCORE | 강한 진동 2회 (200ms on, 200ms off) | 255 |
| STRIKE | 짧은 진동 2회 (80ms on, 80ms off) | 120 |
| BALL | 약한 진동 1회 (50ms) | 80 |

## 다음 단계
1. ~~Mobile 앱과 Data Layer 통신 구현~~ (완료)
2. **포그라운드 서비스로 백그라운드 유지**
   - 화면 꺼져도 경기 중계 유지
   - 알림 표시 (진행 중인 경기 정보)
3. **배터리 최적화**
   - Doze 모드 대응
   - 워치 센서 데이터 최소화
