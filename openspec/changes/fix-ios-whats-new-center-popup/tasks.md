# Tasks

## iOS
- [x] `ios/mobile/BaseHaptic/Components/WhatsNewSheet.swift` — ZStack 백드롭 + 중앙 카드 구조로 재구성. 카드는 콘텐츠 높이로 사이징, `cornerRadius(AppRadius.lg)` 로 둥근 카드. 백드롭 탭 시 `onConfirm` 호출.
- [x] `ios/mobile/BaseHaptic/BaseHapticApp.swift` — `.sheet(item: $pendingReleaseNote)` → `.overlay { if let note = pendingReleaseNote { ... } }` + `.transition(.opacity)` + `.animation(.easeInOut(duration: 0.2), value: pendingReleaseNote?.id)`.
- [x] `ios/mobile/BaseHaptic/Screens/SettingsScreen.swift` — 동일 패턴으로 `manuallyOpenedReleaseNote` overlay 적용.

## 검증
- [ ] 시뮬레이터에서 설정 → 버전 탭 → 페이드인 + 콘텐츠 바로 아래 "확인" 노출 확인.
- [ ] 콜드 스타트 자동 노출 경로(`evaluateWhatsNewTrigger`)도 동일하게 페이드인으로 노출 확인.
- [ ] 백드롭 탭/`확인` 버튼 둘 다 dismiss 확인.

## 후속
- [ ] 시각 확인 후 archive 로 이동 (`openspec/changes/archive/`).
