## Why

이벤트(홈런/안타/득점/병살/승리) 발생 시 워치에서 자동 재생되는 영상이 사용자에 따라 불필요하거나 방해가 될 수 있다. 사용자가 모바일 설정에서 이 영상 재생을 끄거나 켤 수 있도록 토글을 제공하되, 햅틱 진동은 그대로 유지하여 알림 자체는 살린다.

## What Changes

- iOS/Android 모바일 설정 화면 "알림" 섹션에 **"이벤트 영상 알림"** 토글 추가 (기본 ON).
- 토글 값은 모바일 로컬에 저장 (iOS `UserDefaults` / Android `SharedPreferences`, 키: `event_video_enabled`).
- 모바일에서 토글 변경 시 즉시 워치로 동기화 (iOS `WCSession.updateApplicationContext`, Android Wearable `DataClient` `/settings/current` 경로).
- 워치(iOS·Android)는 수신한 값을 자체 저장소에 저장하고, 영상 재생 분기에서 OFF 시 video token 발급/transition 차단. **햅틱 경로는 그대로 유지** — 영상만 막힘.
- 앱 진입 시 모바일이 현재 토글 값을 워치에 한 번 push하여 페어링 직후 또는 재설치 시에도 일치 보장.

## Capabilities

### New Capabilities
- `event-video-toggle`: 모바일 설정 토글로 워치 이벤트 영상(홈런·안타·득점·병살·승리) 재생을 켜고 끄는 기능. 햅틱은 영향 없음.

### Modified Capabilities
- None

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 모바일↔워치 동기화 지연으로 토글 직후 영상이 한 번 더 재생될 수 있음 | `setUrgent()` (Android) / `sendMessage` 폴백 (iOS)로 즉시 전달. 1초 이내 반영 기대. |
| 워치 재설치 시 기본값으로 초기화되어 폰 설정과 어긋남 | 폰 앱 진입 시점에 항상 한 번 push (BaseHapticApp `.onAppear`, MobileMainActivity `LaunchedEffect(Unit)`). |
| 영상은 끄지만 햅틱은 유지하는 의도가 사용자에게 분명하지 않을 수 있음 | 토글 서브타이틀 "홈런·안타·득점 등 이벤트 발생 시 워치 영상 재생"으로 영상 한정임을 명시. |

## Status

- [x] 구현 완료
- [ ] 빌드 검증 (iOS xcodebuild + Android compileDebugKotlin)
- [ ] 실기기 검증 (모바일 토글 OFF → 워치 영상 차단, 햅틱 유지)
- [ ] 커밋 및 푸시
