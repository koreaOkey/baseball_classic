# 다이어그램 도구 규칙을 하네스에 고정

## Why

2026-11-04에 시스템 구성도용 오픈소스·MCP·스킬을 조사했고, 그 결과로 야구봄 구성도를 stack-diagram으로 그렸다(`~/Downloads/yagubom-diagram/`). 이어서 새 아키텍처를 설계할 때는 무엇을 쓸지 정했다.

- 설계 단계에서는 안을 여러 번 바꾼다. stack-diagram은 좌표를 에이전트가 직접 잡아서 수정이 느리고, 그릴 수 있는 종류도 구성도 하나뿐이다. 텍스트로 쓰면 배치가 자동인 Mermaid가 맞다.
- 이 결정은 처음에 Claude 메모리에만 있었다. Codex는 메모리를 보지 못하고, AGENTS.md도 "규칙이 바뀌면 메모리가 아니라 이 섹션을 고친다"고 정하고 있다.
- 기존 "모든 이미지 생성은 hermes-designer 위임" 규칙과 겹칠 수 있었다. 코드로 렌더하는 다이어그램까지 디자이너에게 넘기지 않도록 범위를 분명히 해야 했다.

## What Changes

- `openspec/config.yaml`의 `rules.design`에 규칙을 추가했다. 컴포넌트·서비스 간 연결이나 데이터 흐름이 바뀌는 change는 design.md에 Mermaid 다이어그램을 넣는다(구성은 flowchart, 요청 순서는 sequenceDiagram). 작성 후 mmdc로 렌더해 확인한다. OpenSpec CLI가 design 작성 지침에 이 규칙을 넣어주므로 Claude와 Codex 모두 propose 단계에서 자동으로 따른다.
- `AGENTS.md` "제품·운영 규칙"에 "다이어그램 도구" 항목을 추가했다.
  - 설계와 대안 비교는 Mermaid, 확정된 구성도 공유는 stack-diagram을 쓴다.
  - 코드로 렌더하는 다이어그램은 이미지 제작 규칙(디자이너 위임) 대상이 아니다.
  - stack-diagram 공개 갤러리 공유는 사용자가 원할 때만 한다.
- 로컬 환경: Mermaid CLI(`@mermaid-js/mermaid-cli` 12.2.0)를 npm 전역으로 설치했다. 렌더용 Chrome은 이미 있던 `~/.cache/puppeteer` 캐시를 쓴다.

## Capabilities

### design 문서 작성

- 구조가 바뀌는 change의 design.md에는 렌더 확인을 거친 Mermaid 다이어그램이 들어간다.
- 다이어그램 도구 선택은 Claude Code와 Codex가 같은 규칙을 따른다.

## Impact

- 앱·백엔드·크롤러 코드와 배포에는 영향이 없다. Android/iOS, 모바일/워치 모두 무변경이다.
- 기존 design 규칙 2개는 그대로 두고 1개를 추가했다.
- 위험: `~/.cache/puppeteer`를 정리하면 mmdc가 렌더하지 못한다. 그때는 `npx puppeteer browsers install chrome-headless-shell`을 다시 실행한다.
- 롤백: config.yaml과 AGENTS.md에서 추가한 줄을 지운다.
