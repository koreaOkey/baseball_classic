## 1. Spec 문서화

- [ ] 1.1 `openspec/specs/release/spec.md` 최종 확정 (archive 시 change에서 복사됨)
- [ ] 1.2 릴리즈 이력 표에 `v1.0.0` 항목과 교정 비고가 포함되어 있는지 확인

## 2. 기존 태그 정합성 검증

- [ ] 2.1 `git show v1.0.0`이 커밋 `2961d78`을 가리키는지 확인
- [ ] 2.2 원격 태그(`git ls-remote --tags origin`)에서도 `v1.0.0`이 동일 커밋인지 확인

## 3. 규약 준수 확인

- [ ] 3.1 현재 브랜치(`feature/next-update`)가 spec의 브랜치 네이밍 규칙과 호환되는지 확인 (호환됨: `feature/*` 패턴)
- [ ] 3.2 `main` 외의 장기 브랜치가 없는지 확인

## 4. Archive 및 후속

- [ ] 4.1 change archive 실행 → `specs/release/spec.md`가 `openspec/specs/`로 이동되는지 확인
- [ ] 4.2 다음 배포 시 이 spec의 배포 체크리스트를 따라 태그 생성·푸시·이력 갱신을 실행 (이 change 범위 외, 향후 배포 시 적용)
