## Why

진행 중인 경기 카드를 눌렀을 때 보이는 상세 화면이 실시간 야구 상황을 한눈에 파악하기에 부족하다. 사용자가 제공한 야구장형 레퍼런스를 BaseHaptic의 다크 디자인 시스템에 맞춰 Android와 iOS에서 동일한 정보 구조로 제공한다.

## What Changes

- 모바일 경기 상세 화면을 점수판, 필드 프리뷰, 이닝 탭, 현재 승부, 워치 동기화 상태, 이벤트 카드 목록 구조로 재구성한다.
- 선수 사진 없이 현재 투수/타자, 점수, 이닝, B/S/O, 루상 점유, 최근 이벤트를 강조한다.
- 이벤트 카드에서 가능한 경우 투수와 타자 이름을 함께 표시한다.
- 기존 실시간 수신, 재연결, 복구 pull 흐름은 유지한다.
- 백엔드에 아직 없는 이닝별 이벤트 메타데이터, 라인업/수비 위치, 주자 이름, 선수 상세 스탯은 후속 백엔드 보강 범위로 남긴다.

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `mobile-android`: Android 경기 상세 화면이 실시간 경기 상태를 야구장형 상세 UI로 표시해야 한다.
- `mobile-ios`: iOS 경기 상세 화면이 Android와 동일한 정보 구조의 야구장형 상세 UI로 표시해야 한다.

## Impact

- Android Mobile: 경기 상세 Compose 화면과 이벤트 파싱 모델.
- iOS Mobile: 경기 상세 SwiftUI 화면과 이벤트 파싱 모델.
- Backend API: 이번 변경에서 직접 수정하지 않지만, 레퍼런스 수준의 완전한 데이터 표현을 위해 후속 API 확장이 필요하다.
- Watch Android/iOS: 상세 화면 UI 변경 대상은 아니며, 기존 워치 동기화 상태 표시는 유지한다.
- DB Migration: 필요 없음.
