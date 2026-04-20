## Why

사용자가 구버전 앱을 계속 사용하면 새 기능이나 버그 수정을 받지 못한다. 인앱 업데이트 팝업을 통해 새 버전 출시 시 자동으로 업데이트를 유도하여 최신 버전 사용률을 높인다.

## What Changes

- Android: Google Play In-App Updates API (FLEXIBLE 모드) 적용. 앱 실행 시 Play Store에 새 버전이 있으면 백그라운드 다운로드 후 자동 설치 유도.
- iOS: App Store iTunes Lookup API로 최신 버전 조회, 현재 버전보다 높으면 업데이트 Alert 표시. "업데이트" 버튼으로 App Store 이동, "나중에"로 닫기 가능.
- iOS 버전을 1.1.1 (빌드 3)으로 올림 (마케팅 URL 추가 배포용)

## Capabilities

### New Capabilities
- `android-in-app-update`: Play Store 새 버전 감지 시 FLEXIBLE 업데이트 플로우 실행
- `ios-app-store-version-check`: iTunes API로 스토어 버전 비교 후 업데이트 Alert 표시

### Modified Capabilities
- None

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Play Store 내부 테스트에선 업데이트 감지 안 됨 | 프로덕션 트랙 배포 후 정상 동작 확인 |
| iTunes API 응답 실패 시 | 에러 무시, 팝업 미노출 (앱 정상 사용 가능) |

## Status

- [x] 구현 완료
- [x] 빌드 확인 (Android + iOS)
- [x] 커밋 및 푸시 완료 (e05df2d)
