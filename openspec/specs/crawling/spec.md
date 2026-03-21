# Crawling Specification

## Purpose
네이버 스포츠 API에서 KBO 실시간 중계 데이터를 수집하여 백엔드로 전달하는 데이터 수집 파이프라인.

## Requirements

### Requirement: 일일 경기 스케줄 수집
시스템은 매일 KST 00:05에 당일 KBO 경기 스케줄을 자동으로 조회해야 한다(MUST).

#### Scenario: 정상 스케줄 조회
- GIVEN 당일 KBO 경기가 예정되어 있을 때
- WHEN 디스패처가 일일 스케줄을 조회하면
- THEN 모든 예정 경기 목록을 반환한다
- AND 각 경기의 시작 시간과 gameId를 포함한다

#### Scenario: 경기 없는 날
- GIVEN 당일 예정된 경기가 없을 때
- WHEN 디스패처가 스케줄을 조회하면
- THEN 빈 목록을 반환하고 대기 상태를 유지한다

### Requirement: 중계 가용성 감지
시스템은 경기 시작 180분 전부터 1분 간격으로 릴레이 가용 여부를 확인해야 한다(MUST).

#### Scenario: 릴레이 시작 감지
- GIVEN 경기 시작 전 대기 중일 때
- WHEN 네이버 릴레이 API에서 데이터가 반환되면
- THEN 해당 경기의 크롤러 프로세스를 즉시 시작한다

#### Scenario: 프리뷰 라인업 사전 감지
- GIVEN 릴레이가 아직 시작되지 않았을 때
- WHEN 프리뷰 라인업 API에서 데이터가 확인되면
- THEN 릴레이 시작이 임박했음을 인지하고 폴링 간격을 유지한다

### Requirement: 실시간 릴레이 크롤링
시스템은 10~15초 간격으로 릴레이 데이터를 폴링해야 한다(MUST).

#### Scenario: 정상 데이터 수집
- GIVEN 경기가 진행 중일 때
- WHEN 크롤러가 릴레이 API를 폴링하면
- THEN 새로운 이벤트를 파싱하여 백엔드 인제스트 API로 전송한다

#### Scenario: 경기 종료 감지
- GIVEN 크롤러가 릴레이를 수집 중일 때
- WHEN 경기 상태가 FINISHED로 변경되면
- THEN 최종 스냅샷을 전송하고 크롤러를 종료한다

### Requirement: 이벤트 타입 분류
크롤러는 릴레이 텍스트를 구조화된 이벤트 타입으로 분류해야 한다(MUST).

#### Scenario: 이벤트 분류
- GIVEN 릴레이 텍스트가 수신되었을 때
- WHEN 이벤트를 파싱하면
- THEN BALL, STRIKE, WALK, OUT, HIT, HOMERUN, SCORE, DOUBLE_PLAY, TRIPLE_PLAY, SAC_FLY_SCORE, TAG_UP_ADVANCE, STEAL, PITCHER_CHANGE, HALF_INNING_CHANGE, OTHER 중 하나로 분류한다

### Requirement: 데이터 소스 API
크롤러는 네이버 스포츠 API를 데이터 소스로 사용해야 한다(MUST).

#### Scenario: 경기 메타데이터 조회
- GIVEN 경기 ID가 주어졌을 때
- WHEN `https://api-gw.sports.naver.com/schedule/games/{gameId}` 를 호출하면
- THEN 경기 메타 정보(팀, 시간, 상태 등)를 반환한다

#### Scenario: 텍스트 중계 조회
- GIVEN 경기가 진행 중일 때
- WHEN `https://api-gw.sports.naver.com/schedule/games/{gameId}/relay?inning={1~9}` 를 이닝별로 호출하면
- THEN 해당 이닝의 텍스트 중계 데이터를 반환한다

### Requirement: 중계 텍스트 이벤트 유형
크롤러는 네이버 중계 텍스트의 type 값으로 이벤트 유형을 구분해야 한다(MUST).

#### Scenario: 이벤트 유형 매핑
- GIVEN 중계 텍스트를 파싱할 때
- WHEN type 값을 확인하면
- THEN type=1(투구 이벤트), type=2(선수 교체), type=8(타자 소개), type=13(타석 결과)으로 구분한다

### Requirement: 수집 항목
크롤러는 각 타석별 상세 데이터를 수집해야 한다(MUST).

#### Scenario: 타석별 데이터 수집
- GIVEN 릴레이 데이터를 파싱할 때
- WHEN 각 타석을 처리하면
- THEN 이닝/초말, 공격/수비 팀, 타자, 투수, 볼/스트라이크 누적, 타석 결과, 대타 투입, 투수 교체를 수집한다
