## Context

BaseHaptic(야구봄)은 iOS·Android·backend·crawler 4개 축으로 구성된 모노레포다. 지금까지 배포 시점 관리는 커밋 메시지에 의존해 왔고("완전 1차 최종2" 등), 그 결과 기존 `v1.0.0` 태그가 실제 배포 커밋이 아닌 엉뚱한 커밋(`c28c439` — xcuserdata ignore 커밋)을 가리키고 있었다. 이번 작업 중 `v1.0.0`을 올바른 커밋(`2961d78`)으로 이동시켰지만, 재발 방지 장치가 없으면 같은 혼란이 반복된다.

혼자 운영하는 레포라 협업자 부담은 없으나, 플랫폼이 4개로 늘어나면서 **어느 플랫폼의 어느 버전이 언제 배포됐는가**를 추적할 방법이 필요해졌다.

## Goals / Non-Goals

**Goals:**
- 브랜치/태그/롤백 규약을 단일 문서(`specs/release/spec.md`)로 명문화
- 혼자 쓰는 레포의 실무 현실(짧은 작업, 태그 교정 허용)과 플랫폼별 독립 배포를 동시에 커버
- 향후 CI/CD 자동화 도입 시 참조할 기준점 제공
- 릴리즈 이력(버전 ↔ 커밋 SHA ↔ 날짜) 테이블을 spec 내에 유지

**Non-Goals:**
- CI/CD 파이프라인 자체 구현 (GitHub Actions 등) — 이번 change 범위 외
- 앱스토어/플레이스토어 심사 플로우 문서화
- 시맨틱 버저닝 규칙의 엄격한 강제 (가이드 수준만)
- semver 자동 bump 도구 도입

## Decisions

### D1. 브랜치 전략: main + 짧은 feature 브랜치
- `main` = 항상 배포 가능 상태
- `feature/*` = 작업 단위(기능/리팩토링), 머지 후 삭제
- `hotfix/*` = 태그 시점에서 파생, 긴급 수정용
- **대안 기각**: 영역별 장기 브랜치(`develop`, `infra` 등). 모노레포에서 API 계약을 공유하다 드리프트가 커짐.

### D2. 태그 컨벤션: 통합 + 플랫폼별 병행
- 통합 릴리즈: `v{major}.{minor}.{patch}` (예: `v1.0.0`)
- 플랫폼별 릴리즈: `{platform}-v{major}.{minor}.{patch}` — `ios-v`, `android-v`, `backend-v`, `crawler-v`
- 플랫폼이 독립 배포되는 현실(앱스토어 심사 vs 서버 배포 속도 차이)을 반영
- **대안 기각**: 통합 태그만 사용. 플랫폼별 배포 시점이 다를 때 표현 불가.

### D3. 태그 force-update 허용 (혼자 쓰는 레포 한정)
- 잘못 찍힌 태그는 `git tag -d` + 원격 삭제 + 재생성 허용
- **근거**: 협업자가 없고 로컬 clone이 사용자 본인의 단말 하나뿐. 일반적으로는 금지되는 관행이지만 이 프로젝트 조건에서는 정확성이 우선.
- 이 결정은 spec에 **명시적 예외 조항**으로 기록 — 향후 협업자가 합류하면 즉시 재검토 필요.

### D4. 릴리즈 이력 표를 spec 내부에 유지
- `specs/release/spec.md` 하단에 "릴리즈 이력" 섹션으로 version / commit SHA / platform / date / notes 표 유지
- **대안 기각**: 별도 CHANGELOG.md. 이미 openspec에 모든 변경이 change 단위로 남기 때문에 이중 관리 방지.

### D5. 롤백은 태그 기반 checkout + hotfix 브랜치
- `git checkout <tag>` → 상태 확인
- 수정 필요하면 `git checkout -b hotfix/<platform>-<version> <tag>`
- 수정 후 새 패치 태그(`<platform>-v{x.y.z+1}`) 생성 및 배포
- **기각**: `git reset --hard`로 main을 과거로 강제 이동. 히스토리 손실 위험.

## Risks / Trade-offs

- **[Risk] 태그 force-update 관행이 고착되면 협업 확장 시 사고 위험** → Mitigation: spec에 "혼자 쓰는 레포 한정 예외"임을 명시하고, 협업자 합류 트리거를 재검토 조건으로 박아둠.
- **[Risk] 통합 태그와 플랫폼별 태그가 혼용되면 "진짜 버전"이 불분명** → Mitigation: 릴리즈 이력 표에 **항상 두 가지를 함께 기록**(예: `v1.0.0` 행에 iOS/Android 두 플랫폼이 동일 SHA라고 명시).
- **[Risk] 스펙만 만들고 실제 배포 때 태그를 까먹음** → Mitigation: 배포 체크리스트를 시나리오 형태로 spec에 포함해 "배포 완료 정의"에 태그 존재를 필수로 포함.
- **[Trade-off] 플랫폼별 태그는 관리 오버헤드 증가** → 통합 태그를 기본값으로 쓰고, 독립 배포가 실제로 발생할 때만 플랫폼별 태그 사용.

## Migration Plan

1. `specs/release/spec.md` 신규 작성 (이 change의 산출물)
2. 기존 `v1.0.0` 태그 교정은 **이미 완료됨** (`2961d78`로 이동, 원격 반영)
3. 추가 마이그레이션 없음 — 다음 배포부터 새 규약 적용

**Rollback:** spec 문서는 제품 동작에 영향이 없어 롤백 개념이 불필요. openspec change를 되돌리면 된다.

## Open Questions

- 릴리즈 이력 표를 spec 본문에 두는 게 맞는가, 아니면 `specs/release/history.md`로 분리하는 게 나은가? → 우선 본문 유지, 표가 커지면 분리 고려.
- 향후 GitHub Actions 도입 시 태그 푸시를 트리거로 쓸지, 수동 workflow dispatch로 쓸지는 별도 change에서 결정.
