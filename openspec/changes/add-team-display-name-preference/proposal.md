## Why

현재 앱은 응원팀을 선택하면 대부분의 화면에서 `베어스`, `트윈스` 같은 마스코트명을 기본 표시한다. 사용자가 온보딩에서 본인이 선택한 팀을 `두산` 같은 팀명으로 볼지, `베어스` 같은 마스코트명으로 볼지 직접 고를 수 있어야 한다.

## What Changes

- 온보딩 팀 선택 직후 팀명/마스코트명 표시 방식을 고르는 팝업을 추가한다.
- 기존 사용자도 다음 앱 실행 시 1회 표시 방식 선택 팝업을 본다.
- 설정에서 표시 방식을 다시 변경할 수 있게 한다.
- Android/iOS 모바일 화면과 Wear OS/watchOS 워치 스코어보드가 선택한 표시 방식을 따른다.
- 경기 시작 visible push도 수신자의 표시 방식 선호에 맞춰 팀 라벨을 만든다.
- 팀 식별/구독/테마 동기화에는 기존 팀 코드를 계속 사용하고, 표시 문자열만 분리한다.

## Capabilities

### New Capabilities
- `team-display-name-preference`: 사용자가 응원팀 표시 방식을 팀명 또는 마스코트명 중 선택하고, 모바일/워치/푸시에 일관되게 적용하는 기능.

### Modified Capabilities
- `mobile-android`: Android 모바일 온보딩, 설정, 팀 라벨 표시가 표시 방식 선호를 따른다.
- `mobile-ios`: iOS 모바일 온보딩, 설정, 팀 라벨 표시가 표시 방식 선호를 따른다.
- `watch-android`: Wear OS 스코어보드의 팀 라벨이 모바일에서 동기화된 표시 방식 선호를 따른다.
- `watch-ios`: watchOS 스코어보드의 팀 라벨이 iPhone에서 동기화된 표시 방식 선호를 따른다.
- `ingest`: 경기 시작 visible push가 수신자별 표시 방식 선호를 사용한다.

## Impact

- Android: 팀 모델, 온보딩/설정 UI, 표시 helper, 워치 설정 동기화, 푸시 구독 payload.
- iOS: 팀 모델, 온보딩/설정 UI, 표시 helper, WatchConnectivity 설정 동기화, 푸시 구독 payload.
- Watch: Wear OS/watchOS 스코어보드 표시명 정규화 로직.
- Backend/API: 푸시 토큰/팀 구독 schema, 경기 시작 visible push fanout grouping.
- DB: 푸시 토큰/팀 구독 및 사용자 설정에 표시 방식 선호 컬럼 추가.
