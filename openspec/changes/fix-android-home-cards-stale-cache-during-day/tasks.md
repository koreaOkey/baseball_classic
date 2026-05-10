# Tasks

## 코드 변경
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/data/BackendGamesRepository.kt:132-163` `fetchTodayGamesCached` 네트워크 우선 + 캐시 폴백으로 재구성
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt:454-466` ON_RESUME 무조건 `todayGamesReloadToken += 1`
- [x] `apps/mobile/app/src/main/java/com/basehaptic/mobile/MainActivity.kt:468-498` LaunchedEffect 조기 반환 제거, 첫 진입에만 캐시 페인트

## 검증
- [x] `./gradlew :mobile:assembleDebug` 빌드 성공
- [x] 실기기 설치 후 `backend_games_cache.xml` 점검: SCHEDULED → LIVE + 정상 점수로 갱신
- [ ] 사용자 검증: 홈 카드 점수 표시 + LIVE 카드 탭 시 "워치 동기화" 프롬프트 노출
- [ ] 휴식일 시나리오 회귀 검증 (네트워크 응답 빈 배열 → "오늘 경기 없음" 정상)
- [ ] iOS 동등 경로(앱 재진입 시 score refresh) 회귀 점검 — iOS 는 NSCache 휘발이라 영향 없을 것으로 추정

## 후속
- [ ] 다음 Android 릴리즈(versionCode bump) 포함
- [ ] 캐시 단일화: `fetchXxxCached` 류는 일괄 "네트워크 우선, 캐시 폴백" 형태로 통일하는 것이 안전 (현재는 함수마다 정책이 다름)
