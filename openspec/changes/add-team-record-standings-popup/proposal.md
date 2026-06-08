## Why

메인 화면의 상단 아이콘은 현재 기능 의미가 약한 번개 표시로 남아 있어, 사용자가 KBO 전체 팀 순위를 빠르게 확인할 진입점이 없다. 이미 크롤러가 네이버 스포츠 팀 순위 API를 수집하고 `team_record`에 저장하고 있으므로, 이 데이터를 모바일에서 전체 순위 팝업으로 노출해 활용도를 높인다.

## What Changes

- 메인 화면 오른쪽 상단 아이콘을 팀 순위를 의미하는 새 아이콘으로 교체한다.
- 백엔드에 KBO 시즌 전체 팀 순위 목록 조회 API를 추가한다.
- Android 홈 화면에서 아이콘 클릭 시 전체 10개 팀 순위를 팝업으로 표시한다.
- iOS 홈 화면에도 동일한 진입점과 전체 팀 순위 표시 흐름을 추가한다.
- 기존 `team_record` 저장 구조를 재사용하며 DB 마이그레이션은 추가하지 않는다.

## Capabilities

### New Capabilities

- 없음

### Modified Capabilities

- `team-records`: 특정 팀 기록 조회에 더해 KBO 시즌 전체 팀 순위 목록을 조회할 수 있어야 한다.

## Impact

- Backend: `team_record` 목록 조회 서비스와 공개 API 추가
- Android mobile: 홈 화면 아이콘 교체, 전체 순위 fetch, 팝업 UI 추가
- iOS mobile: 홈 화면 아이콘 교체, 전체 순위 fetch, 팝업 UI 추가
- Watch Android/iOS: 직접 UI 변경 없음, 기존 팀 기록/라이브 경기 동기화 영향 확인 필요
- Database: 기존 `team_record` 테이블과 인덱스 재사용, 마이그레이션 불필요
- Crawler: 기존 네이버 스포츠 API 수집 흐름 재사용, 신규 크롤링 엔드포인트 불필요
