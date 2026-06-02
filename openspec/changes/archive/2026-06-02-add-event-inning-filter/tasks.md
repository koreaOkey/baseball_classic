# Tasks

## 1. 백엔드 모델·마이그레이션

- [x] 1.1 `backend/api/app/models.py` `GameEvent` 에 `inning: Mapped[str | None] = mapped_column(String(32), nullable=True)` 추가
- [x] 1.2 `backend/api/app/db.py` `_ensure_game_event_columns()` 에 `inning VARCHAR(32)` ALTER 자동 적용 추가

## 2. 백엔드 스키마

- [x] 2.1 `CrawlerEventIn` 에 옵셔널 `inning: str | None` (max_length 32) 추가
- [x] 2.2 `GameEventOut` 에 옵셔널 `inning: str | None` 추가

## 3. 백엔드 서비스

- [x] 3.1 `services.py` `upsert_game_from_snapshot` 또는 이벤트 INSERT 위치에서 `inning = payload.inning or game.inning` fallback 적용
- [x] 3.2 이벤트 출력 빌더(`build_event_out` 등) 가 `inning` 을 응답에 포함
- [x] 3.3 라인업 비교 hash 등 기존 dedup 로직에 영향 없는지 확인

## 4. 백엔드 테스트

- [x] 4.1 시드된 이벤트에 inning 이 응답에 포함되는지 확인
- [x] 4.2 payload.inning 없을 때 game.inning fallback 으로 채워지는지 확인
- [x] 4.3 pytest 38건 회귀 통과

## 5. iOS 모델·파서

- [x] 5.1 `BackendGamesRepository.swift` `LiveEvent` 에 `inning: String?` 추가
- [x] 5.2 `parseLiveEvent` 에 `inning` 파싱
- [x] 5.3 DEBUG 더미 `DebugDummyLiveGame.events` 각 항목에 `inning` 채움(예: "7회초")

## 6. iOS UI 필터

- [x] 6.1 `LiveGameScreen` 에 `selectedInning: String?` @State + `hasManualSelection: Bool`
- [x] 6.2 진입 시 `selectedInning = state.inning` 초기화, state.inning 변경 시 manual 선택 없으면 자동 따라감
- [x] 6.3 `InningTabs` 가 tab 탭 시 콜백 호출 → `selectedInning` 갱신 + `hasManualSelection = true`
- [x] 6.4 이벤트 목록을 `events.filter { $0.inning == selectedInning }` 로 필터(빈 경우 "해당 회 이벤트가 없습니다")

## 7. Android 모델·파서

- [x] 7.1 `BackendGamesRepository.kt` `LiveEvent` 에 `inning: String?` 추가
- [x] 7.2 `toLiveEvent` JSON 파서에 `inning` 파싱
- [x] 7.3 DEBUG 더미 이벤트들에 `inning` 채움

## 8. Android UI 필터

- [x] 8.1 `LiveGameScreen` 에 `selectedInning` 상태 + `hasManualSelection`
- [x] 8.2 `InningTabs` 가 tap 콜백 받아 갱신
- [x] 8.3 이벤트 LazyColumn 필터링 + 빈 상태 카피 동일

## 9. Cross-Platform Consistency

- [x] 9.1 선택 탭 default 동작(현재 회 자동 따라감 + manual 선택 우선) 양 플랫폼 동일
- [x] 9.2 빈 상태 카피 일치 ("해당 회 이벤트가 없습니다")
- [x] 9.3 inning 문자열 정확 일치 비교(NULL 은 어느 탭에도 포함 안 됨)

## 10. Validation

- [x] 10.1 백엔드 pytest 통과
- [x] 10.2 iOS BaseHaptic 앱 빌드 BUILD SUCCEEDED
- [x] 10.3 Android 모바일 Kotlin 컴파일 BUILD SUCCESSFUL
- [ ] 10.4 실 LIVE 경기 또는 백엔드 mock 으로 양 플랫폼 회 탭 클릭 → 이벤트 필터 확인
- [ ] 10.5 마이그레이션 이전 데이터(NULL inning) 경기에서 모든 회 탭 빈 상태 확인
- [ ] 10.6 이닝 전환 시 selectedInning 자동 갱신(manual 선택 없을 때) 확인
