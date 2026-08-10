## ADDED Requirements

### Requirement: 경기 종료 후에만 regret-top5 산정

시스템은 마이팀이 참가한 경기가 `FINISHED`로 전이될 때에만 해당 경기의 regret-top5 산정을 트리거해야 한다(SHALL). 경기 진행 중에는 산정하지 않는다. 산정 대상은 **패배한 팀** 관점만이며, 무승부·취소·연기 경기는 산정하지 않는다.

#### Scenario: 패배 경기 종료 시 산정 트리거
- **WHEN** 한 경기가 `FINISHED`로 전이되고 한쪽 팀이 패배로 판정된다
- **THEN** 시스템은 패배 팀 관점의 regret 산정 작업을 out-of-band(요청 경로 밖)로 큐잉한다

#### Scenario: 무승부·취소·연기는 미산정
- **WHEN** 종료된 경기가 무승부이거나 상태가 취소/연기다
- **THEN** 시스템은 regret 산정을 수행하지 않는다

#### Scenario: 경기 진행 중 미산정
- **WHEN** 경기가 아직 `FINISHED`가 아니다
- **THEN** 시스템은 regret 산정을 트리거하지 않는다

### Requirement: 규칙/WPA 후보 선별 후 LLM 재정렬

시스템은 규칙 레이어로 후보를 결정론적으로 선별한 뒤, LLM(GPT-5.6 Luna, OpenAI 호환)이 그 후보 집합 **안에서만** TOP5를 선정하고 각 항목의 "상황" 사유문구를 생성해야 한다(SHALL). LLM은 사람을 평가하는 문장을 생성하지 않으며, 후보 집합에 없는 플레이를 만들어내지 않는다.

#### Scenario: 후보 선별
- **WHEN** 패배 경기의 중계 이벤트·박스스코어·`wpaByPlate`가 주어진다
- **THEN** 시스템은 WPA 절댓값 상위 및 득점권 실패 상황을 근거로 마이팀 후보 10~15개를 선별한다

#### Scenario: LLM은 후보 안에서만 선택
- **WHEN** LLM이 TOP5를 산정한다
- **THEN** 결과의 모든 항목은 선별된 후보 집합에 존재하며, 사유문구는 상황 묘사에 한정된다

#### Scenario: LLM 실패·비활성 시 규칙 폴백
- **WHEN** `BASEHAPTIC_VENTING_LLM_ENABLED`가 false이거나 LLM 호출이 타임아웃/오류로 실패한다
- **THEN** 시스템은 규칙 레이어 상위 5개를 결과로 사용하고, 다른 처리에 오류를 전파하지 않는다

### Requirement: 실명 표시 정책

시스템은 실명을 산정 결과 저장소에 영속시키지 않아야 한다(SHALL NOT). 캐시에는 역할 레이블·타순·이벤트 참조만 저장하고, 실명은 클라이언트가 조회 시점에 박스스코어와 결합해 선택 화면에서만 표시한다. 분풀이 방 내부와 팀 랭킹에는 실명을 노출하지 않는다.

#### Scenario: 캐시에 실명 미저장
- **WHEN** 산정 결과가 `venting_regret_cache`에 저장된다
- **THEN** 저장 레코드는 역할 레이블/타순/이벤트 참조만 포함하고 선수 실명 문자열을 포함하지 않는다

#### Scenario: 선택 화면 전용 실명 결합
- **WHEN** 클라이언트가 regret-top5를 조회한다
- **THEN** 실명은 응답의 박스스코어 참조를 통해 선택 화면 표시용으로만 결합되며, 분풀이 방·랭킹 경로에는 전달되지 않는다

### Requirement: 감독 6번째 고정 포함

regret-top5 응답은 해당 팀의 현재 감독을 6번째 고정 선택지로 포함해야 한다(SHALL).

#### Scenario: 감독 항목 부착
- **WHEN** regret-top5 응답이 생성된다
- **THEN** 응답은 선수 후보 최대 5개에 더해 감독을 6번째 고정 항목으로 포함한다

### Requirement: LLM은 요청 경로 밖에서만 호출

시스템은 LLM을 클라이언트 요청 처리 경로에서 호출하지 않아야 한다(SHALL NOT). 클라이언트 조회 endpoint는 캐시 테이블만 읽는다. LLM 호출 구간에는 DB 커넥션을 점유하지 않는다.

#### Scenario: 클라이언트 조회는 캐시만 읽음
- **WHEN** 클라이언트가 regret-top5 조회 endpoint를 호출한다
- **THEN** 시스템은 `venting_regret_cache`에서 결과를 반환하며 그 요청 경로에서 LLM을 호출하지 않는다

#### Scenario: 산정 중 커넥션 비점유
- **WHEN** out-of-band 워커가 LLM을 호출한다
- **THEN** 시스템은 데이터 조회 후 DB 커넥션을 반납한 상태로 LLM을 호출하고, 결과 저장 시 커넥션을 다시 획득한다

### Requirement: 다크 배포 플래그

시스템은 이 능력 전체를 `BASEHAPTIC_VENTING_BACKEND_ENABLED`(기본 false)로 게이트해야 하며(SHALL), LLM 호출은 `BASEHAPTIC_VENTING_LLM_ENABLED`(기본 false)로 별도 게이트해야 한다. 두 플래그가 기본 false이므로 배포 직후 기능은 비활성 상태다.

#### Scenario: 마스터 플래그 OFF
- **WHEN** `BASEHAPTIC_VENTING_BACKEND_ENABLED`가 false다
- **THEN** regret 산정 워커·조회 endpoint는 동작하지 않고 기존 운영 경로에 영향을 주지 않는다

#### Scenario: 산정 재실행 방지
- **WHEN** 한 경기의 regret 결과가 이미 캐시에 존재한다
- **THEN** 시스템은 해당 경기를 재산정하지 않는다
