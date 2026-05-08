## Why

스토어 업데이트 후 사용자가 "뭐가 바뀌었는지" 알 수 있는 채널이 없다. 현재 iOS 만 `ContentView.onAppear` 에 `last_seen_update_version` 비교 + 단순 `.alert()` ("테마 상점이 오픈되었습니다!\n다양한 워치 테마를 만나보세요.") 한 줄을 띄우는 수준이고 Android 는 아예 없다. 스토어 디스크립션이나 인앱 공지로는 도달률이 낮아서, 업데이트 직후 한 번 노출되는 모달로 신규 기능을 짧게 안내하면 사용자 인지도가 크게 올라간다.

`in-app-update-prompt` (e05df2d) 는 "스토어에 새 버전이 있어요 → 받으세요" 를 띄우는 *업데이트 유도* 기능이고, 본 제안은 *업데이트 직후 무엇이 바뀌었는지* 보여주는 별개 기능이다. 둘은 보완 관계이며 코드 경로도 분리한다.

## What Changes

- iOS: `BaseHapticApp.swift` `ContentView.onAppear` 의 단발성 `.alert()` 를 제거하고, 새 컴포넌트 `WhatsNewSheet` 를 `.sheet(item:)` 로 띄운다. 데이터는 신규 `Models/ReleaseNotes.swift` 에서 버전별 항목을 보유한다.
- Android: 신규 `data/model/ReleaseNotes.kt` + `ui/components/WhatsNewDialog.kt` 를 만들고, `MainActivity.kt` 의 `BaseHapticApp` Composable 에 버전 비교 + Compose `Dialog` 노출 로직을 추가한다. SharedPreferences 키는 iOS 와 동일한 `last_seen_update_version` 사용.
- 설정 → "버전" 항목 클릭 시 동일한 모달을 다시 띄우는 진입점 추가 (iOS `SettingsScreen.swift`, Android `SettingsScreen.kt`). 자동 노출과 동일 컴포넌트 재사용.
- 신규 설치 사용자에게는 노출하지 않음. `last_seen_update_version` 이 빈 값이면 현재 버전을 즉시 저장하고 모달 스킵 (온보딩 직후 광고 같은 화면이 추가로 뜨는 경험 방지).
- 워치(워치 OS / Wear OS) 는 노출 대상이 아님. 폰 앱에서만.
- 백엔드 변경 없음. 릴리즈 노트는 앱 빌드와 함께 하드코딩.

## UX

모달은 다음 세 영역으로만 구성된다:

1. **헤더** — `NEW v{version}` 노란 배지, "업데이트 안내" 제목, 한 줄 서브타이틀.
2. **체크 리스트** — 팀 컬러 원형 체크 아이콘 + bullet 텍스트 (2~6 줄 권장).
3. **확인 버튼** — 풀폭 단일 버튼. 탭 시 모달 닫기.

"나중에" 보조 버튼, "다시 보지 않기" 체크박스, X 닫기 아이콘 모두 미사용. 한 번 뜨면 "확인" 으로만 닫는다(시트 swipe-to-dismiss 와 다이얼로그 외부 탭은 시스템 기본 동작으로 그대로 닫힘).

## Capabilities

### New Capabilities

- `mobile-ios:whats-new-popup`: iOS 폰 앱이 업데이트 후 첫 콜드 스타트 1회 모달로 릴리즈 노트를 표시한다. 설정 → "버전" 클릭으로도 같은 모달을 노출한다.
- `mobile-android:whats-new-popup`: Android 폰 앱이 동일 동작을 한다.

### Modified Capabilities

- 없음.

## 트리거 명세

| 트리거 | 조건 |
|---|---|
| 자동 노출 | 온보딩 통과 + `last_seen_update_version != currentBundleVersion` + `ReleaseNotes.notes(for: currentBundleVersion) != nil` |
| 수동 노출 | 설정 → "버전" 항목 클릭. `ReleaseNotes.notes(for: currentBundleVersion)` 이 nil 이면 클릭 무시 |
| 신규 설치 차단 | `last_seen_update_version` 이 빈 값이면 즉시 현재 버전 저장 + 자동 노출 스킵 |
| 자동 노출 1회 보장 | 자동 모달이 뜨는 순간 `last_seen_update_version` 을 현재 버전으로 저장 |

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| 모달이 온보딩과 동시 노출되어 신규 사용자 부담 | `showOnboarding == false` 가드 |
| 광고 모달과 겹쳐서 두 개가 동시에 뜸 | 본 모달은 `ContentView` 의 onAppear 에서만 트리거. 광고는 Rewarded(상점 화면 진입 시)라 home 진입 시점엔 충돌 없음 |
| 빈 릴리즈 노트로 모달이 빈 채로 뜸 | `ReleaseNotes.notes(for: version)` 이 nil 이면 자동/수동 모두 노출하지 않음 |

## Non-Goals

- 백엔드에서 릴리즈 노트를 동적으로 내려보내는 엔드포인트.
- 워치 / Wear OS 동시 노출.
- 신규 사용자용 환영(welcome) 모달.
- A/B 테스트, 분석 이벤트 (Mixpanel/Amplitude 미도입 상태).
- LiveActivity / Dynamic Island 변경.

## Rollout

1. 본 PR 머지 → 다음 iOS / Android 빌드에 함께 포함.
2. iOS Info.plist `MARKETING_VERSION` 과 Android `versionName` 을 새 버전(예: `1.0.3`)으로 동시 bump.
3. `ReleaseNotes.all` 에 동일한 카피로 양 플랫폼 entry 추가.
4. 스토어 심사 통과 후 사용자 업데이트 시 자동 노출.

### Rollback

- `git revert` 한 번. UserDefaults / SharedPreferences 키 `last_seen_update_version` 은 잔존하지만 동작에는 영향 없음.

## Status

- [x] iOS 구현
- [x] Android 구현
- [x] 설정 → "버전" 클릭 진입점
- [x] UX 단순화 (확인 버튼 단일)
- [x] 빌드 확인 (iOS + Android)
- [x] v1.0.3 카피 반영 (`워치 앱 연동 가이드 추가`, `워치로 투구수 확인 기능 추가`)
- [x] 버전 bump (iOS 1.0.3 / Android 1.0.3 / Wear 1.0.3)
- [ ] 커밋 / 푸시
