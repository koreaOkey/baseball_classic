# Tasks

## 1. 이미지 자산 추가

- [x] 1.1 higgsfield CLI 로 다크 야간 톤 야구장 PNG 1장 생성 → `reference/field-bg-candidates/A-dark-night.png`
- [x] 1.2 iOS — `ios/mobile/BaseHaptic/Assets.xcassets/BaseballFieldBackground.imageset/` 디렉토리 생성, `Contents.json` + `field.png` 추가
- [x] 1.3 Android — `apps/mobile/app/src/main/res/drawable/baseball_field_background.png` 파일 추가 (원본 또는 WebP)
- [x] 1.4 양 플랫폼 빌드 검증으로 자산이 번들되는지 확인

## 2. 포지션 좌표 정의 (양 플랫폼 공통)

- [x] 2.1 10개 포지션 정규화 좌표(0.0~1.0) 상수 정의 — `LF, CF, RF, SS, 2B, 3B, 1B, P, C, B`
- [x] 2.2 정규화 좌표에 1·2·3루 좌표 포함 — 루상 점유 원 표시용
- [x] 2.3 iOS/Android 두 정의가 동일 값(허용 오차 ±0.005 이내) 인지 cross-check

## 3. iOS 필드 카드 재구성

- [x] 3.1 `BaseballFieldCard` 내부의 `BaseballFieldCanvas`(Path 다이아몬드 드로잉) 제거
- [x] 3.2 배경 `Image("BaseballFieldBackground")` 추가 — `.resizable().aspectRatio(4/3, contentMode: .fit)`
- [x] 3.3 정규화 좌표 → GeometryReader 기반 절대 위치 변환 헬퍼 추가
- [x] 3.4 P/B 위치에 캡슐 라벨(`pitcher` / `batter` 이름) 노출 — `AppFont.microBold` 흰색 텍스트 + 어두운 배경 캡슐
- [x] 3.5 1·2·3루 좌표에 점유 원(`Circle().fill(AppColors.green500)`) 오버레이 — 점유 시만 노출
- [x] 3.6 카드 자체 4:3 강제 + 기존 카드 코너/배경 유지

## 4. Android 필드 카드 재구성

- [x] 4.1 `BaseballFieldCard` 내부의 `BaseballFieldCanvas`(Canvas 다이아몬드 드로잉) 제거
- [x] 4.2 배경 `Image(painterResource(R.drawable.baseball_field_background))` 추가 — `ContentScale.Fit`
- [x] 4.3 정규화 좌표 → BoxWithConstraints 기반 절대 위치 변환 헬퍼 추가
- [x] 4.4 P/B 위치에 캡슐 라벨(`pitcher` / `batter` 이름) 노출 — `AppFont.microBold` 흰색 텍스트 + 어두운 배경 캡슐
- [x] 4.5 1·2·3루 좌표에 점유 원(`Box.background(Green500).clip(CircleShape)`) 오버레이 — 점유 시만 노출
- [x] 4.6 카드 자체 4:3 강제(`Modifier.aspectRatio(4f / 3f)`) + 기존 카드 코너/배경 유지

## 5. Cross-Platform Consistency

- [x] 5.1 P/B 캡슐 라벨 위치가 양 플랫폼 동일하게 보이는지 시각 cross-check
- [x] 5.2 1·2·3루 점유 원이 배경 이미지의 베이스 위치와 정확히 정렬되는지 확인
- [x] 5.3 카드 4:3 비율이 양 플랫폼에서 동일하게 유지되는지 확인
- [ ] 5.4 다양한 화면 폭(폰 좁은 폭 ~ 폴더블 펼침)에서 좌표 정렬 유지 확인

## 6. Validation

- [x] 6.1 iOS BaseHaptic 앱 빌드 실행
- [x] 6.2 Android 모바일 Kotlin 컴파일 실행
- [x] 6.3 iOS 실기기 (DEBUG): DEBUG 더미 LIVE 카드 또는 실제 LIVE 경기로 필드 카드 표시 확인
- [x] 6.4 Android 실기기 (DEBUG): 위 시나리오 동일 검증
- [x] 6.5 양 플랫폼: pitcher/batter 이름이 비어있을 때 라벨이 깔끔히 숨겨지는지 확인
- [x] 6.6 양 플랫폼: 루상 점유 변화 시 점유 원이 정확한 베이스 위에 표시되는지 확인
