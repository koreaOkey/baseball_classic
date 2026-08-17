# Tasks

## 1. Test-tool wiring

- [x] 1.1 `selectedPreviewStyle` 상태 추가(기본 `Style.PROMOTED`)
- [x] 1.2 "Promoted/Ongoing 버전" 일회성 버튼 → 선택 토글로 전환(선택 표시 + 즉시 미리보기)
- [x] 1.3 "Live Score 시작"·"득점 강조"가 `forceStyle = selectedPreviewStyle` 전달
- [x] 1.4 자동 시뮬레이션 `postLiveScorePreviewState`에 `forceStyle = selectedPreviewStyle` 전달
- [x] 1.5 자동 시뮬레이션 시작 시 미리보기 미활성이면 자동 활성화("Live Score 시작" 선행 불필요) — 선택 스타일 카드가 시뮬 내내 갱신

## 2. Verification

- [x] 2.1 `:app:compileDebugKotlin` 빌드 통과
- [ ] 2.2 실기기: Promoted 선택 후 "Live Score 시작"/"자동 시뮬레이션" → promoted 스타일로 게시 확인
- [ ] 2.3 실기기: Ongoing 선택 후 동일 플로우 → 이전 리치 카드로 게시 확인
- [ ] 2.4 삼성(One UI 8.0) 실기기: Promoted 선택 시 비승격 BigText 템플릿 표시 확인(Now Bar는 OS 미개방)
