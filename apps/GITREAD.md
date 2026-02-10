# Git 브랜치 전략 및 커밋 가이드

## 🌿 브랜치 구조

```
main (프로덕션 준비 - 항상 배포 가능한 상태)
  │
  └── develop (개발 메인 브랜치)
        │
        ├── feature/mobile-* (모바일 앱 기능)
        ├── feature/watch-* (워치 앱 기능)
        ├── feature/backend-* (백엔드 연동)
        ├── feature/theme-* (테마 관련)
        ├── bugfix/* (버그 수정)
        └── docs/* (문서 작업)
```

## 📌 브랜치 설명

### main
- **목적**: 프로덕션 배포 가능한 안정 버전
- **규칙**: 
  - develop에서 충분히 테스트된 코드만 머지
  - 직접 커밋 금지
  - 태그로 버전 관리 (v0.1.0, v0.2.0 등)

### develop
- **목적**: 개발 통합 브랜치
- **규칙**:
  - feature 브랜치들이 머지되는 곳
  - 항상 빌드 가능한 상태 유지
  - 직접 커밋 최소화 (급한 hotfix 제외)

### feature/*
- **목적**: 새로운 기능 개발
- **규칙**:
  - develop에서 분기
  - 작은 단위로 분리 (1~3일 작업)
  - 완료 후 develop으로 PR
  - 머지 후 브랜치 삭제

## 🎯 브랜치 네이밍 규칙

```bash
feature/{scope}-{feature-name}
bugfix/{issue-number}-{bug-description}
docs/{doc-type}
hotfix/{critical-issue}

예시:
feature/mobile-haptic-feedback
feature/watch-vibration-pattern
feature/backend-api-integration
bugfix/123-team-change-crash
docs/api-documentation
hotfix/critical-data-loss
```

## 📝 커밋 메시지 규칙

### 기본 형식
```
<type>(<scope>): <subject>

<body> (선택)

<footer> (선택)
```

### Type 종류
- `feat`: 새로운 기능 추가
- `fix`: 버그 수정
- `docs`: 문서 수정
- `style`: 코드 포맷팅 (기능 변경 없음)
- `refactor`: 리팩토링
- `test`: 테스트 추가/수정
- `chore`: 빌드, 패키지 매니저 등

### Scope 종류
- `mobile`: 모바일 앱
- `watch`: 워치 앱
- `backend`: 백엔드
- `shared`: 공통 코드
- `docs`: 문서
- `ci`: CI/CD

### 예시

```bash
# 좋은 커밋 메시지
feat(mobile): 홈런 이벤트 워치 전송 구현
feat(watch): 5가지 진동 패턴 추가
fix(mobile): 팀 변경 시 크래시 수정
docs: README에 햅틱 피드백 가이드 추가
refactor(watch): HapticManager 싱글톤으로 변경
test(mobile): TeamTheme 단위 테스트 추가
chore: Gradle 버전 8.7로 업데이트

# 나쁜 커밋 메시지
update code
fix bug
working on feature
WIP
```

### 상세 커밋 예시

```bash
feat(mobile): Data Layer를 통한 워치 햅틱 전송 구현

- WearableDataClient 초기화
- 이벤트 타입별 데이터 직렬화
- 워치 연결 상태 확인
- 전송 실패 시 재시도 로직

Closes #42
```

## 🔄 워크플로우

### 1. 새 기능 시작
```bash
# develop 최신화
git checkout develop
git pull origin develop

# feature 브랜치 생성
git checkout -b feature/mobile-haptic-feedback

# 작업 및 커밋
git add .
git commit -m "feat(mobile): DataLayer 전송 구현"

# 푸시
git push -u origin feature/mobile-haptic-feedback
```

### 2. Pull Request 생성
```
제목: feat(mobile): 워치 햅틱 피드백 전송

본문:
## 개요
모바일 앱에서 경기 이벤트 발생 시 워치로 햅틱 데이터 전송

## 변경사항
- WearableDataClient 통합
- 이벤트 타입별 데이터 매핑
- 워치 연결 상태 UI 표시

## 테스트
- [x] 워치 연결 상태 확인
- [x] 홈런 이벤트 전송 테스트
- [x] 연결 실패 시 fallback

## 스크린샷
(필요시 첨부)
```

### 3. 코드 리뷰 및 머지
```bash
# PR 리뷰 완료 후
# GitHub/GitLab에서 "Squash and merge" 또는 "Merge" 클릭

# 로컬 develop 업데이트
git checkout develop
git pull origin develop

# feature 브랜치 삭제
git branch -d feature/mobile-haptic-feedback
```

### 4. develop → main 배포
```bash
# develop이 안정화되면
git checkout main
git pull origin main
git merge develop
git tag -a v0.2.0 -m "Release: 햅틱 피드백 기능 추가"
git push origin main
git push origin v0.2.0
```

## 🚀 현재 프로젝트 상태

### v0.1.0 - 초기 뼈대 (main)
- ✅ 모바일: 동적 테마 시스템 기반 구조
- ✅ 워치: Wear OS 기본 UI 구조
- ✅ 양쪽: 팀별 색상 프리셋
- ✅ 문서: 리소스 가이드

### 다음 개발 예정 (develop)
- [ ] feature/mobile-haptic-feedback: 워치 햅틱 전송
- [ ] feature/watch-vibration: 진동 패턴 구현
- [ ] feature/backend-api: 실시간 경기 데이터 API
- [ ] feature/mobile-notification: 경기 알림 설정

## 📊 버전 관리

### 버전 번호 규칙 (Semantic Versioning)
```
v{major}.{minor}.{patch}

major: 큰 변경, 하위 호환 깨짐
minor: 새 기능 추가, 하위 호환 유지
patch: 버그 수정
```

### 태그 예시
```bash
v0.1.0  # 초기 뼈대
v0.2.0  # 햅틱 피드백 추가
v0.3.0  # 백엔드 API 연동
v1.0.0  # 정식 출시
```

## ⚠️ 주의사항

1. **main 브랜치**
   - 직접 커밋 금지
   - develop에서만 머지
   - 항상 빌드/테스트 통과 상태

2. **develop 브랜치**
   - feature 머지 전 충돌 해결
   - 빌드 실패 시 즉시 수정
   - 정기적으로 main과 동기화

3. **feature 브랜치**
   - 작은 단위로 분리
   - 자주 커밋 (논리적 단위)
   - develop 최신 상태 유지 (rebase)

4. **커밋**
   - 의미 있는 메시지 작성
   - 하나의 커밋에 하나의 목적
   - WIP, temp 등 임시 커밋 지양

## 🛠️ 유용한 명령어

```bash
# 브랜치 목록
git branch -a

# 브랜치 전환
git checkout develop

# 최신 상태로 업데이트
git pull origin develop

# 변경사항 확인
git status
git diff

# 커밋 히스토리
git log --oneline --graph --all

# 원격 브랜치 삭제
git push origin --delete feature/old-feature

# develop을 현재 브랜치에 반영 (충돌 해결)
git checkout feature/my-feature
git rebase develop
```

## 🔗 참고 자료

- [Semantic Versioning](https://semver.org/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Flow](https://nvie.com/posts/a-successful-git-branching-model/)

