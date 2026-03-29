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
