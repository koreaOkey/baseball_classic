# 야구봄 “분풀이 모드” UI DESIGN SPEC

기준 기기: iPhone 390 x 844pt. 언어: 한국어. 목적: 경기 후 아쉬움을 실제 선수/감독 비난이 아닌 앱 내부의 귀여운 장난감 상호작용으로 전환한다.

## 1. 디자인 토큰

### Color
- Background / dark navy: #10151d
- Panel: #171e29
- Elevated panel: #1a2330 → #151c27 vertical gradient
- Text primary: #e8e4da
- Text secondary: #9ca6b3
- Border / divider: #2a3443
- Accent orange: #ff5a36
- Accent orange highlight: #ff724e
- Gold: #d9a441
- Cream: #fff2d7
- Warning red/team red example: #f05266
- Gauge red: #d92f25

### Typography (SwiftUI pt)
- App logo: 23pt, weight .black, tracking -0.8
- Section eyebrow/badge: 12pt, weight .heavy, letterSpacing +0.8
- Home title: 30pt, lineHeight 34pt, weight .black, tracking -1.1
- Screen title large: 31pt, lineHeight 36pt, weight .black, tracking -1.1
- Result title: 43pt, lineHeight 43pt, weight .black, tracking -1.5
- Body: 14pt, lineHeight 20pt, weight .regular
- Caption/legal: 11pt, lineHeight 16pt, weight .regular
- Score number: 46pt, lineHeight 50pt, weight .black
- Score team: 18pt, weight .black
- Rank number: 24pt, weight .black
- Rank name: 18pt, weight .black
- Button label: 16–17pt, weight .black

### Spacing / Layout
- Screen frame: 390 x 844pt
- Safe area visual top padding: 22pt for header, content starts 58–72pt
- Horizontal page padding: 22pt
- Card padding: 20–22pt
- Vertical component gaps: 8pt small, 10–14pt list, 18–22pt section, 24–26pt major
- Home card top margin: 24pt, height 684pt
- Player list gap: 10pt
- Gauge label gap: 8pt

### Radius
- Header icon: 14pt
- Small badge: 999pt capsule
- Main card: 30pt
- Stage card: 32pt
- Rank row: 21pt
- Primary button: 20–21pt
- Secondary button: 19pt
- Gauge container: 22pt
- Gauge bar: 999pt capsule

### Shadow / Depth
- Main card: y 18pt, blur 60pt, black 36%
- Primary CTA: y 14pt, blur 28pt, #ff5a36 27%
- Reward CTA: y 18pt, blur 34pt, #d9a441 25%
- Sprite: drop shadow y 20–24pt, blur 24–26pt, black 35–50%

## 2. 화면별 컴포넌트

### ① 홈카드 — screen1_homecard.png
- Header: 좌측 `야구봄` 로고, 우측 36x36pt 아이콘 버튼.
- Background: #10151d 기반, 낮은 opacity 점/야구공 패턴.
- MainCard: 346pt width, 684pt height, radius 30pt, gradient #1a2330 → #151c27, border #2a3443 1pt.
- Eyebrow: `TONIGHT RECAP`, 12pt, #d9a441.
- Title: `오늘의 아쉬운 순간`, 30pt, primary/gold 혼합.
- Description: 14pt secondary, 2줄. 선수 비난 대신 앱 내부 해소 톤.
- Mascot image: 244 x 244pt, `assets/penguin_doll_normal.png`.
- Baseball ornaments: 32pt circles, decorative only.
- Score row: team 18pt, score 46pt. 예시 `LG 3 : 7 두산`; 응원팀 컬러는 동적으로 교체 가능.
- Primary CTA: 100% width, 58pt height, radius 20pt, #ff724e → #ff5a36, label `분풀이 하러 가기`.
- Safety caption: 11pt, #858f9d.

### ② 선수 선택 — screen2_playerselect.png
- Header 동일.
- Badge: `오늘 경기 기록 기반`, capsule, 12pt.
- Title block: `오늘의 아쉬운 순간` 29pt + `TOP5` 44pt gold.
- RankList: 6 rows, each 70pt height, radius 21pt, border #313b4a 1pt.
- Row grid: rank 46pt column / player text flexible / moment auto / check 30pt.
- TOP5: 1~5위 선수형 항목. 예시 이름은 placeholder이며 실제 서비스에서는 서버 기록 기반 익명/실명 정책에 맞춰 주입.
- 6번째 감독 항목: `감독`, `투수 교체 타이밍`, sublabel `운영 판단`.
- Check icon: 24pt circle, 2pt #d9a441 stroke, check 13pt.
- Legal card: radius 18pt, 10x12pt padding, caption 11pt. 문구: `기록 기반 자동 선정이며 공식 평가가 아닙니다...`

### ③ 분풀이 룸 — screen3_ventroom.png
- Header 동일.
- Badge: `분풀이 룸`.
- Title: `연타해서 분을 푸세요!`, 31pt.
- Safety subcopy: 14pt secondary.
- Stage card: 346pt width, 486pt height, radius 32pt, gradient, border #303b4a.
- Radial burst lines: orange 18% opacity, decorative feedback layer.
- Mascot: `assets/penguin_doll_crack.png`, 286 x 286pt, centered y 82pt.
- Damage pill: top-right, `DAMAGE 68%`, 12pt, orange-on-dark.
- Floating feedback chips: `+12 톡!`, `콤보 x8`, 12pt heavy.
- Tap zone: bottom center 148 x 48pt, radius 18pt, orange gradient, label `화면 연타`.
- Gauge container: 346pt width, radius 22pt, padding 14x16pt.
- Gauge bar: 18pt height, capsule, fill 68%, gradient #ff8c35 → #ff5a36 → #d92f25.
- Tick labels: `균열` / `터짐` / `완파`, 12pt, #d1b16c.

### ④ 완파 화면 — screen4_destroyed.png
- Header 동일.
- Confetti/firework decorative layer: small orange/gold particles.
- Badge: `RESULT`.
- Result title: `분풀이 완료!`, 43pt, gold + primary, heavy shadow.
- Subcopy: `내일은 다시 응원할 준비 완료`, 15pt.
- Destroyed mascot area: 332pt height. `assets/penguin_doll_destroyed.png`, 300 x 300pt.
- Stars: 28pt gold decorative, should not be read by VoiceOver.
- Reward CTA: 100% width, 62pt height, radius 21pt, #f1c55f → #d9a441, label `광고 보고 황금 배트로 한 판 더`, includes 45x45pt `golden_bat.png`.
- Secondary CTA: 100% width, 52pt height, radius 19pt, dark fill #141b25, gold border/text, label `홈으로`.
- Safety caption: 11pt.

## 3. 개발용 이미지 자산
- `assets/penguin_doll_normal.png`: 정상 상태 펭귄 인형.
- `assets/penguin_doll_crack.png`: 금/균열 상태.
- `assets/penguin_doll_burst.png`: 솜이 터져 나오는 상태.
- `assets/penguin_doll_destroyed.png`: 납작해진 완파 상태 + 별.
- `assets/golden_bat.png`: 황금 배트 단독 보상 아이템.
- 생성 요청은 투명 배경 PNG였으며, 실제 파일의 alpha 유무는 DONE.txt에 기록한다. 투명 배경이 아닌 경우 크로마키 대체 배경은 사용하지 않았다.

## 4. 접근성 / 안전 UX
- `분풀이`는 실제 선수·감독을 향한 공격이 아니라 기록 기반 장난감 인터랙션임을 각 화면에 반복 노출한다.
- 모든 핵심 텍스트는 #10151d/#171e29 위 #e8e4da 또는 #d9a441 계열로 고대비를 유지한다.
- 보상형 광고 CTA는 선택형임을 결과 화면 caption으로 보완한다.
- 장식 별, 야구공, 파티클은 accessibilityHidden(true) 처리 권장.
- SwiftUI Dynamic Type 적용 시 Rank row는 70pt에서 최소 78pt까지 늘어날 수 있게 한다.

## 5. SwiftUI 구현 힌트
- 공통 `VentingBackground`, `YagubomHeader`, `VentingCard`, `PrimaryOrangeButton`, `RewardGoldButton`, `DamageGauge`, `RankRow` 컴포넌트로 분리.
- 색상은 `Color(hex:)` extension 또는 Asset Catalog semantic color로 등록.
- Sprite는 `Image("penguin_doll_normal")` 등으로 상태 enum에 매핑: `.normal`, `.crack`, `.burst`, `.destroyed`.
- `DamageGauge(progress:)`는 0.0...1.0 값을 받아 fill width를 계산하고 0.33/0.66/1.0 상태 전환 threshold로 `균열/터짐/완파`를 표시.
