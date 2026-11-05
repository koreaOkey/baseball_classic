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

## 제품·운영 규칙 (Claude Code 자동 메모리와 동기화, 2026-09-07)

Claude Code 메모리에만 있던 제품 규칙을 두 툴이 같이 읽도록 옮긴 것이다. Claude Code는 `CLAUDE.md`의 `@AGENTS.md` import로 이 파일을 읽는다. 규칙이 바뀌면 메모리가 아니라 이 섹션을 고친다.

- **앱 이름**: 사용자 노출 브랜드명은 "야구봄". 코드·패키지·파일 식별자는 BaseHaptic(`com.basehaptic.*`) 그대로 둔다. 마케팅·숏츠·스토어 설명·사용자 안내 문구에는 반드시 "야구봄"으로 쓴다.
- **빠따존 명칭**: 분풀이 모드의 사용자 노출 명칭은 "빠따존"(2026-08-24 전환). 빠따존=장소·모드 명칭, "분풀이"는 행위 표현으로만 남긴다("분풀이 완료!"). 💢 이모지는 단독 제목·배지에만 쓰고 문장·버튼·아이콘 있는 행에는 텍스트만. 코드명 venting(클래스·패키지·API `/venting/*`·DB `venting_*`·푸시 kind·딥링크)은 바꾸지 않는다. 카피에 "분풀이 모드"라고 쓰지 않는다.
- **빠따존 광고 게이트**: Rewarded 게이트는 완파 화면 "재도전" 버튼에만 적용하며 플로우 세션당 1회 무료다. 나갔다 재진입하면 무료 완파가 가능한 점은 사용자가 2026-08-24 "현재대로 유지"로 확정했다. 경기당 1회 엄격화를 다시 제안하지 않는다.
- **워치 이벤트 "영상"**: HOMERUN·HIT·SCORE 등에서 재생되는 것은 마스코트 캐릭터 애니메이션이지 실제 경기 리플레이가 아니다. 노티·Live Activity에 "[영상보기]" 같은 액션 버튼을 넣지 않는다. 워치 foreground에서만 자동 재생되고 폰에는 없다. 설정 키 `event_video_enabled`는 마스코트 애니메이션 ON/OFF다. 변환 도구는 `watch-animation`, `watch-animation-android`.
- **이미지 제작**: 일러스트·실사풍을 포함한 모든 이미지 생성은 hermes-designer(디자이너)에 위임한다. Aside로 Gemini 등 이미지 생성 서비스를 직접 쓰지 않는다(2026-08-31·09-01 두 차례 중단 지시). 디자이너가 못 하면 우회 생성하지 말고 한계를 사용자에게 보고한다. 무료 스톡 사진 수집은 생성이 아니므로 예외이나 애매하면 확인한다.
- **다이어그램 도구**: 새 아키텍처 설계·대안 비교는 Mermaid로 그린다(OpenSpec design.md의 Mermaid 블록, `mmdc`로 렌더 확인, 없으면 `npm i -g @mermaid-js/mermaid-cli`). 확정된 시스템을 공유용 한 장으로 그릴 때는 stack-diagram 스킬(공식 로고, HTML·PNG)을 쓴다. 둘 다 코드로 렌더하는 다이어그램이라 위 이미지 제작 규칙(디자이너 위임) 대상이 아니다. stack-diagram의 공개 갤러리 공유는 사용자가 원할 때만 한다.
- **변경 기록 자동화**: 코드·인프라 변경을 마무리하면 요청이 없어도 `openspec/changes/archive/{날짜-제목}/`에 proposal.md와 tasks.md를 작성한다. 형식은 기존 archive 항목(proposal: Why/What Changes/Capabilities/Impact, tasks: 체크리스트)을 따른다.
- **배포 기준 브랜치**: 실운영은 `staging` 브랜치다. Railway 프로젝트 baseball-classic-staging이 staging tip을 배포하며 `origin/staging` 푸시가 곧 프로덕션 배포 트리거다. 검증 전에 staging을 푸시하지 않는다. 브랜치·태그·롤백 절차는 `openspec/specs/release/spec.md`를 먼저 본다.

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
- 사용자가 `/opsx:sync`, `sync`, `spec 동기화`, `delta spec 반영`을 요청하면 `.claude/commands/opsx/sync.md` 흐름을 따른다.
  - change 이름이 없으면 `openspec list --json`으로 delta spec이 있는 change를 보여주고 사용자에게 고르게 한다. 추측으로 자동 선택하지 않는다.
  - delta spec을 읽고 main spec을 직접 편집해 병합한다. change 디렉토리는 옮기지 않는다(archive와 별개).

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
