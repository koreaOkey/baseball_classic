# Tasks

## 1. 시스템 카드(Promoted) 다듬기

- [x] 1.1 평상시 본문 BSO 고정 + 펼침 타자/투수(+투구수) 줄
- [x] 1.2 하이라이트 시 이벤트 문구 단독 교체 + `eventEmoji()`(💥🔥⚾⚡)
- [x] 1.3 `post()` → `applyPromotedStyle`에 eventType/highlight 전달

## 2. 커스텀 카드(Ongoing) 다듬기

- [x] 2.1 접힘 레이아웃 재작성(구단명 강제 + `notification_compact_info` 이닝·BSO 스팬)
- [x] 2.2 `compactInfoText()` — 실제 카운트 색 점(색상 상수 = live_score_* 값)
- [x] 2.3 펼침 팀명 24→16sp(잘림 수정), 최근 이벤트 maxLines 2
- [x] 2.4 BSO 점 7→9dp + 열 폭 56dp
- [x] 2.5 투구수 표시("P 임찬규 87구 | B 김현수")

## 3. 설정 2택 선택

- [x] 3.1 스위치 → `LiveScoreStyleOption` 라디오형 2행(시스템/커스텀)
- [x] 3.2 노출 조건 API36+ 전 기기(삼성 재노출), 배너는 promoted 선택+비삼성만
- [x] 3.3 canPromote 삼성 강제 제거(폐기된 force-classic-live-score-on-samsung 되돌림)

## 4. 테스트 도구·디버그

- [x] 4.1 미리보기 스타일 기본값 = 설정 선택 상속
- [x] 4.2 버튼·로그 명칭 "시스템 카드/커스텀 카드" 통일
- [x] 4.3 debug 인텐트 `force_style=promoted|classic` extra

## 5. Verification

- [x] 5.1 `:app:assembleDebug` 빌드 통과
- [ ] 5.2 실기기: 커스텀 카드 접힘 — 로고+구단명+스코어+이닝+BSO 한 줄 확인
- [ ] 5.3 실기기: 커스텀 카드 펼침 — 팀명·이벤트 잘림 해소, 점 확대, 투구수 확인
- [ ] 5.4 실기기: 시스템 카드 평상시 BSO 본문 / 하이라이트 단독 문구+이모지 확인
- [ ] 5.5 실기기: 설정 2택 선택 → 다음 게시부터 스타일 반영 + 테스트 도구 기본값 상속 확인
