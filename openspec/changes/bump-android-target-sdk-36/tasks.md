# Tasks — Bump Android target SDK to 36

## 코드 변경
- [x] 모바일 `apps/mobile/app/build.gradle.kts`: `targetSdk = 36`
- [x] 워치 `apps/watch/app/build.gradle.kts`: `compileSdk = 36`
- [x] 워치 `apps/watch/app/build.gradle.kts`: `targetSdk = 36`

## 빌드 검증
- [x] 모바일 `:app:assembleDebug` 통과 (exit 0, APK 생성)
- [x] 워치 `:app:assembleDebug` 통과 (exit 0, APK 생성)

## Android 16 동작 변경 소스 감사 (완료)
- [x] 16KB 페이지: 두 APK 네이티브 `.so` 전부 ELF p_align 0x4000 = 16KB 정렬 확인 ✅
- [x] 화면 방향 잠금(`screenOrientation`) 선언 0건 → 대화면 변경 영향 없음
- [x] Predictive back: 레거시 `onBackPressed` 없음, Compose `BackHandler`만 → 안전
- [x] Job/Alarm(WorkManager/JobScheduler/AlarmManager) 미사용 → 쿼터 변경 무관
- [x] `POST_NOTIFICATIONS` 런타임 요청 이미 구현

## 실기기 동작 검증 (잔존, 시각/체감 확인)
- [ ] Android 16 실기기/에뮬레이터에서 앱 정상 구동
- [ ] edge-to-edge: 콘텐츠가 상태바/내비바 뒤로 가려지지 않는지 시각 확인
- [ ] 라이브 스코어 포그라운드 노티 + 잠금화면 라이브 노티 체감 정상
- [ ] 워치 동기화 흐름 정상 (Wear OS)

## 선택적 하드닝 (후속)
- [ ] dataSync FGS(`GameSyncForegroundService`/`GameForegroundService`)에 `onTimeout()` 추가
      — 하루 다수 경기 장시간 사용 시 6h 쿼터 대비

## 릴리즈 (잔존)
- [ ] 배포 시 versionCode 상향 (모바일/워치)
- [ ] Play Console에서 targetSdk 36 경고 해소 확인
