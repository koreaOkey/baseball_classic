# Tasks — show-venting-target-real-names

## 1. 표기 형식 "역할(실명)"
- [x] 1.1 iOS `VentingTargetSelectionScreen.swift` TargetRow 제목 `"역할 실명"` → `"역할(실명)"`
- [x] 1.2 Android `VentingTargetSelectionScreen.kt` TargetRow 제목 동일 변경

## 2. 라이브 진입 경로 실명 채움
- [x] 2.1 iOS `LiveRegretProvider.swift` batter/pitcher 후보에 `playerName` 실기 (+trim/empty→nil)
- [x] 2.2 Android `LiveRegretProvider.kt` 동일 반영
- [x] 2.3 중복 제거 키를 실명 우선(없으면 역할 레이블)으로 변경 (양 플랫폼)

## 3. 검증
- [x] 3.1 Android `:mobile:compileDebugKotlin` 통과
- [x] 3.2 iOS `BaseHaptic` 스킴 시뮬레이터 빌드 통과
- [ ] 3.3 실기기: 서버 TOP5·라이브 진입 각각 "역할(실명)" 표기 확인, 룸·완파 익명 유지 확인
