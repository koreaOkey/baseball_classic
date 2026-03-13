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


## Recent Changes (2026-03-07)

- Mobile home list now shows only today's games via backend date filter (`/games?date=today`).
- Game cards now show start-time information and finished-state text consistently.
- Live-card watch sync UX was tightened:
  - synced live card is highlighted
  - sync approval dialog is shown only for unsynced live cards
- Watch app finished-state display now normalizes inning/count presentation for end-of-game.

## Recent Changes (2026-03-13)

- Mobile app now supports auto watch-sync confirmation when my-team game transitions to `LIVE`.
- Existing manual watch-sync interaction remains available.
- Crawler/dispatcher now supports pregame lineup start from Naver preview data via
  `--enable-preview-lineup-precheck`.
