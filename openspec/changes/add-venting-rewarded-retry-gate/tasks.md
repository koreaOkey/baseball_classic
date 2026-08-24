# Tasks — add-venting-rewarded-retry-gate

## 1. 재도전 광고 게이트
- [x] 1.1 iOS `VentingRetryVerdict` + 프로토콜 반환형 확장, `RewardedAdGate` 신규(+pbxproj 등록), 코디네이터 gate 교체
- [x] 1.2 iOS 완파 화면 — "광고 보고 재도전하기" 버튼·스피너·중복 탭 가드, verdict 분기
- [x] 1.3 Android `VentingRetryGate` 신규 + 완파 화면 클릭 배선(동일 UX)
- [x] 1.4 광고 유닛 상수 추가(테마 스토어 유닛 재사용, DEBUG는 테스트 유닛)

## 2. 지표
- [x] 2.1 `retry_ad_complete` 클라이언트 보고(보상 획득 시에만) — 양 플랫폼
- [x] 2.2 백엔드 `VALID_VENTING_EVENT_TYPES`에 retry_ad_complete 추가
- [x] 2.3 `GET /venting/ad-funnel` — 이벤트별 count·users(DISTINCT user_id)·completion_rate·KST 일별 추이 (X-API-Key)

## 3. 검증
- [x] 3.1 iOS Debug+Release 시뮬레이터 빌드 통과
- [x] 3.2 Android `:app:compileDebugKotlin`+`:app:compileReleaseKotlin` 통과
- [x] 3.3 백엔드 py_compile 통과
- [ ] 3.4 실기기(DEBUG=테스트 광고): 완파 → 광고 보고 재도전 → 보상 후 룸 재진입, 중도 이탈 시 거부, 이벤트 3종 기록 확인
- [ ] 3.5 백엔드 배포 후 `/venting/ad-funnel` 응답 확인
- [ ] 3.6 전용 AdMob Rewarded 유닛 발급 후 상수 교체 (iOS `ventingRetryAdUnitID` / Android `VENTING_RETRY_AD_UNIT`)
