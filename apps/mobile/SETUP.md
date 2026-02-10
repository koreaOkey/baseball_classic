# BaseHaptic 안드로이드 앱 설정 가이드

## 📋 개요
리액트 앱을 Jetpack Compose 기반 안드로이드 네이티브 앱으로 변환한 프로젝트입니다.

## 🛠️ 개발 환경 설정

### 필요 사항
- **Android Studio**: Hedgehog (2023.1.1) 이상 권장
- **JDK**: 17
- **Android SDK**: 
  - Minimum SDK: 26 (Android 8.0)
  - Target SDK: 34 (Android 14)
  - Compile SDK: 34
- **Gradle**: 8.7 (Wrapper 포함)

### 설치 단계

1. **Android Studio 설치**
   - [공식 웹사이트](https://developer.android.com/studio)에서 다운로드
   - 설치 시 Android SDK도 함께 설치됨

2. **프로젝트 열기**
   ```bash
   cd apps/mobile
   # Android Studio에서 이 폴더를 열기
   ```

3. **Gradle Sync**
   - Android Studio가 자동으로 Gradle 동기화 시작
   - 첫 빌드 시 필요한 의존성 다운로드 (시간 소요 가능)
   - ⚠️ 에러 발생 시: File > Invalidate Caches... > Invalidate and Restart

4. **에뮬레이터 설정** (실제 기기가 없는 경우)
   - Tools > Device Manager
   - Create Device
   - Phone > Pixel 6 선택 권장
   - System Image: API 34 (Android 14) 다운로드 및 선택

5. **앱 실행**
   - Run > Run 'app' (또는 Shift+F10)
   - 에뮬레이터 또는 연결된 실제 기기 선택

## 📱 앱 구조

### 주요 컴포넌트

```
app/src/main/java/com/basehaptic/mobile/
├── MainActivity.kt                    # 앱 진입점
├── data/model/                        # 데이터 모델
│   ├── Team.kt                        # 팀 정보
│   ├── Game.kt                        # 경기 정보
│   ├── GameEvent.kt                   # 경기 이벤트
│   └── ThemeData.kt                   # 테마 데이터
└── ui/
    ├── components/                    # 재사용 컴포넌트
    │   └── TeamLogo.kt                # 팀 로고
    ├── screens/                       # 화면
    │   ├── OnboardingScreen.kt        # 온보딩
    │   ├── HomeScreen.kt              # 홈
    │   ├── LiveGameScreen.kt          # 실시간 경기
    │   ├── CommunityScreen.kt         # 커뮤니티
    │   ├── ThemeStoreScreen.kt        # 테마 상점
    │   └── SettingsScreen.kt          # 설정
    └── theme/                         # 테마
        ├── Color.kt                   # 색상 정의
        ├── Type.kt                    # 타이포그래피
        └── Theme.kt                   # 테마 설정
```

## 🎨 리액트 → Compose 변환 내역

### 주요 변환 사항

| React | Jetpack Compose |
|-------|-----------------|
| `useState` | `remember { mutableStateOf() }` |
| `useEffect` | `LaunchedEffect` / `DisposableEffect` |
| `<div>` | `Box` / `Column` / `Row` |
| `className` / CSS | `Modifier` 체이닝 |
| React Navigation | Compose Navigation |
| `localStorage` | DataStore Preferences (예정) |
| Tailwind CSS | Material 3 / Custom Modifiers |

### 구현된 화면

1. **OnboardingScreen** ✅
   - 팀 선택 UI
   - 2단계 온보딩 플로우
   - 알림 설정 안내

2. **HomeScreen** ✅
   - 응원 팀 정보
   - 오늘의 경기 목록
   - 실시간/예정/종료 경기 표시
   - 나의 팀 하이라이트 효과

3. **LiveGameScreen** ✅
   - 실시간 스코어보드
   - 볼/스트라이크/아웃 카운트
   - 이벤트 타임라인
   - 햅틱 패턴 표시
   - 퀵 응원 버튼

4. **CommunityScreen** ⏳
   - 준비중 (플레이스홀더)

5. **ThemeStoreScreen** ⏳
   - 준비중 (플레이스홀더)

6. **SettingsScreen** ✅
   - 팀 설정
   - 알림 토글
   - 앱 정보

## 🔧 다음 작업

### 우선순위 높음
1. **앱 아이콘 추가**
   - `app/src/main/res/mipmap-*/` 폴더에 아이콘 파일 추가
   - 이름: `ic_launcher.png`, `ic_launcher_round.png`
   - 크기별 생성 필요
   - 💡 팁: Android Studio의 Image Asset Studio 사용 (우클릭 > New > Image Asset)

2. **DataStore 통합**
   - SharedPreferences 대신 DataStore 사용
   - 팀 선택, 설정 저장

3. **ViewModel 추가**
   - 비즈니스 로직 분리
   - 생명주기 안전한 데이터 관리

### 우선순위 중간
4. **백엔드 API 연동**
   - Retrofit 설정
   - Repository 패턴 적용
   - 실제 경기 데이터 로드

5. **워치 앱 연동**
   - Wearable Data Layer API
   - 데이터 동기화

### 우선순위 낮음
6. **애니메이션 추가**
   - Lottie 애니메이션
   - 화면 전환 애니메이션

7. **테마 시스템**
   - 팀별 테마 적용
   - 다크/라이트 모드

## 🎨 팀 로고 이미지

팀 로고 이미지가 `app/src/main/res/drawable/` 폴더에 포함되어 있습니다:
- ✅ 두산 베어스 (`dosan.png`)
- ✅ LG 트윈스 (`lg.png`)
- ✅ 키움 히어로즈 (`kiwoom.png`)
- ✅ 삼성 라이온즈 (`samsung.png`)
- ✅ 롯데 자이언츠 (`lotte.png`)
- ✅ SSG 랜더스 (`ssg.png`)
- ✅ KT 위즈 (`kt.png`)
- ✅ 한화 이글스 (`hanwha.png`)
- ✅ KIA 타이거즈 (`kia.png`)
- ✅ NC 다이노스 (`nc.png`)

`TeamLogo` 컴포넌트가 자동으로 각 팀의 실제 로고를 표시합니다.

## 🔧 버전 정보

현재 프로젝트는 다음 버전을 사용합니다 (안정성 및 호환성 우선):

### 빌드 도구
- **Gradle**: 8.7
- **Android Gradle Plugin (AGP)**: 8.5.2
- **Kotlin**: 2.0.21
- **Compile SDK**: 34
- **Target SDK**: 34
- **Min SDK**: 26

### 주요 라이브러리
- **Compose BOM**: 2024.09.03
- **AndroidX Core KTX**: 1.13.1 (compileSdk 34 호환)
- **Lifecycle**: 2.8.6
- **Activity Compose**: 1.9.2
- **Navigation Compose**: 2.8.5
- **Coil**: 2.7.0
- **Lottie**: 6.6.2

## 🐛 알려진 이슈

1. **앱 아이콘**
   - ✅ 임시 아이콘 생성 완료 (파란색 야구공 디자인)
   - 💡 커스텀 아이콘으로 교체하려면: Android Studio > 우클릭 res > New > Image Asset

2. **하드코딩된 데이터**
   - Mock 데이터 사용 중
   - 실제 API 연동 필요

3. **상태 저장 미구현**
   - 앱 재시작 시 설정 초기화
   - DataStore 통합 필요

## 🚨 트러블슈팅

### Gradle Sync 에러
```bash
# 캐시 정리
cd apps/mobile
rm -rf .gradle app/build build

# Android Studio 재시작
# File > Invalidate Caches... > Invalidate and Restart
```

### "Cannot select root node" 에러
→ 이미 수정됨. Gradle 8.7 + AGP 8.5.2로 안정화

### "Invalid Gradle JDK configuration found" 에러
**해결 방법:**
1. `Android Studio > Settings > Build, Execution, Deployment > Build Tools > Gradle`
2. `Gradle JDK` 드롭다운에서 **`Embedded JDK (jbr-17)`** 선택
3. Apply > OK
4. File > Sync Project with Gradle Files

**또는:** 에러 메시지의 "Use Embedded JDK" 버튼 클릭

### AAR 메타데이터 충돌
→ 이미 수정됨. AndroidX Core 1.13.1 사용 (compileSdk 34 호환)

### "resource mipmap/ic_launcher not found" 에러
→ 이미 수정됨. 임시 앱 아이콘(파란색 야구공) 생성 완료

**커스텀 아이콘으로 교체하려면:**
1. Android Studio에서 `res` 폴더 우클릭
2. New > Image Asset
3. Icon Type: Launcher Icons (Adaptive and Legacy)
4. Asset Type: Image/Clip Art/Text 선택
5. Path/Clipart/Text 설정
6. Next > Finish

## 📚 참고 자료

- [Jetpack Compose 공식 문서](https://developer.android.com/jetpack/compose)
- [Material 3 Design](https://m3.material.io/)
- [Android Developers](https://developer.android.com/)
- [Kotlin 공식 문서](https://kotlinlang.org/docs/home.html)

## 💡 팁

### Compose Preview 사용
```kotlin
@Preview(showBackground = true)
@Composable
fun PreviewOnboardingScreen() {
    BaseHapticTheme {
        OnboardingScreen(onComplete = {})
    }
}
```

### 디버깅
- `Logcat`에서 로그 확인
- Layout Inspector로 UI 계층 분석
- Compose 애니메이션 디버거 활용

### 성능 최적화
- `remember`로 재구성 최소화
- `derivedStateOf`로 계산 최적화
- LazyColumn에서 `key` 파라미터 사용

## 🤝 기여 가이드

1. 새 기능 추가 시 별도 브랜치 생성
2. Compose 스타일 가이드 준수
3. UI는 재사용 가능한 컴포넌트로 분리
4. 복잡한 로직은 ViewModel로 이동

