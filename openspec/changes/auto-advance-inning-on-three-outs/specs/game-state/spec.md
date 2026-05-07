# Game State Specification (delta)

## MODIFIED Requirements

### Requirement: 3아웃 시 이닝 자동 전환
시스템은 3아웃 발생 시 BSO/주자를 0으로 리셋하고 KBO 룰에 따라 다음 이닝으로 자동 전환하거나 경기를 종료해야 한다(MUST). 이 결정은 응답 시점이 아닌 스냅샷 ingest 시점에 DB 에 반영되어야 한다.

규칙:
- 정규 9회까지(`MAX_REGULATION_INNING=9`)는 3아웃 시 무조건 다음 회로 전진한다(N회초 → N회말, N회말 → N+1회초).
- 9회 이상에서 다음 우선순위로 종료 판정:
  - **N회초 종료 + 홈팀 리드** → N회말 생략, 종료(`should_finish=true`).
  - **N회말 종료 + 점수차** → 종료.
  - **연장 11회말 종료**(`MAX_EXTRA_INNING=11`)는 점수 무관 종료. 동점이면 무승부.
- 종료 시 `inning` 텍스트는 동점이면 `"경기 종료 (무승부)"`, 그 외에는 `"경기 종료"` 로 강제 라벨링한다.

#### Scenario: 정규 이닝 즉시 전환
- GIVEN 라이브 경기에서 inning 이 `"3회초"` 이고 BSO 가 `0,0,2` 일 때
- WHEN `out=3` 인 스냅샷이 들어오면
- THEN DB 에 `inning="3회말"`, `ball=strike=out=0`, 주자 모두 `false` 로 저장된다

#### Scenario: BSO 만 리셋된 폴링도 transition 으로 전환
- GIVEN 직전 스냅샷이 `inning="5회말", out=2`
- WHEN 새 스냅샷이 `inning="5회말", out=0` 으로 오고 inning 텍스트가 동일하면
- THEN 직전 회의 3아웃으로 간주하여 `inning="6회초"` 로 전환한다

#### Scenario: 이닝 텍스트 후퇴 방지
- GIVEN 백엔드가 `"3회말"` 로 advance 한 직후
- WHEN 네이버가 `inning="3회초"` 로 옛 텍스트를 다시 보내오면
- THEN inning 은 `"3회말"` 로 보존하고 BSO/score 등 나머지 필드는 페이로드 값으로 갱신한다

#### Scenario: 9회초 홈팀 리드 시 말 생략 종료
- GIVEN inning 이 `"9회초"` 이고 홈 5 - 어웨이 2 일 때
- WHEN `out=3` 스냅샷이 들어오면
- THEN status 는 `FINISHED`, inning 은 `"경기 종료"` 로 저장된다

#### Scenario: 9회초 동점은 9회말 진행
- GIVEN inning 이 `"9회초"` 이고 동점일 때
- WHEN `out=3` 스냅샷이 들어오면
- THEN status 는 `LIVE`, inning 은 `"9회말"` 로 진행한다

#### Scenario: 9회말 동점 → 연장 진입
- GIVEN inning 이 `"9회말"` 이고 동점일 때
- WHEN `out=3` 스냅샷이 들어오면
- THEN status 는 `LIVE`, inning 은 `"10회초"` 로 진행한다

#### Scenario: 9회말 점수차 종료
- GIVEN inning 이 `"9회말"` 이고 점수차일 때
- WHEN `out=3` 스냅샷이 들어오면
- THEN status 는 `FINISHED`, inning 은 `"경기 종료"` 로 저장된다

#### Scenario: 11회말 무승부 라벨
- GIVEN inning 이 `"11회말"` 이고 동점일 때
- WHEN `out=3` 스냅샷이 들어오면
- THEN status 는 `FINISHED`, inning 은 `"경기 종료 (무승부)"` 로 저장된다

#### Scenario: 11회말 점수차 종료
- GIVEN inning 이 `"11회말"` 이고 점수차일 때
- WHEN `out=3` 스냅샷이 들어오면
- THEN status 는 `FINISHED`, inning 은 `"경기 종료"` 로 저장된다
