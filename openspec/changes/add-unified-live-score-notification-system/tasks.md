## 1. 백엔드 준비

- [ ] 1.1 Supabase migration: `user_preferences.preferred_event_types` 컬럼 추가 (text[] / jsonb), 기본값 = 전체 이벤트 선택
- [ ] 1.2 사용자 preferences GET/PUT 엔드포인트에 `preferred_event_types` 필드 노출 (FastAPI 핸들러 + Pydantic 스키마)
- [ ] 1.3 push payload 표준 이벤트 타입 식별자 확인. 누락된 경우 payload에 `event_type` 필드 추가
- [ ] 1.4 미수신 이벤트 큐잉 로직(있다면) 제거. 도달 가능 시 클라이언트가 별도 동기화 경로로 최신 상태만 복원하도록 정리
- [ ] 1.5 백엔드 단위 테스트: preferences CRUD, payload 직렬화에 event_type 포함

## 2. iOS 폰 (mobile-ios)

- [ ] 2.1 설정 화면에 "선택한 이벤트만 알림" 멀티 셀렉트 섹션 추가 (SCORE/HOMERUN/HIT/WALK/STEAL 등)
- [ ] 2.2 `preferred_event_types`를 백엔드와 동기화하고 로컬 저장(UserDefaults) 후 워치와 sync
- [ ] 2.3 푸시 노티 핸들러에 워치 활성 가드 추가: `WCSession.default.isPaired && WCSession.default.isReachable`이면 헤드업/햅틱 suppress, Live Activity·인앱 갱신만 수행
- [ ] 2.4 폰 foreground 상태에서 헤드업 노티 suppress 확인 (`willPresent`에서 빈 옵션 반환)
- [ ] 2.5 Live Activity가 활성인 동안 같은 정보의 push 헤드업 노출 여부 실기기 테스트 → 시각적 노이즈 발생 시 suppress 가드 추가

## 3. Android 폰 (mobile-android)

- [ ] 3.1 설정 화면에 "선택한 이벤트만 알림" 멀티 셀렉트 추가, `preferred_event_types` 저장
- [ ] 3.2 폰 라이브 스코어 ongoing notification 신규 구현: 같은 notification id로 in-place replace, `setOngoing(true)`, 잠금화면 가시성 채널 설정
- [ ] 3.3 ongoing 콘텐츠에 스코어·이닝·BSO·최근 이벤트 1개 라인 포함
- [ ] 3.4 LIVE 진입/종료 시 ongoing notification 자동 게시/제거 라이프사이클 연결
- [ ] 3.5 푸시 핸들러에 워치 노드 페어링·연결 가드 추가 (`Wearable.NodeClient`로 연결된 워치 노드 확인), 있으면 햅틱/헤드업 suppress
- [ ] 3.6 폰 foreground 시 헤드업 노티 suppress 동작 확인

## 4. iOS 워치 (watch-ios)

- [ ] 4.1 워치 ongoing 노티 패턴 도입: 동일 식별자로 `UNNotificationRequest` 발행해 in-place replace 검증
- [ ] 4.2 라이브 경기 데이터 수신 시 ongoing 콘텐츠를 최신 스코어·이닝·BSO·최근 이벤트 1개로 갱신
- [ ] 4.3 long-look expand 가드: payload의 `event_type`이 `preferred_event_types`에 포함된 경우만 expand 표시 + 햅틱 발화. 미선택 이벤트는 ongoing 갱신만
- [ ] 4.4 foreground 시 노티 suppress(`willPresent` 빈 옵션) 정책 유지 확인 — 회귀 없도록 테스트
- [ ] 4.5 워치 독립 APNs 경로에서도 동일 가드 적용 (기존 워치 독립 동기화와 호환)

## 5. Android 워치 (watch-android)

- [ ] 5.1 `MainActivity.kt` ongoing notification 콘텐츠를 정적 "경기 관람 중" → 스코어·이닝·BSO·최근 이벤트 동적 갱신으로 교체
- [ ] 5.2 `OngoingActivity` 갱신 트리거를 라이브 데이터 변경 시점에 연결 (같은 notification id 유지)
- [ ] 5.3 `DataLayerListenerService.kt`의 이벤트 핸들러에 long-look expand 가드 추가: `preferred_event_types`에 포함된 이벤트만 expand + 햅틱 + `wakeScreenForEvent` 호출. 미선택 이벤트는 ongoing 갱신만
- [ ] 5.4 워치 sync로 `preferred_event_types` 수신·로컬 저장(SharedPreferences)
- [ ] 5.5 LIVE 진입/종료 시 ongoing notification 생성·제거 라이프사이클 점검 (기존 onCreate/onDestroy 흐름 유지)
- [ ] 5.6 `apps/watch/app/src/main/java/com/basehaptic/watch/tile/GameTileService.kt` 및 관련 파일 삭제 (`tile/` 디렉터리 정리)
- [ ] 5.7 `apps/watch/app/src/main/AndroidManifest.xml`에서 `GameTileService` service 선언·intent-filter 제거
- [ ] 5.8 `apps/watch/app/build.gradle.kts`에서 `androidx.wear.tiles` 의존성 제거 (다른 곳에서 안 쓰는 경우)
- [ ] 5.9 `DataLayerListenerService.kt`의 `TileService.getUpdater(...).requestUpdate(...)` 호출 제거

## 6. 정책 검증·QA

- [ ] 6.1 시나리오 2 (P-fg + W-home): 폰은 인앱 갱신만, 워치 ongoing 갱신 + 선택 이벤트 햅틱·expand 확인
- [ ] 6.2 시나리오 3 (P-fg + W-off): 폰 인앱 갱신만, 워치 햅틱·노티 발화 안 함 확인
- [ ] 6.3 시나리오 4 (P-bg + W-fg): 폰 Live Activity/ongoing notification 갱신, 폰 헤드업·햅틱 suppress, 워치 마스코트 애니메이션 정상 재생
- [ ] 6.4 시나리오 5 (P-bg + W-home): 양쪽 ongoing 갱신, 햅틱은 워치만, 선택 이벤트만 양쪽 expand 표시 확인
- [ ] 6.5 시나리오 6 (P-bg + W-off): 폰 Live Activity/ongoing notification만 갱신, 워치 발화 안 함
- [ ] 6.6 시나리오 8 (P-off + W-home): 워치 APNs 직접 수신으로 ongoing 갱신 + 햅틱 동작 확인
- [ ] 6.7 시나리오 9 (P-off + W-off): 깨어났을 때 ongoing이 최신 스코어 + 최근 이벤트 1개만 표시. 누락된 이벤트 개별 재발화 없음
- [ ] 6.8 노티 스택 중복 검증: 이벤트 다수 발생해도 라이브 스코어 노티가 동일 id로 replace되어 한 개만 유지되는지 확인 (iOS·Android 워치/폰 각각)
- [ ] 6.9 expand 후 약 3초 뒤 ongoing 복귀 확인 (워치/폰)
- [ ] 6.10 마스코트 펭귄 애니메이션이 워치 앱 foreground에서만 정상 재생되는지 회귀 확인 — 노티에 "영상보기" 등 액션 버튼 없는지 확인

## 7. 문서·메모리 정리

- [ ] 7.1 `openspec/specs/` 본 change archive 후 갱신 (mobile-android/mobile-ios/watch-android/watch-ios/realtime/live-score-notification)
- [ ] 7.2 메모리 `project_2026-05-13_live_score_noti_design.md`에 구현 완료·열린 항목 해소 결과 반영
- [ ] 7.3 릴리즈 노트(iOS/Android)에 "라이브 스코어 노티 + 선택 이벤트 알림" 항목 추가
- [ ] 7.4 타일 제거 결정 archive 기록
