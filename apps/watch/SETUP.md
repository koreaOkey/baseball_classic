# Watch App 테스트 가이드

## 테스트 환경 구성

### 필요 사항
- Android Studio Hedgehog (2023.1.1) 이상
- JDK 17
- 에뮬레이터 2대 (모바일 + 워치) 또는 실제 기기

### 에뮬레이터 생성

#### 1. 모바일 에뮬레이터
1. Android Studio > Tools > Device Manager > Create Device
2. Phone > **Pixel 6** 선택
3. System Image: **API 34** (Android 14) 다운로드 및 선택
4. Finish

#### 2. Wear OS 에뮬레이터
1. Device Manager > Create Device
2. **Wear OS** 카테고리 > Wear OS Small Round 또는 Square 선택
3. System Image: **API 33** (Wear OS 4) 이상 다운로드 및 선택
4. Finish

### 에뮬레이터 페어링

모바일 ↔ 워치 간 Data Layer 통신을 위해 반드시 페어링이 필요합니다.

1. 두 에뮬레이터를 모두 실행
2. 터미널에서 adb 포트 포워딩 설정:
   ```bash
   adb -s <워치_시리얼> forward tcp:5601 tcp:5601
   ```
3. 워치 에뮬레이터에서 **Wear OS 페어링 앱** 실행
4. 모바일 에뮬레이터에 **Wear OS by Google** 앱 설치 (Play Store)
5. Wear OS 앱에서 "새 시계 추가" > 에뮬레이터 선택하여 페어링

> **팁**: Android Studio의 Device Manager에서 Wear OS 에뮬레이터 생성 시 "Pair with phone" 옵션을 사용하면 더 간편합니다.

## 테스트 방법

### 모바일 앱의 워치 테스트 화면 사용

모바일 앱에 경기 시뮬레이션 전용 테스트 화면이 내장되어 있습니다.

**진입 경로**: 모바일 앱 실행 > 하단 **설정** 탭 > **개발자** 섹션 > **워치 테스트**

#### 자동 시뮬레이션

"시작" 버튼을 누르면 1회 공방 시나리오가 2~3초 간격으로 자동 재생됩니다.
경기 상태(점수, BSO, 주자, 투수/타자)가 매 이벤트마다 갱신되며 워치에 전송됩니다.

- 진행 상황은 프로그레스 바와 전송 로그로 확인
- "중단" 버튼으로 언제든 정지 가능

#### 수동 이벤트 전송

6개 이벤트 버튼으로 원하는 햅틱 이벤트를 즉시 워치에 전송합니다.
이 모드는 `/haptic` 경로로 이벤트 타입만 전송하므로 경기 상태는 변경되지 않습니다.

| 버튼 | 전송 이벤트 | 용도 |
|------|-------------|------|
| 홈런 | `HOMERUN` | 강한 진동 3회 확인 |
| 안타 | `HIT` | 중간 진동 2회 확인 |
| 득점 | `SCORE` | 강한 진동 2회 확인 |
| 아웃 | `OUT` | 짧은 진동 1회 확인 |
| 스트라이크 | `STRIKE` | 가벼운 진동 2회 확인 |
| 볼 | `BALL` | 약한 진동 1회 확인 |

## 테스트 데이터

### 시뮬레이션 시나리오

SSG vs KIA 1회 공방을 재현합니다. 총 19개 이벤트로 구성됩니다.

**초기 상태**:

| 항목 | 값 |
|------|-----|
| 홈팀 | SSG |
| 원정팀 | KIA |
| 점수 | 0 : 0 |
| 이닝 | 1회초 |
| 투수 | 양현종 |
| 타자 | 추신수 |

**1회초 (SSG 공격)**:

| # | 이벤트 | 설명 | 상태 변화 |
|---|--------|------|-----------|
| 1 | BALL | 1회초 — 볼 원 | B:1 |
| 2 | STRIKE | 스트라이크! | S:1 |
| 3 | BALL | 볼 투 | B:2 |
| 4 | HIT | 추신수, 좌전 안타! | 1루 주자, 타자→김현수 |
| 5 | STRIKE | 김현수에게 스트라이크 | S:1 |
| 6 | STRIKE | 연속 스트라이크! | S:2 |
| 7 | HOMERUN | 김현수! 투런 홈런!! | SSG 2:0, 주자 클리어, 타자→최정 |
| 8 | SCORE | SSG 2점 리드! | (상태 유지, 득점 알림) |
| 9 | BALL | 최정에게 볼 | B:1 |
| 10 | OUT | 최정, 플라이 아웃 | O:1, 타자→한유섬 |
| 11 | STRIKE | 한유섬에게 스트라이크 | S:1 |
| 12 | HIT | 한유섬, 중전 안타! | 1루 주자, 타자→박성한 |
| 13 | OUT | 박성한, 삼진 아웃 | O:2, 타자→이재원 |
| 14 | OUT | 이재원, 땅볼 아웃 — 체인지! | 이닝 전환 → 1회말 |

**1회말 (KIA 공격)**:

| # | 이벤트 | 설명 | 상태 변화 |
|---|--------|------|-----------|
| 15 | STRIKE | 나성범에게 스트라이크 | 투수→김광현, S:1 |
| 16 | BALL | 볼 | B:1 |
| 17 | HIT | 나성범, 우전 안타! | 1루 주자, 타자→김도영 |
| 18 | HOMERUN | 김도영!! 역전 투런 홈런!!! | KIA 0→2, 타자→최형우 |
| 19 | SCORE | KIA 역전! 2:2 → 2:3! | (득점 알림) |

**최종 상태**: SSG 2 : 3 KIA (1회말 진행중)

### 이벤트 타이밍

- 기본 이벤트 간격: **2초**
- HOMERUN 이벤트 후: **3초** (강한 진동 패턴 완료 대기)

### Data Layer 전송 경로

시뮬레이션은 두 가지 Data Layer 경로를 사용합니다:

| 경로 | 전송 방식 | 설명 |
|------|-----------|------|
| `/game/{timestamp}` | 자동 시뮬레이션 | 전체 경기 상태 + 이벤트 타입 |
| `/haptic/{timestamp}` | 수동 이벤트 버튼 | 이벤트 타입만 |

#### `/game` DataMap 키 구성

```
game_id     : String  ("test_001")
home_team   : String  ("SSG")
away_team   : String  ("KIA")
home_score  : Int     (0~)
away_score  : Int     (0~)
inning      : String  ("1회초", "1회말", ...)
ball        : Int     (0~3)
strike      : Int     (0~2)
out         : Int     (0~2)
base_first  : Boolean
base_second : Boolean
base_third  : Boolean
pitcher     : String
batter      : String
my_team     : String  (설정에서 선택한 응원팀)
event_type  : String  ("HOMERUN", "HIT", "OUT", "SCORE", "STRIKE", "BALL")
```

#### `/haptic` DataMap 키 구성

```
event_type  : String  ("HOMERUN", "HIT", "OUT", "SCORE", "STRIKE", "BALL")
```

## 관련 소스 파일

### 모바일 앱 (sender)

| 파일 | 역할 |
|------|------|
| `apps/mobile/.../wear/WearGameSyncManager.kt` | 경기 데이터 및 햅틱 이벤트 워치 전송 |
| `apps/mobile/.../ui/screens/WatchTestScreen.kt` | 테스트 UI (시뮬레이션 화면) |
| `apps/mobile/.../data/model/GameEvent.kt` | EventType enum 정의 |

### 워치 앱 (receiver)

| 파일 | 역할 |
|------|------|
| `apps/watch/.../DataLayerListenerService.kt` | 데이터 수신 + 햅틱 피드백 실행 |
| `apps/watch/.../data/GameData.kt` | 경기 데이터 모델 |

## Logcat 확인

### 모바일 앱 로그 (전송 확인)

```
# 필터 태그
WearGameSync
```

출력 예시:
```
D/WearGameSync: Game data sent: inning=1회초, score=2:0, event=HOMERUN
D/WearGameSync: Haptic event sent: STRIKE
```

### 워치 앱 로그 (수신 확인)

```
# 필터 태그
DataLayerListener
```

출력 예시:
```
D/DataLayerListener: Haptic feedback: HOMERUN
D/DataLayerListener: Haptic feedback: STRIKE
W/DataLayerListener: Vibrator not available    # 에뮬레이터에서는 정상
```

> **참고**: Wear OS 에뮬레이터는 실제 진동 하드웨어가 없으므로 `Vibrator not available` 또는 진동 없이 로그만 출력될 수 있습니다. 실제 진동 테스트는 물리적 워치 기기가 필요합니다.

## 트러블슈팅

### "No connected Wear nodes" 로그가 계속 출력됨
- 모바일 ↔ 워치 에뮬레이터 페어링이 안 되어 있음
- 위의 [에뮬레이터 페어링](#에뮬레이터-페어링) 섹션 참고

### 워치에서 이벤트가 수신되지 않음
1. 양쪽 앱이 모두 실행 중인지 확인
2. Logcat에서 `WearGameSync`로 필터링하여 전송 성공 여부 확인
3. `DataLayerListenerService`가 AndroidManifest.xml에 등록되어 있는지 확인
4. 두 에뮬레이터의 Google Play 서비스 버전 확인

### 에뮬레이터에서 진동이 안 느껴짐
- 정상 동작입니다. 에뮬레이터에는 진동 모터가 없습니다
- Logcat에서 `Haptic feedback: HOMERUN` 등의 로그로 호출 여부 확인
- 실제 진동 확인은 물리 기기 필요
