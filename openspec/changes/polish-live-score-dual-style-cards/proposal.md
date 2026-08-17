# Polish Dual Live Score Card Styles + Explicit User Choice

## Why

2026-08-17 실기기 검증(SM-F976N, One UI 9.0) 결론:

- promoted(BigText)는 삼성에선 일반 알림, 픽셀에선 잠금화면 고정으로 렌더링되며 **실사용 가능**.
- classic 커스텀 카드는 팀 로고·하이라이트 행이 강점이나 잘림 버그(팀명 "...", 이벤트 텍스트)와 낮은 정보 밀도(접힘=로고+스코어뿐) 존재.
- 사용자 결정: 두 스타일을 모두 유지하고 **설정에서 명시적으로 선택**하게 하며, 각 스타일 UI를 다듬는다. (직전 검토 중 잠시 넣었던 "삼성 classic 강제"(force-classic-live-score-on-samsung)는 이 결정으로 폐기·되돌림.)

## What Changes

### A. 시스템 카드(Promoted) 다듬기 — `applyPromotedStyle`
- 평상시: 본문 = BSO 줄(접힘에서도 노출), 펼침 = BSO + "타자 X | 투수 Y n구"(투구수 추가). 이벤트 문구를 평상시 본문에 섞지 않음(소소한 이벤트 노출 문제 해소).
- 하이라이트(득점·홈런·안타): 본문을 이벤트 문구 단독으로 교체 + 이벤트별 이모지(💥홈런 🔥득점 ⚾안타 ⚡기타). 3초 후 원복은 기존 호출부 로직.
- `eventEmoji()` 헬퍼 추가. subText=이닝, largeIcon 다이아몬드, 상태바 칩 유지.

### B. 커스텀 카드(Ongoing) 다듬기
- 접힘 재설계: `[로고 구단명 3 : 4 구단명 로고] … [9회초 · ●●○ ●○ ●○]` — 이닝+BSO 미니 점(실제 카운트 색, `compactInfoText()` 스팬) 추가. 팀명은 표기 설정과 무관하게 짧은 구단명 강제.
- 펼침: 팀명 잘림 수정(24→16sp), 최근 이벤트 텍스트 2줄 허용, BSO 점 확대(7→9dp, 열 폭 56dp), 투수 투구수 표시("P 임찬규 87구 | B 김현수").
- 하이라이트(노란 박스 행)는 기존 유지.

### C. 설정 — 2택 선택 UI
- 기존 스위치 → 라디오형 2행 선택(`LiveScoreStyleOption`): "시스템 카드 (Promoted)" / "커스텀 카드 (Ongoing)". 같은 pref(`live_score_promoted_style_enabled`) 사용.
- 노출 조건은 API36+ 전 기기(삼성 포함 재노출). 실시간 업데이트 안내 배너는 promoted 선택 시에만 + 삼성 게이트 유지.

### D. 테스트 도구 연동
- 미리보기 스타일 기본값이 설정 선택을 상속. 버튼·로그 명칭을 "시스템 카드/커스텀 카드"로 통일.
- debug 인텐트에 `force_style=promoted|classic` extra 추가(검증용).

## Capabilities

### lock-screen-live-score

- 사용자는 설정에서 시스템 카드/커스텀 카드 중 하나를 명시적으로 선택할 수 있고, 선택이 라이브 스코어 게시 스타일을 지배한다(기본: 시스템 카드).
- 커스텀 카드 접힘 상태에서 로고·스코어·이닝·BSO를 한 줄로 확인할 수 있다.
- 두 스타일 모두 득점·홈런·안타 시 이벤트 문구가 강조 표시된다(시스템=단독 문구+이모지, 커스텀=노란 하이라이트 행).

## Impact

- `apps/mobile/.../push/LiveScoreNotificationManager.kt` — applyPromotedStyle 재구성, compactInfoText, 투구수, eventEmoji, canPromote 삼성 조건 제거.
- `apps/mobile/.../ui/screens/SettingsScreen.kt` — 2택 선택 UI + LiveScoreStyleOption.
- `apps/mobile/.../ui/screens/WatchTestScreen.kt` — 기본값 상속 + 명칭.
- `apps/mobile/.../MainActivity.kt` — debug force_style extra.
- `res/layout/notification_live_score_compact.xml`(재작성) · `expanded.xml`(잘림·크기) · `values/themes.xml`(점 크기).
- 백엔드·iOS·Watch 영향 없음.

## Non-Goals

- ProgressStyle 정식 채택(보류, DEBUG 프로토타입 잔존).
- 삼성 Now bar 등록(파트너/BD 트랙).
