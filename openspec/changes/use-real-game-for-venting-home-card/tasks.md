# Tasks — use-real-game-for-venting-home-card

## 1. iOS

- [x] 1.1 `HomeScreen.swift`: `ventingMockFinishedGame` 주입 삭제, `ventingFinishedLossGame`
      (오늘 완료된 마이팀 패배 경기) 선별 후 컨테이너에 전달
- [x] 1.2 `VentingFlowCoordinator.swift` `VentingHomeCardContainer`: Mock →
      서버 regret-top5 + state + boxscore 병렬 조회, 실패 시 `LiveRegretProvider` 폴백
      (loss_push 딥링크와 동일 경로), `--venting-force-show` 분기 삭제
- [x] 1.3 `BaseHapticApp.swift`: `--venting-force-show` 팀 자동 선택 삭제
- [x] 1.4 `Mock/MockRegretProvider.swift`·`Resources/mock_regret_candidates.json` 삭제
      + pbxproj 참조(빌드파일·파일레퍼런스·그룹·Resources 그룹) 정리
- [x] 1.5 시뮬레이터 빌드 통과 (BaseHaptic scheme, Debug)

## 2. Android

- [x] 2.1 `HomeScreen.kt`: `ventingMockFinishedGame` 주입 삭제, `ventingFinishedLossGame`
      선별 후 컨테이너에 전달, `VentingFeatureFlag` 미사용 import 제거
- [x] 2.2 `VentingFlowCoordinator.kt` `VentingHomeCardContainer`: Mock →
      Dispatchers.IO 병렬 조회 + `ServerRegretProvider` 조립, `LiveRegretProvider` 폴백,
      `VentingOpenConditionChecker` 최종 판정
- [x] 2.3 `venting/MockRegretProvider.kt` 삭제
- [x] 2.4 `:app:compileDebugKotlin` 통과

## 3. 검증 (잔존)

- [ ] 3.1 실기기: 마이팀 패배 당일 홈카드 노출 + 카드 스코어/미리보기 = 실제 경기 확인
- [ ] 3.2 실기기: 카드 진입 → 대상 목록이 서버 regret-top5(실명 조인 포함)로 표시되는지 확인
- [ ] 3.3 마이팀 승리/무/경기 없음 날 홈카드 미노출 확인
