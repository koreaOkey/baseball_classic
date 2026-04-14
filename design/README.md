# BaseHaptic 디자인 토큰 (Figma 연동)

## 이 폴더의 역할

[openspec/specs/design-system/spec.md](../openspec/specs/design-system/spec.md)에 정의된 디자인 토큰을 **Figma로 옮기기 위한 포맷**으로 변환한 파일들이 있다. 코드(`AppFont`/`AppSpacing`/`AppShapes`/`AppEventColors`)와 Figma Variables가 같은 값을 공유하게 만드는 연결 고리.

## 파일

- **`tokens.json`** — [Tokens Studio](https://tokens.studio) Figma 플러그인이 직접 import할 수 있는 W3C Design Tokens 호환 JSON. 12개 토큰 세트(global + 11개 팀) 포함.

## 동기화 원칙 (레벨 0)

현재 동기화는 **문서 기반**이다. 값이 바뀌면 사람이 수동으로 아래 파일들을 모두 고쳐야 한다:

1. `openspec/specs/design-system/spec.md` (설계 기준)
2. `design/tokens.json` (Figma 연동용)
3. iOS: `ios/mobile/BaseHaptic/Theme/*.swift` (AppColors / AppFont / AppSpacing / AppRadius / AppEventColors)
4. iOS Watch: `ios/watch/BaseHapticWatch/Theme/*.swift`
5. Android Mobile: `apps/mobile/.../ui/theme/*.kt` (Color / AppFont / Spacing / Shapes / EventColors)
6. Android Watch: `apps/watch/.../ui/theme/*.kt`
7. Figma Variables (플러그인으로 tokens.json 재import)

자동화 필요성이 생기면 [Style Dictionary](https://amzn.github.io/style-dictionary/) (레벨 1) 도입을 검토한다.

---

## Figma로 처음 옮기기 (3가지 경로)

### 경로 A: Tokens Studio 플러그인 (권장 ⭐)

**왜 권장?** JSON import → Figma Variables 변환이 자동. 팀 테마 전환(11개)도 플러그인 UI에서 원클릭.

#### 준비
1. Figma에서 **새 파일 생성** (또는 기존 디자인 파일 열기)
2. Figma 메뉴 → **Plugins** → **Browse plugins in Community**
3. "**Tokens Studio for Figma**" 검색 후 설치 (무료)

#### 토큰 import
1. 열린 파일에서 **Plugins** → **Tokens Studio for Figma** 실행
2. 플러그인 창 우측 상단의 **톱니바퀴(⚙️)** → **Tools** → **Load from file / folder or preset**
3. 이 프로젝트의 `design/tokens.json` 선택
4. 좌측 패널에 `global`, `teams/doosan`, `teams/lg`... 12개 Token Set이 나타남
5. 우측 상단 **Themes** 탭 → "Default (no team)" 포함 11개 테마가 자동 생성됨
6. 원하는 테마 선택 → **Apply to selection** (전체 스타일 적용) 또는 개별 프레임에 적용

#### Figma Variables로 변환
Tokens Studio는 기본적으로 **Figma Styles**를 만들지만, 최신 버전은 **Figma Variables**도 지원한다:

1. 플러그인 설정 → **Settings** → **Base Font Size** 16 확인
2. **Tools** → **Export → Variables** (또는 **Sync to Figma Variables**)
3. Figma 우측 패널의 **Variables** 섹션에 `gray/950`, `spacing/lg`, `team/primary` 등이 나타남

> **참고**: Tokens Studio의 UI는 버전마다 조금씩 다름. 위치를 못 찾겠으면 플러그인 첫 화면의 **"Get Started"** → **"Import"** 버튼 경로도 시도해보자.

---

### 경로 B: Figma Variables 수동 생성

플러그인을 쓰고 싶지 않거나 소규모만 옮기려면 Figma의 기본 Variables UI로 직접 입력:

1. Figma 파일 열기 → 우측 패널 **Variables** 섹션 → **+** 버튼
2. **Create collection** → 이름: `BaseHaptic / Global`
3. **Mode**를 팀 테마 개수만큼 추가 (Default, Doosan, LG, Kiwoom, …)
4. 변수 추가:
   - Color 변수: `gray/950` → `#0A0A0B` (모든 mode 동일)
   - Number 변수: `spacing/lg` → `16` (모든 mode 동일)
   - Color 변수: `team/primary` → mode별로 다른 값 (Doosan=`#131230`, LG=`#C30452` …)
5. 각 값은 [tokens.json](tokens.json) 또는 [spec.md](../openspec/specs/design-system/spec.md)에서 확인

소요 시간: 1~2시간 (꼼꼼히). 플러그인 경로는 5~10분.

---

### 경로 C: Figma REST API (자동화)

Node.js 스크립트로 `tokens.json`을 읽어 Figma REST API로 직접 Variables 생성. 개발 환경 필요:
- Figma Personal Access Token 발급
- `POST /v1/files/:key/variables` 호출

이 경로는 **CI에서 자동 동기화**가 필요할 때 쓴다. 지금 단계(레벨 0)에선 과함.

---

## 옮긴 후 확인 사항

Figma Variables가 제대로 들어갔다면 아래가 가능해야 한다:

### ✅ Color 테스트
- 빈 Rectangle 만들고 Fill → Variables에서 `gray/950` 선택 → 배경색이 `#0A0A0B`로 바뀜
- 같은 Rectangle에 `team/primary` 적용 → Mode를 "Doosan"으로 바꾸면 `#131230`, "LG"로 바꾸면 `#C30452`

### ✅ Spacing 테스트
- 두 개 Rectangle 사이 Auto Layout → gap 값에 `spacing/lg` 지정 → 16px gap

### ✅ Typography 테스트
- Text 노드 → Font Style에 `h2` 적용 → 28pt bold

### ✅ Radius 테스트
- Rectangle → Corner Radius에 `borderRadius/md` 적용 → 12px

이 4가지가 작동하면 **Figma → Claude Code (MCP) → SwiftUI/Compose 자동 구현** 루프가 열린다.

---

## Figma MCP 연동 (다음 단계)

Figma Variables 세팅이 끝나면 [2026-04-13-design-tokens change 메모](../openspec/changes/archive/2026-04-13-design-tokens/proposal.md)에서 예고한 워크플로가 가능해진다:

```
1. Figma에서 새 화면 디자인
   (Variables로 gray/900 + spacing/lg + h5Bold 조합)
        ↓
2. Claude Code에서 "이 프레임을 SwiftUI로 구현해줘"
        ↓
3. 공식 Figma MCP가 get_design_context 호출
   → Variables 이름을 그대로 읽음 ("gray/900")
        ↓
4. Claude가 코드 생성:
   Color: gray/900 → AppColors.gray900 ✅
   Spacing: spacing/lg → AppSpacing.lg ✅
   Font: h5Bold → AppFont.h5Bold ✅
   (이름이 1:1 매칭되므로 자동)
        ↓
5. 생성된 SwiftUI 코드가 기존 앱과 스타일 일치
```

Android도 동일 원리 (`eventColor/safe` → `AppEventColors.eventColor("HIT")`).

---

## 값 수정 체크리스트

토큰 값을 하나라도 바꿀 때는 **반드시 아래 순서로** 진행:

1. [ ] `openspec/specs/design-system/spec.md` 수정 (설계 기준 먼저)
2. [ ] `design/tokens.json` 수정
3. [ ] iOS `AppColors`/`AppFont`/... 수정
4. [ ] iOS Watch 대응 파일 수정
5. [ ] Android Mobile 대응 파일 수정
6. [ ] Android Watch 대응 파일 수정
7. [ ] Figma에서 Tokens Studio로 `tokens.json` 재import (또는 수동 업데이트)
8. [ ] PR 설명에 "디자인 토큰 값 변경"이라고 명시

7개 파일을 빼먹으면 플랫폼 간 drift가 발생. Style Dictionary 도입을 진지하게 고려해야 할 신호.

---

## 현재 토큰 규모 (2026-04-13 기준)

| 카테고리 | 개수 |
|---|---|
| 중립 컬러 (Gray) | 10 |
| 시맨틱 컬러 | 14 (Blue/Cyan/Green/Yellow/Orange/Red) |
| 팀 테마 컬러 | 77 (11 팀 × 7 슬롯) |
| 스페이싱 | 10 |
| Border Radius | 5 |
| Font Size | 14 |
| Font Weight | 5 |
| Typography composite | 18 |
| Event Color | 5 (시맨틱 그룹) |
| **총계** | **~158 토큰** |

## 참고
- [W3C Design Tokens Community Group](https://www.designtokens.org/) — 향후 표준
- [Tokens Studio 공식 문서](https://docs.tokens.studio/)
- [Figma Variables 공식 가이드](https://help.figma.com/hc/en-us/articles/15339657135383-Guide-to-variables-in-Figma)
