# AGENTS.md

> **이 파일의 내용은 `openspec/specs/`로 이전되었습니다.**
>
> | 기존 섹션 | 이전 위치 |
> |-----------|----------|
> | Backend State | `openspec/specs/ingest/spec.md` |
> | Verified Results | `openspec/specs/ingest/spec.md` |
> | Important Fixes | `openspec/specs/game-state/spec.md` |
> | Known Gap / Next Task | `openspec/specs/crawling/spec.md` (구현 완료) |
> | 환경 설정 / 실행 방법 | `openspec/specs/infra/spec.md` |
>
> 프로젝트 전체 구조와 명세는 `openspec/` 디렉토리를 참고하세요.

## OpenClaw 실행 규칙

- `openclaw` 관련 명령은 항상 WSL 로그인 셸에서 실행합니다.
- 기본 형식: `wsl bash -lc 'openclaw <args>'`
- Windows PowerShell에서 `openclaw`를 직접 실행하지 않습니다.

## Codex OpenSpec 운영 규칙

Codex로 작업을 요청받아도 Claude Code에서 사용하던 `.claude/commands/opsx/`와 `.claude/skills/`의 OpenSpec 흐름을 동일한 기준으로 따른다.

### 기준 파일

- 프로젝트 컨텍스트와 작성 규칙은 `openspec/config.yaml`을 우선 확인한다.
- 현재 동작 명세는 `openspec/specs/`를 기준으로 삼는다.
- 진행 중인 변경은 `openspec/changes/<change>/`의 `proposal.md`, `design.md`, `tasks.md`, `specs/`를 기준으로 삼는다.
- Claude 워크플로우 정의는 `.claude/commands/opsx/*.md`와 `.claude/skills/openspec-*.md`를 Codex에서도 참고한다.

### 요청 매핑

- 사용자가 `/opsx:explore`, `explore`, `먼저 검토`, `요구사항 정리`, `설계 논의`를 요청하면 `.claude/commands/opsx/explore.md` 흐름을 따른다.
  - 코드와 명세는 읽을 수 있지만 애플리케이션 코드는 구현하지 않는다.
  - 결정사항을 저장할 필요가 있으면 먼저 어디에 반영할지 제안한다.
- 사용자가 `/opsx:propose`, `proposal`, `변경 제안`, `OpenSpec 만들어줘`를 요청하면 `.claude/commands/opsx/propose.md` 흐름을 따른다.
  - `openspec new change "<name>"`로 변경 디렉토리를 만들고, OpenSpec CLI 지시에 따라 필요한 artifact를 순서대로 작성한다.
- 사용자가 `/opsx:apply`, `구현해줘`, `tasks 진행`, `이 change 적용`을 요청하면 `.claude/commands/opsx/apply.md` 흐름을 따른다.
  - 먼저 change를 선택/확인하고 `openspec status --change "<name>" --json` 및 `openspec instructions apply --change "<name>" --json`을 확인한다.
  - CLI가 반환한 `contextFiles`를 모두 읽은 뒤 구현한다.
  - 완료한 항목만 `tasks.md`에서 `- [ ]`를 `- [x]`로 바꾼다.
- 사용자가 `/opsx:archive`, `archive`, `변경 완료 처리`를 요청하면 `.claude/commands/opsx/archive.md` 흐름을 따른다.
  - artifact와 task 완료 상태를 확인하고, delta spec이 있으면 main spec 동기화 필요 여부를 설명한 뒤 진행한다.

### 구현 전 확인 절차

- 단순 질문이 아닌 코드 변경 요청이면 먼저 관련 `openspec/specs/`와 진행 중인 `openspec/changes/`를 확인한다.
- 관련 change가 명확하지 않으면 `openspec list --json`으로 활성 change를 확인한다.
- 활성 change가 여러 개이고 자동 선택이 위험하면 사용자에게 어떤 change를 적용할지 묻는다.
- 기존 코드와 명세가 충돌하면 즉시 구현하지 말고 충돌 내용을 설명하고, spec 또는 design 업데이트가 필요한지 확인한다.

### 작업 기록 규칙

- 최종 답변에는 최소한 다음을 포함한다.
  - 참고한 OpenSpec/Claude 기준 파일
  - 수정한 파일
  - 완료 처리한 `tasks.md` 항목이 있으면 그 항목
  - 실행한 검증 명령과 결과
- OpenSpec artifact에는 `openspec/config.yaml`의 규칙을 따른다.
  - spec은 한국어로 작성하고 Given/When/Then 시나리오 형식을 사용한다.
  - spec에는 클래스명, 라이브러리명 같은 구현 디테일을 넣지 않는다.
  - task에는 Android/iOS, 모바일/워치 영향 확인을 포함한다.
  - DB 마이그레이션이 필요하면 별도 task로 분리한다.
- `.claude` 파일과 `openspec` 파일이 서로 다르면 `openspec`의 현재 명세와 active change를 우선하고, 워크플로우 절차는 `.claude`를 참고한다.
