# Tasks

## 코드 변경
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt:54` `lastNotificationText: String?` 필드 추가
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt:77-79` 초기 본문 `워치로 관람 중...` 을 `initialText` 변수로 추출하고 `lastNotificationText` 동기화
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt:133-148` `pushStateToWatch` 진입부에서 `Team.teamName`(마스코트) 우선, raw 문자열 폴백으로 `${away} vs ${home} 경기 워치로 관람 중...` 알림 갱신 (텍스트 변동 시에만)
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/service/GameSyncForegroundService.kt:333` `setContentTitle("BaseHaptic")` → `setContentTitle("야구봄")`

## 검증
- [ ] `./gradlew :mobile:assembleDebug` 빌드 성공
- [ ] 실기기 워치 관람 시작 → 알림 타이틀 `야구봄`, 본문 마스코트 표기(예: `랜더스 vs 베어스 경기 워치로 관람 중...`) 노출 확인 — 팀 코드(SSG, 두산)가 보이지 않아야 함
- [ ] 워치 관람 중 다른 경기로 전환 시(서비스 재시작 시나리오) 새 팀명으로 정상 갱신 확인
- [ ] 백엔드 첫 응답 지연 시 초기 `워치로 관람 중...` 가 잠깐 보였다가 즉시 팀명 포함 텍스트로 갱신되는지 확인

## 후속
- [ ] 다음 Android 릴리즈(versionCode bump) 포함
- [ ] iOS 동등 노티(있는 경우) 표기 일관성 검토 — iOS 는 별도 LiveActivity 경로라 우선 대상 아님
