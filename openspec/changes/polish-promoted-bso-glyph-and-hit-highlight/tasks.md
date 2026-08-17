# Tasks

## 1. BSO glyph

- [x] 1.1 `bsoEmojiLine()` 빈 슬롯 `○`(U+25CB) → `⚪`(U+26AA)로 교체 + 주석 갱신

## 2. Highlight hits

- [x] 2.1 `shouldHighlightLiveScorePreview()`에 `EventType.HIT` 추가
- [x] 2.2 `post(bypassEventFilter)` 추가 — 테스트 미리보기는 사용자 이벤트 필터를 무시하고 강조 강제. 필터에서 "안타"를 끈 기기에서도 시뮬레이션 HIT가 heads-up+진동으로 강조되도록(이중 게이트 제거)

## 3. Verification

- [x] 3.1 `:app:compileDebugKotlin` 빌드 통과
- [ ] 3.2 실기기: promoted 카드 BSO 빈 슬롯이 ⚪로, 채운 슬롯과 정렬 일치 확인
- [ ] 3.3 실기기: 자동 시뮬레이션 안타 이벤트에서 heads-up + 진동 강조 확인
