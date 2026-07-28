# Tasks — add-android-venting-mode-mock

## 1. 자산

- [x] 1.1 iOS Asset Catalog 스프라이트 10종(인형 4단계 + 도구 5 + 히트 이펙트)을 drawable-nodpi로 복사 (venting_doll_*, venting_tool_*, venting_hit_effect)

## 2. 데이터·상태 계층 (백엔드 미수정)

- [x] 2.1 VentingModels.kt: RegretCandidate / VentingManagerOption / VentingGameResult / VentingGameContext / VentingTarget / VentingTool 포팅
- [x] 2.2 Destruction.kt: 상수(0.33/0.66/1.0, 1/40) + 단계 enum + 상태머신, 경기당 첫 완파 SharedPreferences 기록
- [x] 2.3 MockRegretProvider: iOS mock JSON과 동일 데이터 + 오늘(KST)·현재 마이팀 주입
- [x] 2.4 VentingOpenConditionChecker: 마이팀 패배 + 당일 KST + 무승부/취소 미오픈 (실제 코드)
- [x] 2.5 VentingFeatureFlag: BuildConfig.DEBUG + venting_mode_enabled 이중 게이트

## 3. 화면 (iOS 레이아웃 동일)

- [x] 3.1 VentingHomeCard + VentingHomeCardContainer (오픈 조건 미충족 시 미렌더, 풀스크린 Dialog 진입)
- [x] 3.2 VentingTargetSelectionScreen: TOP5 + 감독 고정 6번째 + 면책 문구 + 부분 표시
- [x] 3.3 VentingRoomScreen: 인형 직접 탭 타격, 도구 트레이(A안), 내려치기 연출(와인드업→스윙→히트스톱→복원) + 히트 이펙트 + 스쿼시 + 흔들림, 게이지·임계값 마커·단계 배경
- [x] 3.4 VentingDestroyedScreen: 완파 연출 + 첫 완파 배지 + 재도전(Phase 1 무광고 허용)
- [x] 3.5 VentingFlowCoordinator: selection → room → destroyed 흐름 + BackHandler

## 4. 진동

- [x] 4.1 VentingHapticPlayer: 탭 경진동(50ms 스로틀) / CRACKED·BURST 중진동 / DESTROYED 성공 웨이브폼

## 5. 통합

- [x] 5.1 HomeScreen: 오늘의 경기 헤더 아래 카드 삽입 + 목업 패배 경기(3:7) 주입 (iOS 동일)
- [x] 5.2 SettingsScreen: DEBUG 섹션 "분풀이 모드" 토글

## 6. 검증

- [x] 6.1 assembleDebug 빌드 통과
- [x] 6.2 에뮬레이터(Pixel 8) E2E: 토글 → 홈카드 → 선택 → 룸 단계 전환(38% 균열) → 완파 화면 스크린샷 확인
- [ ] 6.3 실기기 진동 검증 (탭/단계 전환/완파 패턴)
