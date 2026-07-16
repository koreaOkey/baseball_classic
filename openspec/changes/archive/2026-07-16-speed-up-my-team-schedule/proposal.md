## Why

응원팀 일정 시트가 시즌 전체(3/1~시즌 종료)를 **월 단위 8회 순차 요청**(전 리그 payload, 클라이언트 필터)으로 불러와 콜드 로딩이 5~8초 걸렸다. 백엔드 HTTP 캐시도 라이브용 5초 TTL을 일정 범위에 그대로 적용해 사실상 매번 콜드였고, 클라이언트 6시간 캐시가 만료되면 기존 데이터 대신 스피너만 표시했다.

## What Changes

### A. 백엔드 팀 필터 (`backend/api/app/main.py`)
- `GET /games` 에 `team` 쿼리 파라미터 추가 (영문 코드: DOOSAN/LG/SSG/KT/KIA/NC/LOTTE/SAMSUNG/KIWOOM/HANWHA).
- `_team_filter_labels()` 가 코드를 저장 라벨 집합(영문 코드·한글 모기업·마스코트)으로 확장해 home/away 매칭. 미지원 코드는 400.
- 팀당 시즌 ~144경기 → limit 500 단일 요청으로 시즌 전체 수신 가능.

### B. 캐시 TTL 차등 (`_games_list_cache_ttl`)
- 조회 범위에 오늘(KST)이 포함되지 않으면(전부 과거/전부 미래) TTL 5초 → **600초**.
- 오늘 포함 조회는 기존 5초 유지 (라이브 스코어 신선도 보존).

### C. 클라이언트 단일 요청 + stale-while-revalidate (iOS·Android 동일)
- 시즌 시트·홈 카드 모두 `team` 단일 요청으로 전환. 실패/구서버 응답 감지 시 기존 월 청크 경로로 폴백, 클라이언트 측 isMyTeam 필터는 안전망으로 유지.
- 캐시를 TTL 무관하게 즉시 렌더(`peek`) 후 만료 시 백그라운드 갱신. 스피너는 보여줄 캐시가 전혀 없을 때만.
- iOS: 홈 카드와 시즌 시트 캐시 키 scope 분리(상호 덮어쓰기 방지).

## Capabilities

### Modified Capabilities
- `backend-api`: /games 는 팀 코드 필터를 지원해야 하며, 오늘이 포함되지 않은 일정 조회는 장기 캐시로 응답해야 한다.
- `mobile-ios` / `mobile-android`: 응원팀 일정은 단일 요청으로 로드하고, 만료된 캐시라도 즉시 표시 후 백그라운드로 갱신해야 한다.

## Impact

- 성능: 콜드 5~8초 → 단일 요청 ~0.5초, 캐시 보유 시 체감 0초. payload 전 리그 → 응원팀만(1/10).
- Backend: main.py (+테스트 2건, 92 passed) — 하위호환(team 미사용 구버전 앱 동작 불변).
- iOS: BackendGamesRepository.swift, HomeScreen.swift — 빌드 성공.
- Android: BackendGamesRepository.kt, HomeScreen.kt — compileDebugKotlin·testDebugUnitTest 통과.
- 배포: staging 푸시 `4d7e3178` → Railway 자동 배포. DB Migration 불필요.
