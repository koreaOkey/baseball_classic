## Context

야구봄은 폰 앱 + 워치 앱 한 쌍으로 동작하는 햅틱 중계 서비스다. 핵심 가치인 "손목으로 받는 현장감"은 워치 앱이 설치되어야만 성립한다. 그러나 현재 [SettingsScreen.kt:74-88](apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt#L74-L88) 어디에도 워치 앱 설치 여부가 노출되지 않으며, "워치 테스트" 항목은 개발자 섹션에 숨어 있다. 사용자는 자신의 워치에 앱이 깔려 있는지조차 모른다.

iOS 쪽은 [ios/watch/](ios/watch/)에 companion watchOS 앱이 존재하고 `WKCompanionAppBundleIdentifier` 로 폰 앱과 묶여 있어, 이론적으로는 폰 앱 설치 시 자동 설치되지만 사용자의 자동 설치 옵션 상태에 따라 누락이 생긴다.

Android Wear OS 쪽은 폰 앱과 워치 앱이 같은 `applicationId`(`com.basehaptic.mobile`)를 쓰지만 ([apps/watch/app/build.gradle.kts:31](apps/watch/app/build.gradle.kts#L31)), 워치에 별도 설치가 필요하다. 현재 manifest 에는 capability 선언이 없어 ([apps/watch/.../AndroidManifest.xml]) 폰 앱이 워치 앱 존재를 감지하려면 우회적 신호(DataLayer 메시지 응답 여부)에 의존해야 한다.

## Goals / Non-Goals

**Goals:**
- 사용자가 설정 탭을 열자마자 워치 앱 상태를 한눈에 볼 수 있다.
- 미설치 상태일 때 한 번의 탭으로 설치 경로(Play Store / Apple Watch 앱)에 진입할 수 있다.
- 워치 앱 실행 없이도 "설치됨" 상태를 감지할 수 있다 (Android: capability advertise, iOS: WCSession.isWatchAppInstalled).
- iOS / Android 양쪽 동일한 카드 디자인 + 팀컬러 일관성 유지.
- 페어링 안 됨 / 미설치 / 설치됨 3단계로 잘못된 신호(false positive/negative) 최소화.

**Non-Goals:**
- 워치 앱 자동 원격 설치 — iOS / Android 모두 OS 레벨에서 불가. 사용자 액션 필요.
- 워치 앱과의 실시간 통신 가능 여부(reachable) 확인 — `isReachable` 은 워치 foreground 의존이라 푸시 기반 동작과 맞지 않음. 별도 capability(설치 여부) 만 본다.
- 백엔드 변경 / 새 데이터 모델 / RLS 정책 — 본 변경은 클라이언트 한정.
- Live Activity / Dynamic Island 통합 — 별도 워크.
- 온보딩 화면 변경 — 메모리 [project_2026-04-14_community_tab.md] 와 동일하게 온보딩에는 가볍게만 언급 (별도 작업).

## Decisions

### D1. 상태 감지 방식: ping/pong 아닌 capability advertise

**Android**: `CapabilityClient.getCapability("basehaptic_watch_app", FILTER_REACHABLE)` 사용. 워치 앱 manifest 에 `<meta-data android:name="com.google.android.wearable.capability" android:value="basehaptic_watch_app" />` 선언. 설치만 되면 워치 앱 실행 없이도 capability 가 advertise 된다.

**iOS**: `WCSession.default.isPaired` (페어링) + `isWatchAppInstalled` (설치). 둘 다 워치 앱 실행 없이 동작한다.

**왜 ping/pong 안 쓰는지**: ping/pong 은 본질적으로 reachable(워치 앱 foreground/active) 측정이다. 이 앱은 푸시(APNs/FCM) 기반이라 워치 앱이 켜져 있을 필요가 없다 ([project_apns_todo.md] 참조). 워치 앱 안 켜져 있을 때 "연결 안 됨" 으로 표시하면 정상 사용자에게 잘못된 빨간불을 띄우는 셈이라 사용자 신뢰가 떨어진다.

**대안 검토**: `MessageClient` 로 ping → 응답 timeout. 단점 — 워치 DOZE 시 응답 지연 → false negative 빈발. 기각.

### D2. 상태 매트릭스 (3단계)

| 상태 | Android 감지 | iOS 감지 | UI |
|---|---|---|---|
| **PAIRED_NONE** | `NodeClient.getConnectedNodes()` 비어있음 | `WCSession.isPaired == false` | 회색 톤 글로우 + 회색 ! 배지 + 페어링 안내 + [워치/Wear OS 앱 열기] |
| **PAIRED_NO_APP** | nodes 있음, capability 없음 | `isPaired && !isWatchAppInstalled` | 빨간 글로우 + 빨간 ! 배지 + [워치 앱 설치하기] + [연결 확인] |
| **INSTALLED** | capability 에 1개 이상 노드 존재 | `isPaired && isWatchAppInstalled` | 초록 글로우 + 초록 ✓ 배지 + "워치 앱 설치됨" + [Watch / Wear OS 앱 열기] |

상태 결정 결과는 `WatchCompanionStatus` enum (Android: sealed class, iOS: enum) 으로 통일.

**비주얼 통일**: 세 상태 모두 동일한 골격(큰 워치 아이콘 + 상태색 radial-glow 배경 + 우상단 작은 상태 배지 + 헤더/서브 텍스트 + 하단 액션 버튼들)을 공유한다. INSTALLED 도 컴팩트 1줄이 아니라 동일한 expanded 카드 톤으로 — "정상 동작 중"의 시각 피드백이 카드의 가치이고, 비주얼이 줄어들면 사용자는 그 가치를 인지하지 못한다. Alert fatigue 우려는 글로우/배지의 색을 부드러운 초록(success)으로 처리해 경고와 시각 분리하는 것으로 대응한다.

### D3. 설치 유도 진입점

**Android**: Play Store Intent
```kotlin
Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("https://play.google.com/store/apps/details?id=com.basehaptic.mobile")
    setPackage("com.android.vending")
}
```
사용자가 Play Store 상세 페이지 상단의 기기 선택 드롭다운에서 **워치를 직접 선택**해야 한다. 이를 위해 카드 하단에 안내 텍스트:
> "Play Store에서 설치 대상 기기를 워치로 선택해 주세요."

**iOS**: `itms-watch://` URL scheme
```swift
if let url = URL(string: "itms-watch://") {
    UIApplication.shared.open(url)
}
```
이는 시스템 Watch 앱을 열어 사용 가능한 앱 목록으로 사용자를 보낸다. iOS 는 `WKCompanionAppBundleIdentifier` 로 묶여 있어 자동 설치 옵션이 켜져 있으면 사실상 자동 설치되므로 이 버튼은 fallback 역할.

### D4. 카드 노출 위치 = 설정 탭 최상단 첫 item

[SettingsScreen.kt:81-88](apps/mobile/app/src/main/java/com/basehaptic/mobile/ui/screens/SettingsScreen.kt#L81-L88) 의 `"설정"` 타이틀 다음, "응원 팀" 위. 이유:
- 설치 안 된 사용자는 한 번이라도 설정에 들어왔을 때 즉시 인지해야 한다.
- 설치된 사용자에게도 "정상 동작 중" 시각적 피드백을 준다 (alert fatigue 우려는 INSTALLED 상태에서 카드 높이를 줄여 보조 행 1줄로 간소화).

### D5. 디자인 토큰 — 팀컬러 따름

- 카드 배경: `Gray900` (기존 SettingsItem 과 동일)
- 모서리: `AppShapes.md`
- Primary 버튼(설치하기): `teamTheme.primary` — LG 빨강, 두산 남색 등 사용자 응원 팀 컬러 따름
- Secondary 버튼(연결 확인): outlined, `Gray700` border + `Gray300` 텍스트
- 경고 배지: `Color.Red` (고정)
- 성공 배지: `Color(0xFF4CAF50)` (기존 로그인 표시와 동일 톤, [SettingsScreen.kt:189-198] 참조)

iOS 도 동일 톤을 SwiftUI `Color` 로 매핑.

### D6. 상태 갱신 트리거

- 카드 컴포저블 진입 시 1회 동기화
- `onResume` (Android) / `onAppear + scenePhase == .active` (iOS) 시 재동기화
- [다시 확인] / [연결 확인] 버튼 탭 시 즉시 재조회 (UX 명시적 피드백)
- 자동 폴링 X — 배터리 보호

## Risks / Trade-offs

- **[Risk] Play Store Intent 가 사용자의 Play Store UI 변형(국가/베타 채널) 에 따라 워치 선택 드롭다운을 안 보여줄 수 있음** → Mitigation: 카드 하단에 명시적 안내 텍스트 + 실패해도 사용자가 직접 워치 모드로 전환 가능한 보조 안내 링크 제공.
- **[Risk] iOS 자동 설치 옵션이 꺼진 사용자 비율 알 수 없음** → Mitigation: PAIRED_NO_APP 상태 감지율을 일정 기간 로깅(이번 변경 범위 밖, 후속 작업)하여 fallback 노출 빈도 측정.
- **[Risk] capability 선언 후 첫 워치 앱 업데이트 전까지 기존 사용자에겐 PAIRED_NO_APP 으로 잘못 보일 수 있음** → Mitigation: 워치 앱 업데이트(capability 선언 추가) 와 폰 앱 카드 노출은 동일 릴리즈로 묶어 출시. 폰만 먼저 받은 사용자가 "재설치 필요" 라고 오해하지 않도록 카드 카피에 "워치 앱이 최신 버전이 아닐 수 있어요" 안내 추가 검토.
- **[Risk] 팀컬러가 강한 빨강(LG/한화) 일 때 빨간 경고 배지와 시각 충돌** → Mitigation: 배지는 작게(8dp) + 카드 좌측 상단 워치 아이콘 위에만 배치. Primary 버튼이 팀컬러여도 배지 위치가 달라 구분 가능.
- **[Trade-off] 카드를 항상 최상단에 두면 INSTALLED 사용자에게도 매번 보임** → 결정: "정상 동작 중" 의 시각 피드백이 가치 있다고 판단. 다만 INSTALLED 상태는 컴팩트 1줄로 축약하여 스크롤 방해 최소화.

## Migration Plan

1. 워치 앱 manifest 에 capability 선언 추가 → 워치 앱 새 빌드 배포
2. 폰 앱에 카드 + 상태 감지 추가 → 폰 앱 새 빌드 배포
3. **두 빌드는 동일 릴리즈 사이클로 묶어** Play Store / App Store 동시 게시
4. 롤백 시: 카드 컴포저블/뷰 호출만 제거(LazyColumn 첫 item 삭제)하면 됨. capability 선언은 잔존해도 무해.

## Open Questions

- INSTALLED 상태에서 카드를 1주 후 자동으로 컴팩트 모드로 전환할지? (현재 결정: 무조건 노출, 추후 사용자 피드백 보고 조정)
- iOS `itms-watch://` 가 모든 iOS 버전에서 동작하는지 (iOS 17+ 검증 필요). 미동작 시 `App-Prefs:` 또는 안내 텍스트만 표시하는 fallback 필요.
- "다시 확인" 버튼이 INSTALLED 상태에서도 필요한지, 아니면 설치 완료 후 자동 갱신만으로 충분한지 (사용성 테스트 결과에 따라 결정).
