# Watch-Initiated Sync Ad Flow (경기 알람 → 워치 팝업 → 폰 광고 → 동기화)

## Why

경기 시작 시 워치 주도로 관람을 시작하는 흐름을 양 플랫폼 동일하게 정비. 기반 흐름(워치 독립 경기 감지 → 팝업 → 수락 → 폰 광고 게이트 → 동기화)은 이미 구현돼 있었으나 네 가지 갭이 있었다: (1) Android 워치 수락 시 폰 앱이 자동 실행되지 않고 알림 탭에만 의존, (2) iOS 는 폰 앱이 비활성일 때 수락 응답이 유실 위험(백그라운드에서 광고 표시 불가, 알림 폴백 없음), (3) 수락 직후 워치에 "폰에서 확인하세요" 안내 부재, (4) 동기화 완료 시 "워치와 동기화되었습니다" 안내 부재.

## What Changes

### 워치 (양쪽)
- 팝업 문구 확정: "관람하시겠습니까?" / "폰에서 광고 관람 후 이용 가능합니다." + 예/아니오 버튼.
- 수락 직후 "폰에서 확인하세요 / 광고 관람 후 관람이 시작됩니다" 안내 카드 4초 노출 (iOS `CheckPhoneNoticeView`, Wear `CheckPhoneNoticeCard`).
- 이미 동기화 중인 경기는 팝업 미노출 (기존 가드 유지).

### Wear → Android 폰 자동 실행
- `PhoneAppLauncher` 신설: 수락 시 `RemoteActivityHelper`("Open on phone")로 폰 앱을 `basehaptic://watch-sync` 딥링크로 원격 실행. 실패 시 기존 고우선 알림 폴백 유지.
- 폰 manifest 에 `basehaptic://watch-sync` BROWSABLE 인텐트 필터 추가. 의존성 `androidx.wear:wear-remote-interactions:1.0.0`.

### iOS 폰 비활성 시 알림 폴백
- `PhoneConnectivityManager`: 수락 응답 수신 시 앱이 비활성이면 로컬 알림("워치 관람 광고 확인 / 탭하여 광고 관람 후 워치 관람이 시작됩니다") 게시 — Android `notifyPhoneAdRequired` 대응. 응답 소비 시 알림 제거.
- `BaseHapticApp`: 응답 소비를 scenePhase `.active` 로 게이트 — 백그라운드 수신분은 보류했다가 활성화(알림 탭 포함) 시점에 광고 플로우 진입.

### 동기화 완료 안내 (양쪽 폰)
- 광고 통과 → `completeSync` 시 "워치와 동기화되었습니다" 잠시 노출 (iOS 상단 캡슐 배너 2.2초, Android Toast).

### 광고 재사용 규칙 (기존 유지)
- 경기당 1회 게이트(`WatchSyncAdLedger`): 이미 본 경기는 광고 생략 즉시 동기화.

## Capabilities

### watch-sync-onboarding

- 경기 시작 감지 시 미동기화 상태에서만 워치 팝업이 뜬다.
- 예 → Android 는 폰 앱 자동 실행, iOS 는 폰 로컬 알림 1탭 → 광고 → 동기화 시작 + 완료 안내.
- 아니오 → 아무 일도 일어나지 않는다.

## Impact

- iOS: `WatchSync/PhoneConnectivityManager.swift`, `BaseHapticApp.swift`, `ios/watch/BaseHapticWatch/BaseHapticWatchApp.swift`
- Android: `apps/watch/.../PhoneAppLauncher.kt`(신규), `MainActivity.kt`(watch), `build.gradle.kts`(watch), `apps/mobile/.../AndroidManifest.xml`, `MainActivity.kt`(mobile)
- 백엔드 영향 없음.

## Verification

- iOS 폰·워치 타깃, Android :mobile·:watch 4개 빌드 전부 통과.
- 실기기 E2E(워치 팝업 → 폰 광고 → 동기화) 검증 잔존.
