# Apps

클라이언트 앱 영역입니다.

## 구조
- `mobile/`: 스마트폰 앱 (Android, Hub 역할)
- `watch/`: Wear OS 워치 앱 (Live Node)

## 아키텍처
- 폰이 **중계 허브**가 되어 워치로 이벤트를 전달하는 구조
- **Wearable Data Layer API**를 통한 모바일 ↔ 워치 실시간 통신 구현 완료
  - 경기 데이터 동기화 (`WearGameSyncManager`)
  - 팀 테마 동기화 (`WearThemeSyncManager`)
  - 햅틱 이벤트 전달 (HOMERUN, HIT, OUT, SCORE 등)

## 빌드
루트(`baseball_classic/`)에서 모노레포 Gradle로 통합 빌드 가능:
```bash
./gradlew :apps:mobile:app:assembleDebug
./gradlew :apps:watch:app:assembleDebug
```

