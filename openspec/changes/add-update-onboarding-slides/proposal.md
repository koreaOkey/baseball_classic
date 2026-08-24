# add-update-onboarding-slides — 슬라이드형 업데이트 안내 (1.1.8 대표 기능 온보딩)

## Why

1.1.8은 분풀이 모드·잠금 화면 라이브 스코어라는 비주얼 임팩트가 큰 대표 기능 2개를
포함한다. 기존 업데이트 안내 모달은 텍스트 불릿뿐이라 이 기능들의 전달력이 약하다.
목업 검토(아티팩트)로 확정된 방향: **팝업 카드 안에서 슬라이드로 넘기는 3페이지 구성**
— 대표 기능은 이미지 페이지, 나머지 변경사항은 마지막 텍스트 불릿 페이지.

## What Changes

- **모델 확장 (iOS·Android)**: `ReleaseNote`에 옵셔널 `featurePages` 추가.
  - `WhatsNewFeaturePage(visual, title, body)` — visual 은 `image`(스크린샷 crop 표시)
    또는 `lockScreen`(코드로 그린 시계·날짜 잠금 화면 프레임 + 노티 카드 이미지) 2종.
  - **featurePages 가 비어 있으면 기존 단일 불릿 모달 그대로** → 이후 소규모 버전 호환.
- **슬라이드 레이아웃 (iOS `WhatsNewSheet` / Android `WhatsNewDialog`)**:
  - iOS `TabView(.page)` / Android `HorizontalPager`, 카드 높이 고정(화면 72%, 최대 600)
    으로 페이지 전환 시 출렁임 방지.
  - 닷 인디케이터(활성 = 팀 `controlAccent`) + 하단 버튼 "다음"→마지막 페이지 "확인".
    스와이프·닷 탭·버튼 모두 이동 가능. 배경 탭 닫기 유지(버전 게이트가 이미 기록되므로
    재노출 없음, 설정 "업데이트 안내"로 재열람).
- **1.1.8 엔트리 추가**: 1p 분풀이(실제 룸 스크린샷 crop) → 2p 잠금 화면(실기기 노티
  스크린샷) → 3p 불릿 3개(라인스코어·박스스코어 탭 / 타석 카드 타순·오늘 성적 /
  워치 관람 속도) + 마무리 한 줄.
- **이미지 에셋**: iOS `WhatsNewVenting`/`WhatsNewLockScreen` imageset,
  Android `drawable-nodpi/whats_new_venting.jpg`/`whats_new_lockscreen.jpg` (각 ~70KB JPEG).

## Impact

- 변경: iOS 2파일(`ReleaseNotes.swift`, `WhatsNewSheet.swift`) + imageset 2개,
  Android 2파일(`ReleaseNotes.kt`, `WhatsNewDialog.kt`) + drawable 2개.
- **트리거·게이트 로직 무변경**: `last_seen_update_version`·`isExistingUserAtLaunch`·
  피처 가이드 큐잉(`queueFeatureGuideIfNeeded`) 그대로. 이번 버전엔 홈 스포트라이트
  피처 가이드 신규 스텝을 추가하지 않아 온보딩과 겹치지 않는다.
- 설정의 "업데이트 안내 다시 보기"는 같은 컴포넌트를 재사용하므로 자동으로 슬라이드형.
- 검증: iOS `BaseHaptic` 시뮬레이터 빌드, Android `:mobile:compileDebugKotlin`.

## Non-Goals

- 분풀이 "지금 해보기" CTA — 분풀이는 경기 컨텍스트(아쉬운 순간 후보)가 필수라
  업데이트 직후엔 열 대상이 없는 경우가 대부분. 적시 진입은 기존 패배 프롬프트·홈 카드·
  패배 푸시가 담당. 연습(컨텍스트 없는) 룸이 생기면 재검토.
- 분풀이 진입점 NEW 뱃지, 룸 첫 진입 흔들기 가이드 — 별도 change.
- 앱 버전(1.1.8) 범프 — 릴리즈 준비 시점에 수행(범프 전까지 모달 미노출이 정상).
- Android 잠금 화면 이미지의 Android 실기기 스크린샷 교체 — 현재 iOS Live Activity
  스크린샷을 양 플랫폼 공용 사용, 릴리즈 전 Android 캡처로 교체 권장.

## Capabilities

### Modified Capabilities
- `whats-new-popup`: 대표 기능이 있는 버전은 이미지 슬라이드 + 마지막 불릿 페이지로
  안내하고, 없는 버전은 기존 단일 불릿 모달을 유지한다.
