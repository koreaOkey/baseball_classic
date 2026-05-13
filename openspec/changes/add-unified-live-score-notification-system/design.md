## Context

야구봄 앱은 KBO 경기 중 실시간 이벤트(SCORE/HOMERUN/HIT/WALK/STEAL 등)를 햅틱·노티·마스코트 애니메이션으로 사용자에게 전달한다. 현재 운영 자산:

- **iOS Live Activity** ([ios/liveactivity/BaseHapticLiveActivity/BaseHapticLiveActivityWidget.swift](ios/liveactivity/BaseHapticLiveActivity/BaseHapticLiveActivityWidget.swift)): iPhone 잠금화면 + Dynamic Island. ActivityKit 기반, 이벤트 시 in-place 갱신.
- **iOS 워치 푸시 핸들러** ([ios/BaseHaptic Watch App/WatchAppDelegate.swift](ios/BaseHaptic Watch App/WatchAppDelegate.swift) `didReceiveRemoteNotification`, `willPresent`): foreground 시 노티 suppress(빈 옵션 반환), 마스코트 애니메이션 재생 트리거.
- **iOS 워치 확장 런타임 세션** ([ios/BaseHaptic Watch App/WatchConnectivityManager.swift](ios/BaseHaptic Watch App/WatchConnectivityManager.swift)): 경기 LIVE 동안 화면 ON. 1시간 캡 시 cyclic 재시작 + APNs fallback.
- **워치 독립 APNs 수신** (2026-04-05 구현, project_2026-04-05_watch_independent_sync): 폰 없이도 워치가 직접 APNs로 이벤트 수신.
- **Android 워치 ongoing 노티 shell** ([apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt:124-185](apps/watch/app/src/main/java/com/basehaptic/watch/MainActivity.kt#L124)): IMPORTANCE_LOW, OngoingActivity API 사용. **현재는 정적 "경기 관람 중" 텍스트**만 표시.
- **Android 워치 wake/launch** ([apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt:402](apps/watch/app/src/main/java/com/basehaptic/watch/DataLayerListenerService.kt#L402) `wakeScreenForEvent`): 모든 이벤트에 wake lock + `FLAG_ACTIVITY_NEW_TASK`로 앱 자동 진입.
- **Android Wear OS 타일** ([apps/watch/app/src/main/java/com/basehaptic/watch/tile/GameTileService.kt](apps/watch/app/src/main/java/com/basehaptic/watch/tile/GameTileService.kt)): 스코어/이닝/BSO 표시, 5초 freshness + 이벤트 트리거 갱신.
- **현재 설정 토글**: `live_haptic_enabled`, `ball_strike_haptic_enabled`, `event_video_enabled` (이벤트 멀티 셀렉트 미구현)

**제약**: watchOS는 백그라운드에서 앱 강제 foreground launch 불가(Apple OS 정책). Android Wear OS는 가능하지만 손목 미착용 가능성 고려. 양 플랫폼 모두 노티는 같은 identifier 사용 시 in-place replace 가능. iPhone Live Activity는 ActivityKit으로만 갱신 가능(별도 채널).

**스테이크홀더**: KBO 10개 구단 팬 사용자, iOS·Android 양 OS, 배터리/네트워크 제약 환경(이동 중·운동 중 관람).

## Goals / Non-Goals

**Goals:**
- 워치 홈/잠금 상태에서도 사용자가 손목 들면 즉시 최신 스코어 확인 가능
- 폰 잠금 상태에서도 iOS·Android 동등하게 라이브 스코어 노출
- 사용자가 선택한 이벤트만 long-look expand로 강하게 알림. 미선택 이벤트는 ongoing 스코어 갱신으로만 조용히 처리
- 워치+폰 동시 진동 등 중복 알림 제거 (한 이벤트 = 한 채널)
- 이미 구현된 자산(iOS Live Activity, 워치 독립 APNs, 워치 fg 마스코트 애니, Android 워치 ongoing shell)을 그대로 활용·확장

**Non-Goals:**
- 시나리오 1(P-fg + W-fg): 기존 in-app UI 유지, 변경 없음
- 시나리오 7(P-off + W-fg): 이미 구현됨, 변경 없음
- Android Wear OS 타일 deprecate: 별도 결정. 본 change에서는 그대로 유지.
- iOS watchOS Smart Stack 위젯·복합기능: 본 change 범위 밖
- 실제 야구 리플레이 영상 재생: 워치 이벤트 "영상" = 마스코트 펭귄 캐릭터 애니메이션. 노티 액션에 "영상보기" 같은 버튼 만들지 않음
- 미수신 이벤트 큐잉·"조용한 시간대" 정책: drop. 깨어났을 때 최신 스코어 + 최근 이벤트 1개만
- 백엔드 push payload 구조 대규모 개편: 가능한 한 기존 payload에 필드 추가(또는 클라이언트 가드 우선)

## Decisions

### 1) 노티 채널 통합: "ongoing 스코어 + 같은 id replace" 패턴

**선택**: 워치/폰 양쪽 모두 라이브 스코어를 **하나의 ongoing notification**(같은 id)으로 관리. 이벤트 발생 시 같은 id를 `notify()` 또는 `UNNotificationRequest`로 replace해 in-place 갱신. 선택 이벤트는 같은 id를 **잠시 expand 스타일**(BigTextStyle / long-look)로 강조했다가 ongoing으로 복귀.

**근거**:
- 노티 스택 깨끗하게 유지(같은 id replace로 중복 없음)
- iOS/Android UX 동일 패턴 → 디자인·문서 단일화
- 사용자가 dismiss하지 않는 한 항상 최신 정보 노출

**대안**:
- 이벤트마다 별개 notification id로 띄우기 → 스택 폭증, dismiss 부담
- 타일/위젯 중심 → iOS watchOS는 별도 WidgetKit 타깃 필요, 개발 비용 큼. 본 change에서는 노티로 통일.

### 2) 워치 우선 햅틱 정책 (suppress 룰)

**선택**: 폰 측 햅틱·헤드업 노티 트리거 직전에 가드:
- iOS 폰: `WCSession.default.isPaired && WCSession.default.isReachable` 체크. true면 햅틱·헤드업 suppress(단, Live Activity는 갱신).
- Android 폰: `Wearable.NodeClient`로 연결된 watch node 존재 확인. 있으면 햅틱·헤드업 suppress(단, ongoing notification은 갱신).

워치 측은 기존대로 정상 발화. ongoing 스코어 갱신과 Live Activity 갱신은 항상 동작(시각 채널이라 시끄럽지 않음).

**근거**:
- 워치 페어링된 사용자에게 폰+워치 동시 진동은 최대 불편 요소
- "정보 표시(Live Activity/ongoing 노티)는 양쪽 모두, **능동 알림(햅틱·헤드업)은 워치만**"이 자연스러운 분리

**대안**: 사용자 설정으로 노출 → 결정 부담 가중. 자동 가드가 더 깔끔.

**Trade-off**: 워치가 일시적으로 unreachable이지만 페어링은 된 경우 폰이 발화 안 함. → 사용자가 워치 미착용 상태에서 이벤트 놓칠 가능성. **3) 결정으로 보완**.

### 3) "워치 OFF / 미착용" 상황은 폰도 발화 안 함 (시나리오 3)

**선택**: 워치가 페어링되어 있으면 워치 측에서만 햅틱 발화. 워치가 OFF/잠금/미착용이면 폰도 햅틱 안 함(시나리오 3, 6). 단, **Live Activity·ongoing 스코어 갱신은 항상 양쪽 동작**.

**근거**: 손목 미착용 = 폰을 보고 있을 가능성 높음. 폰 fg면 in-app UI로 충분. 폰 bg면 Live Activity로 확인 가능. 굳이 진동까지 보낼 필요 없음.

**대안**: 워치 OFF 감지되면 폰으로 fallback → 워치 미착용/배터리 부족 같은 정상 상황에서 폰이 시끄러워짐. 사용자가 "둘 다 안 보고 있다"고 가정하는 게 안전.

**Open Question**: 워치 OFF 정확 감지는 어려움(워치 배터리, 미착용, 잠시 sleep 구분 X). 본 change에서는 `isReachable=false`도 "워치 OFF"로 간주.

### 4) 이벤트 필터(`preferred_event_types`)는 long-look expand에만 적용

**선택**: ongoing 스코어 갱신은 모든 이벤트에 대해 항상 실행. 사용자 선택 이벤트 타입에 해당하는 이벤트만 **long-look으로 잠시 expand**(BigTextStyle/long-look) + 햅틱. 미선택 이벤트는 ongoing in-place 갱신만(조용히).

**근거**:
- 스코어 자체는 항상 최신이어야 함(필터링하면 "스코어가 왜 이상하지?" 사용자 혼란)
- 능동 알림(햅틱·expand)만 사용자 선호에 따라 제어
- 설정 문구 단순화 가능: "선택한 이벤트만 알림"

**대안**: ongoing 자체에도 필터 적용 → 스코어 부정확. 거부.

### 5) Android 폰 Live Activity = Custom ongoing notification + 잠금화면 노출

**선택**: Android는 ActivityKit 같은 OS API 없음. `NotificationCompat.Builder` + `setOngoing(true)` + custom RemoteViews(BigContentView)로 잠금화면에 스코어/이닝/BSO 노출. Category `MSG`/`STATUS`. Same notification id로 in-place replace.

**근거**:
- 이미 워치쪽 ongoing 패턴과 동일 → 코드 패턴 재사용
- MediaStyle은 미디어 컨트롤 의미 강해서 부적합. Status/Progress 카테고리 사용
- 잠금화면 표시는 채널 `setLockscreenVisibility(VISIBILITY_PUBLIC)`로 제어

**대안**: 별도 foreground service → 폰에서는 불필요. Notification만으로 충분.

### 6) 이벤트 expand 노출 시간 = 현재 정책(3초) 유지

**선택**: long-look/BigText expand 후 3초 뒤 ongoing 스코어로 복귀. 별도 조정 안 함.

**근거**: 사용자가 손목 들고 보기엔 짧을 수 있으나, ongoing에 "최근 이벤트 1개" 라인을 항상 노출(아래)하므로 놓쳐도 거기서 확인 가능.

### 7) ongoing 노티에 "최근 이벤트 1개" 항상 노출

**선택**: ongoing 콘텐츠에 항상 "최근 이벤트(예: 김현수 솔로 홈런 · 9회초)" 한 줄 포함. 새 이벤트 들어오면 그 줄 교체. 사용자가 손목 늦게 들어도 직전 이벤트는 항상 확인 가능.

**근거**: expand 노출 시간 짧음을 보완 + 큐잉 제거(미수신 시 최신만)와 일관.

### 8) 큐잉 제거 — drop + 깨어났을 때 최신만

**선택**: 양쪽 모두 off일 때 발생한 이벤트는 백엔드/클라이언트 모두 큐잉하지 않음. 디바이스 깨어날 때 ongoing 노티가 자동으로 최신 스코어 + 최근 이벤트 1개만 표시.

**근거**: 큐잉은 폭탄 노티 위험·정보 가치 낮음. "지금 어떻게 됐지?" 알면 충분.

### 9) ongoing 영역은 설정 UI 미노출

**선택**: 사용자 설정에는 "선택한 이벤트만 알림" 한 줄(이벤트 멀티 셀렉트)만 표시. ongoing 스코어 노티는 "항상 진행 중" 영역으로 시각적·문구적 분리, 토글 없음.

**근거**: 사용자 멘탈 모델 단순화. ongoing은 라이브 경기 중 자연스러운 status indicator로 인식.

**Trade-off**: ongoing 자체 끄고 싶은 사용자 일부 있을 수 있음. → 기존 `live_haptic_enabled` 마스터 OFF로 우회 가능.

## Risks / Trade-offs

- **[iOS Live Activity + push 노티 시각적 중복]** 잠금화면에 Live Activity 떠 있는데 같은 정보 push 노티 또 오면 위·아래로 두 번 보임 → 실기기 테스트 후 결정. 발생 시 iOS 폰에서 Live Activity 활성 동안 push 헤드업 suppress 가드 추가.
- **[워치 페어링 감지 false negative]** 워치가 unreachable인 짧은 순간에 이벤트 발생 시 폰이 햅틱 발화 안 할 수 있음 → 시나리오 5/6의 정상 동작으로 간주. 사용자가 ongoing에서 확인 가능.
- **[ongoing 동적 콘텐츠 잠금화면 노출 정책]** Android `setLockscreenVisibility` 설정 필요. 사용자 OS 설정으로 비공개일 경우 스코어 안 보일 수 있음 → 안내 문구 또는 onboarding 추가 고려.
- **[Android 워치 wakeScreenForEvent 정책]** 현재는 모든 이벤트에 앱 자동 launch. 필터 도입 시 미선택 이벤트는 wake 안 함(ongoing 갱신만) → 사용자가 "왜 안 켜져?" 혼란할 수 있음. 설정 문구로 명확히 안내.
- **[배터리 영향]** ongoing 노티 + Live Activity는 시스템 레벨 라이트한 채널이지만, 워치 ongoing 노티 자주 replace 시 시스템 wake 빈도 증가 가능 → 이벤트 단위로만 replace(스코어 변경 시), 매 push마다 replace는 피함.
- **[BREAKING: Android 워치 정적 "경기 관람 중" 텍스트 제거]** 기존 사용자가 익숙한 표현이 사라짐 → 대체 라벨이 "LG 5:4 두산 · 9회초" 같은 정보형이라 가치 더 큼. 별도 마이그레이션 안내 불필요.

## Migration Plan

1. 백엔드: `user_preferences.preferred_event_types` 컬럼 추가(Supabase migration). 기본값 = 모든 이벤트 선택. push payload에 이벤트 타입이 이미 포함되어 있으면 재활용, 없으면 추가.
2. iOS 폰·Android 폰 동시 배포: 설정 UI에 이벤트 멀티 셀렉트 추가, 워치 우선 햅틱 suppress 가드 추가, Android 폰 라이브 스코어 ongoing notification 신설.
3. iOS 워치·Android 워치 동시 배포: ongoing 노티 동적 콘텐츠 갱신, long-look expand 필터 가드 추가.
4. 폰·워치 버전 mismatch 대응: 워치가 구버전이면 ongoing 정적 텍스트 유지. 폰이 구버전이면 워치만 신규 동작(폰은 기존 헤드업). 단방향 호환.
5. 롤아웃: 내부 테스트 → 실기기 검증(iOS Live Activity + push 중복 여부 포함) → 점진 배포(staged rollout 옵션 검토).
6. 롤백: notification id·payload 호환되므로 어느 한쪽 롤백해도 큰 문제 없음. 설정 컬럼은 컬럼 추가만이라 롤백 시 무시.

## Open Questions

- iOS Live Activity 활성 상태에서 같은 정보 push 노티가 동시 표시되는가? 시각적 노이즈 발생 시 push 헤드업 suppress 정책 추가 필요. (실기기 테스트 필요)
- Android Wear OS 타일 GameTileService는 본 change 이후에도 유지 가치가 있는가? ongoing 노티가 같은 정보를 제공하므로 deprecate 고려 — 별도 후속 change로 결정.
- 이벤트 필터의 기본값: 전부 선택 vs 핵심 이벤트(SCORE/HOMERUN)만 선택. 사용자 인지·노이즈 사이 균형 — 디자이너 협의 필요.
- 백엔드 push payload에 이벤트 타입이 항상 포함되는가? 없으면 추가 → 클라이언트 가드 단순화 vs 백엔드 변경 범위.
