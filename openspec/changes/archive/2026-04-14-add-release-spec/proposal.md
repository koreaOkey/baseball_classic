## Why

현재 배포/롤백 기준이 문서화되지 않아 "어느 커밋이 1.0.0인지" 같은 기초 질문조차 태그 검사로 매번 확인해야 한다. 실제로 기존 `v1.0.0` 태그가 잘못된 커밋을 가리키고 있던 사실이 대화 중에 발견됐고, 혼자 운영하는 프로젝트여도 릴리즈 일관성을 잡아두지 않으면 플랫폼(iOS/Android/backend/crawler)이 늘어날수록 혼란이 커진다.

## What Changes

- **브랜치 전략** 명문화: `main` = 항상 배포 가능, `feature/*` = 작업 단위 짧은 수명, 머지 후 삭제
- **태그 컨벤션** 확립:
  - 통합 릴리즈: `v{major}.{minor}.{patch}` (예: `v1.0.0`)
  - 플랫폼별 릴리즈: `ios-v*`, `android-v*`, `backend-v*`, `crawler-v*`
- **배포 체크리스트**: 태그 생성 → 원격 푸시 → 배포 기록
- **롤백 절차**: `git checkout <tag>` → `hotfix/*` 브랜치에서 수정 → 새 패치 태그
- **릴리즈 이력 표**: `v1.0.0` = `2961d78` "완전 1차 최종2" (iOS+Android 최초 배포, 2026-04-07)
- **예외 규정**: 혼자 운영 레포이므로 잘못된 태그의 force-update(`git push -f` tag) 허용

## Capabilities

### New Capabilities
- `release`: 브랜치 전략, 태그 컨벤션, 배포 체크리스트, 롤백 절차, 릴리즈 이력을 다루는 릴리즈/CI-CD 일관성 스펙

### Modified Capabilities
(없음 — 순수 신규 spec)

## Impact

- 신규 문서: `openspec/specs/release/spec.md`
- 영향 코드 없음 (문서·규약 성격)
- 향후 CI/CD(예: GitHub Actions) 도입 시 이 spec이 기준이 됨
- 기존 태그 `v1.0.0`은 이미 올바른 커밋(`2961d78`)으로 이동 완료된 상태
