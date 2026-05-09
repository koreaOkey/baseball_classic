## Why

iOS 설정 → "버전" 탭 시 노출되는 업데이트 안내 모달이 `.sheet(item:)` 풀스크린 시트로 떠서, 짧은 bullet(2개) 콘텐츠와 화면 하단 "확인" 버튼 사이에 큰 공백이 생긴다. 콘텐츠 분량은 적은데 시트가 화면을 가득 채우기 때문에 시각적으로 "비어 보이고", 아래에서 위로 슬라이드 업 되는 시트 애니메이션이 짧은 안내 모달 성격과 맞지 않는다.

## What Changes

- iOS: `WhatsNewSheet.swift` 를 중앙 정렬 팝업 카드 스타일로 재구성한다. 카드는 콘텐츠 높이에 맞게 사이징되고, "확인" 버튼이 콘텐츠 바로 아래에 붙는다. 카드 외부는 반투명 검정 백드롭(0.6)으로 덮인다.
- iOS: `BaseHapticApp.swift`(자동 노출), `SettingsScreen.swift`(설정 → 버전 수동 노출) 양쪽 모두 `.sheet(item:)` → `.overlay { if let ... }` + `.transition(.opacity)` 로 교체한다. 슬라이드 업 대신 페이드 인으로 한 번에 등장.
- 백드롭 탭 또는 "확인" 버튼으로 닫힘. 콘텐츠 / 버튼 / 헤더 구조 자체는 변경 없음.
- Android는 변경 없음 (이미 Compose `Dialog` 라 중앙 팝업 동작).

## Capabilities

### Modified Capabilities

- `mobile-ios:whats-new-popup`: 시트 스타일 → 중앙 팝업 스타일. 자동/수동 두 진입점 모두 동일 적용. 트리거 조건·1회 노출 규칙·신규 설치 차단 로직은 유지.

### New Capabilities

- 없음.

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| overlay 가 안전 영역을 무시하지 못해 카드가 노치/홈 인디케이터에 가림 | `WhatsNewSheet` 내부 백드롭이 `.ignoresSafeArea()` 사용. 카드는 `.padding(.horizontal, AppSpacing.xxl)` 로 양옆 여백 확보 |
| `.transition(.opacity)` 가 부모 `animation` 모디파이어 없이는 즉시 swap 됨 | 부모에 `.animation(.easeInOut(duration: 0.2), value: pendingReleaseNote?.id)` 명시 |
| 백드롭 탭으로만 닫히고 명시적 dismiss 가 없는 사용자 혼란 | "확인" 버튼은 그대로 유지. 백드롭 탭은 보조 동작 |

## Non-Goals

- Android 다이얼로그 스타일 변경.
- 카드 내부 콘텐츠/카피 변경.
- 트리거 조건이나 `last_seen_update_version` 저장 로직 변경.
- LiveActivity / Dynamic Island.

## Status

- [x] `WhatsNewSheet` 중앙 팝업 카드로 재구성
- [x] `BaseHapticApp.swift` overlay 전환
- [x] `SettingsScreen.swift` overlay 전환
- [ ] 실기기/시뮬레이터 시각 확인
- [ ] archive
