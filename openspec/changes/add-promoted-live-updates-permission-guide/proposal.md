# Guide Users to Enable "Live Updates" for Promoted Live Score

## Why

`POST_PROMOTED_NOTIFICATIONS`는 매니페스트에 선언돼 install 시 자동 granted되지만, **실제 승격은 별도 appop("실시간 업데이트/Live Updates" 토글)** 이 좌우한다. 삼성 One UI 등은 이 appop이 **기본 거부**라, promoted를 켠 사용자도 `canPostPromotedNotifications()=false` → 조용히 Classic 카드로 폴백된다. 앱에는 이를 켜도록 안내하는 경로가 전혀 없어, 삼성 실사용자는 promoted 잠금화면 고정을 사실상 못 본다.

지원 기기(API36+)이고 사용자가 promoted를 켰는데 시스템 설정이 막고 있을 때, 이를 감지해 설정 딥링크로 안내한다.

## What Changes

- `LiveScoreNotificationManager`:
  - `isPromotedBlockedBySystemSetting(context)` — API36+ && promoted 켜짐 && `!canPostPromotedNotifications()`.
  - `openLiveUpdatesSettings(context)` — `ACTION_APP_NOTIFICATION_SETTINGS` 딥링크(실패 시 앱 상세 설정 폴백).
  - `KEY_PROMOTED_PROMPT_DISMISSED` + `isPromotedPromptDismissed` / `markPromotedPromptDismissed` — 1회성 프롬프트 상태.
- **#1 설정 배너** (`SettingsScreen`): "잠금화면 라이브 스코어" 섹션에서 blocked 상태면 스위치 아래 안내 배너("잠금화면 고정이 꺼져 있어요" + "켜기") 노출. 탭 시 설정 딥링크. `ON_RESUME`마다 재확인해 켜고 돌아오면 사라짐.
- **#2 라이브 진입 1회성 프롬프트** (`LiveGameScreen`): 라이브 진입 시 blocked && 미닫힘이면 다이얼로그("설정 열기"/"나중에") 1회 노출. 어느 버튼이든 `markPromotedPromptDismissed`로 다시 뜨지 않음.

## Capabilities

### lock-screen-live-score

- 승격 지원 기기에서 promoted가 켜졌지만 시스템 "실시간 업데이트"가 꺼진 경우, 앱이 이를 감지해 사용자에게 설정 딥링크로 안내한다.
- 안내는 두 지점(설정 배너 + 라이브 진입 프롬프트)에서 제공하며, 프롬프트는 1회성이다.
- 사용자가 실시간 업데이트를 켜고 돌아오면 배너는 자동으로 사라진다.

## Impact

- `apps/mobile/.../push/LiveScoreNotificationManager.kt` — 헬퍼 3종 + 딥링크 + pref 키.
- `apps/mobile/.../ui/screens/SettingsScreen.kt` — 배너 컴포저블 + 스위치 item 통합.
- `apps/mobile/.../ui/screens/LiveGameScreen.kt` — 진입 프롬프트.
- 백엔드·iOS·Watch 영향 없음.

## Non-Goals

- iOS 대응(Live Activity는 별도 권한 모델).
- 승격 자체를 강제로 켜기(시스템 appop은 사용자만 토글 가능 — 딥링크로 안내만).
- Classic 폴백 로직 변경(기존 유지).
