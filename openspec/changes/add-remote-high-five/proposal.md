## Why

"야구봄"의 핵심 가치는 "보지 않아도 손목으로 느끼는 현장감"이다. 지금은 득점·홈런 순간의 감정이 개인 워치 안에서 끝나는데, 같은 팀을 응원하는 근처 팬과 그 순간을 **공유**할 수 있다면 혼자 보는 경기에도 커뮤니티가 생긴다. Figma 프로토타입([Real-time Baseball Broadcast/src/components/Settings.tsx:270-288](Real-time Baseball Broadcast/src/components/Settings.tsx#L270-L288))에 "원격 하이파이브 — 근처 팬과 득점 순간 공유" 토글이 이미 구상돼 있었지만 실제 앱으로 이식된 적이 없다. 커뮤니티 탭(`add-community-tab`, LG/두산 파일럿)과 같은 시점에 붙이면 "우리 팀 팬을 만난다"는 스토리가 완결된다.

## What Changes

- 내 팀 득점·홈런 이벤트 수신 순간, 주변에 같은 팀을 응원하는 "야구봄" 사용자가 있으면 양쪽 워치에 동시에 "하이파이브" 햅틱이 울린다.
- 근접 감지는 **BLE 광고 기반**(오프라인, 서버 위치 공유 없음)을 기본으로 한다. 광고 페이로드에는 익명 팀 ID와 회전 토큰만 실리고, 위치 raw는 저장하지 않는다.
- 설정에 "원격 하이파이브" 토글을 신설(기본 off, 명시적 opt-in). BLE/블루투스 권한은 토글 활성 시에만 요청한다.
- 하이파이브 발생 시 커뮤니티 탭 피드(파일럿 팀 한정)에 "근처에서 N명과 하이파이브" 배지가 남는다. 개인 식별 정보는 남기지 않는다.
- 파일럿 범위: **LG / 두산**만 활성화(커뮤니티 탭과 동일한 플래그). 나머지 8개 구단은 토글 숨김.
- **Non-Goals**: 구장 지오펜스("직관 모드")는 본 change에서 제외 — 별도 `add-stadium-mode`로 분리. GPS·서버 위치 공유 방식도 본 change 범위 아님.

## Capabilities

### New Capabilities
- `remote-high-five`: 내 팀 득점·홈런 시점에 근처 같은 팀 팬을 BLE로 감지하고, 양쪽 워치에 동시 햅틱을 트리거하는 기능. 광고 페이로드 스펙, opt-in 토글, 파일럿 팀 게이트, 프라이버시 원칙(익명/회전 토큰/raw 위치 미저장)을 포함.

### Modified Capabilities
- `mobile-ios`: 원격 하이파이브 설정 토글 및 BLE 권한 플로우 추가.
- `mobile-android`: 원격 하이파이브 설정 토글 및 BLE/위치 권한 플로우 추가(Android 12+ `BLUETOOTH_SCAN`/`BLUETOOTH_ADVERTISE` `neverForLocation` 원칙).
- `watch-ios` / `watch-android`: 폰에서 전달된 하이파이브 트리거에 대한 전용 햅틱 패턴 추가(기존 득점 햅틱과 구분).
- `realtime`: 폰 앱이 득점·홈런 이벤트 수신 시 BLE 스캔·광고 윈도우를 짧게 여는 훅(이벤트 훅 지점만 추가, 프로토콜 변경 없음).

## Impact

- **코드**: iOS/Android 모바일에 BLE 중앙/주변(central/peripheral) 매니저 신규 모듈, Settings 화면 토글, 파일럿 팀 게이트. 워치 앱에 신규 햅틱 패턴 등록. 커뮤니티 탭 피드 아이템 타입 1종 추가.
- **권한**: iOS `NSBluetoothAlwaysUsageDescription`, Android 12+ `BLUETOOTH_SCAN`/`BLUETOOTH_ADVERTISE`(`neverForLocation`). 토글 off 상태에서는 권한 요청 금지.
- **백엔드**: 본 change에서는 서버 변경 없음(핵심은 P2P BLE). 단, 커뮤니티 탭 피드 배지 기록을 위해 기존 커뮤니티 API에 아이템 타입 1종 추가 여부는 design에서 결정.
- **프라이버시·법무**: opt-in 토글, 회전 토큰, raw 위치 미저장 원칙을 개인정보 처리방침에 반영 필요.
- **배포 게이트**: 파일럿 팀(LG/두산) 플래그 아래에서만 노출. 성공 지표 확인 후 전 구단 확대.
- **의존**: `add-community-tab`의 피드 구조가 안정화된 뒤 배포하는 것이 자연스럽다(같은 파일럿 범위).
