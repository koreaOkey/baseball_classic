## ADDED Requirements

### Requirement: State Machine 기반 Agent 진입점
시스템은 `python main.py` 단일 명령으로 Agent 전체 Workflow를 실행할 수 있어야 한다(SHALL). 엔트리포인트는 argparse로 `--mode`, `--player`, `--concept_hint` 세 가지 인자를 받고, 인자 없이 실행되면 `--mode auto`를 기본값으로 적용한다.

#### Scenario: 인자 없이 기본 실행
- **WHEN** 사용자가 `python main.py`를 실행한다
- **THEN** Agent는 `--mode auto`로 시작해 선수 자율 선정 → 팩 생성까지 완주한다

#### Scenario: 대상 지정 실행
- **WHEN** 사용자가 `python main.py --mode player_pack --player 김혜성`을 실행한다
- **THEN** Agent는 `CHOOSE_PLAYER` Node를 스킵하고 지정된 선수로 Research부터 시작한다

#### Scenario: 컨셉 힌트 포함 실행
- **WHEN** 사용자가 `python main.py --mode theme_pack --concept_hint "봄 파스텔"`을 실행한다
- **THEN** Agent는 카탈로그 gap 분석에 힌트를 반영해 테마만 생성한다

### Requirement: Supervisor 자율 mode 판단
Agent는 입력 mode가 `auto`인 경우 Supervisor의 PLAN Node에서 `player_pack` / `theme_pack` / `event_pack` 중 하나를 자율 결정해야 한다(SHALL). 판단 근거는 웹 검색을 통한 최신 KBO 이슈와 기존 카탈로그 gap 분석을 함께 사용한다.

#### Scenario: 선수 이슈가 있을 때 player_pack 선택
- **WHEN** 웹 검색 결과 특정 선수에게 최근 이슈(복귀·기록·이적)가 감지된다
- **THEN** Supervisor는 `player_pack` 모드로 진입해 Research 체인부터 실행한다

#### Scenario: 카탈로그 공백이 두드러질 때 theme_pack 선택
- **WHEN** 카탈로그 gap 분석이 특정 축(계절·무드)의 심각한 부재를 반환한다
- **THEN** Supervisor는 `theme_pack` 모드로 진입해 해당 gap을 채우는 테마만 생성한다

### Requirement: Research 체인과 저작권 필터링
Agent는 선수 대상 팩 생성 시 `player_database` Tool 조회와 `web_search` Tool 호출을 반드시 수행해야 한다(SHALL). `EXTRACT_TRAITS` Node는 저작권 금지어(얼굴·초상·로고 관련 키워드)를 제거하고 사실 정보(등번호·팀컬러·포지션 실루엣·플레이 모션·별명)만 downstream으로 전달해야 한다(MUST).

#### Scenario: 선수 리서치 후 시각 모티프 추출
- **WHEN** Agent가 선수 이정후에 대한 Research를 완료한다
- **THEN** 시각 모티프로 `[CF 실루엣, 11번, 샌프 오렌지, 배트 스윙, 바람 이펙트]`만 추출하고 얼굴·외모 관련 키워드는 배제한다

#### Scenario: 저작권 금지어가 포함된 프롬프트 차단
- **WHEN** LLM이 시각 모티프 추출 중 "이정후의 얼굴"과 같은 초상 관련 문구를 반환한다
- **THEN** Agent는 해당 문구를 필터링하고 사실 모티프로 재작성한다

### Requirement: Theme 서브그래프 (스플래시·홈·잠금 + 디자인 토큰)
Agent는 테마 팩 생성 시 스플래시 배경, 홈 배경, 잠금 화면 배경 3종을 생성해야 하며(SHALL), 생성 결과에서 대표 팔레트를 추출해 야구봄 기존 디자인 토큰 스키마와 호환되는 `tokens.json`을 출력해야 한다(MUST).

#### Scenario: 테마 3종 생성 성공
- **WHEN** Agent가 Theme 서브그래프를 실행 완료한다
- **THEN** `packs/<pack_id>/theme/` 하위에 `splash.png`, `home_bg.png`, `lock.png`, `tokens.json` 4개 파일이 저장된다

#### Scenario: 토큰 JSON이 기존 스키마와 호환됨
- **WHEN** Agent가 `tokens.json`을 생성한다
- **THEN** 필드명은 `color.primary`, `color.accent`, `color.surface`, `color.onSurface` 등 기존 야구봄 디자인 토큰 네이밍을 따른다

#### Scenario: 테마 검증 실패 시 재생성 루프
- **WHEN** 생성된 `splash.png`가 대비 검증(WCAG)에 실패한다
- **THEN** Agent는 프롬프트를 보정해 최대 3회까지 재생성하고, 3회 모두 실패하면 해당 테마 요소를 `status: "failed_after_retry"`로 기록한다

### Requirement: Event 서브그래프 (이벤트별 히어로 스틸)
Agent는 이벤트 팩 생성 시 정의된 이벤트 타입(HR/HIT/STEAL/WALK/STRIKEOUT 등)별로 독립적인 히어로 스틸과 `meta.json` 힌트를 생성해야 한다(SHALL). 각 이벤트는 독립적으로 재시도·SKIP 처리되며 일부 실패가 전체 팩 실패로 이어지지 않아야 한다(MUST).

#### Scenario: 이벤트별 히어로 스틸 저장
- **WHEN** Agent가 HR, HIT, STEAL, WALK, STRIKEOUT 5개 이벤트 생성을 완료한다
- **THEN** `packs/<pack_id>/events/<EVENT>/` 하위에 각각 `hero.png`와 `meta.json`이 저장된다

#### Scenario: meta.json 힌트 필드 포함
- **WHEN** Agent가 이벤트 히어로 스틸을 생성한다
- **THEN** `meta.json`은 `event`, `scene_description`, `focal_point`, `dominant_colors`, `mood_tags` 필드를 포함한다

#### Scenario: 이벤트 3회 실패 시 SKIP
- **WHEN** 특정 이벤트(예: STRIKEOUT) 생성이 얼굴 감지·대비 실패로 3회 연속 실패한다
- **THEN** Agent는 해당 이벤트를 `status: "failed_after_retry"`로 기록하고 다음 이벤트 생성을 계속한다

### Requirement: 저작권 가드 Tool `face_logo_detector`
Agent는 생성된 모든 이미지에 대해 `face_logo_detector` Tool로 얼굴과 구단 로고를 검사해야 한다(SHALL). 실제 얼굴이 감지되거나 구단 공식 로고가 재현된 경우 해당 결과를 리젝트하고 추상화 레벨을 상향해 재생성해야 한다(MUST).

#### Scenario: 얼굴 감지 시 추상화 상향
- **WHEN** `face_logo_detector`가 생성 이미지에서 사람 얼굴을 감지한다
- **THEN** Agent는 프롬프트에 `"silhouette only, pictogram style, no human face"` 지시를 강화하고 재생성한다

#### Scenario: 구단 로고 감지 시 리젝트
- **WHEN** `face_logo_detector`가 구단 공식 로고 요소를 감지한다
- **THEN** Agent는 해당 이미지를 저장하지 않고 재생성한다

### Requirement: Tool 7종 사용
Agent는 다음 7개 Tool을 Workflow 내에서 사용해야 한다(SHALL): `player_database`, `web_search`, `catalog_analyzer`, `dalle_generate`, `face_logo_detector`, `color_extractor`, `pack_writer`.

#### Scenario: Static Storage Tool 호출
- **WHEN** Agent가 Research를 시작한다
- **THEN** `player_database` 또는 `catalog_analyzer` 중 최소 하나를 호출해 로컬 저장소를 조회한다

#### Scenario: 웹 검색 Tool 호출
- **WHEN** Agent가 트렌드 또는 선수 최근 이슈를 조사한다
- **THEN** `web_search` Tool을 호출해 외부 정보를 수집한다

### Requirement: 조건부 분기와 graceful degradation
Agent는 최소 9축의 조건부 분기를 가져야 한다(SHALL): PLAN 3-way, needs_player, needs_catalog, Theme 3종 검증 실패 각각, Event 얼굴 감지 추상화 루프, Event 3회 실패 SKIP, MERGE graceful degradation.

#### Scenario: 일부 이벤트 실패 시 팩 저장 계속
- **WHEN** 5개 이벤트 중 4개가 성공하고 1개가 SKIP된다
- **THEN** Agent는 성공한 4개와 실패 기록 1개를 모두 manifest에 포함해 팩을 저장한다

#### Scenario: 테마 요소 전체 실패 시 팩 생성 중단
- **WHEN** Theme 서브그래프에서 splash·home_bg·lock 3종 모두 재시도 한도를 초과한다
- **THEN** Agent는 `player_pack` 또는 `theme_pack` 모드에서 팩 저장을 중단하고 종료 코드 2(치명적 실패)를 반환한다

### Requirement: Pack 디렉토리 산출물
Agent는 생성 완료 시 `packs/pack_YYYYMMDD_<seq>_<label>/` 구조의 디렉토리를 산출해야 한다(SHALL). 디렉토리 최상위에는 `manifest.json`이 있어야 하며 `pack_id`, `mode`, `player`, 생성된 이벤트 목록, 총 비용, 재시도 카운트를 포함해야 한다(MUST).

#### Scenario: manifest.json 구조
- **WHEN** Agent가 팩 저장을 완료한다
- **THEN** `manifest.json`은 `pack_id`, `mode`, `player`, `theme_bundle`, `generated_events`, `total_cost_usd`, `retry_counts` 필드를 포함한다

#### Scenario: 팩 ID 생성 규칙
- **WHEN** Agent가 2026-04-23에 이정후 첫 팩을 생성한다
- **THEN** `pack_id`는 `pack_20260423_01_leejungho` 형식을 따른다

### Requirement: stdout JSON progress event
Agent는 각 주요 Node 완료 시 stdout에 한 줄 JSON progress event를 방출해야 한다(SHALL). 이벤트는 최소 `ts`, `node`, `status` 필드를 포함해야 하며, 종료 코드는 `0=성공`, `1=부분 실패`, `2=치명적 실패`로 매핑된다.

#### Scenario: progress event 방출
- **WHEN** Agent의 `RESEARCH_PLAYER` Node가 완료된다
- **THEN** stdout에 `{"ts": "HH:MM:SS", "node": "RESEARCH_PLAYER", "status": "done", "player": "이정후"}` 형식의 JSON 한 줄이 출력된다

#### Scenario: 부분 실패 종료 코드
- **WHEN** 이벤트 1개가 SKIP되고 나머지는 성공해 팩이 저장된다
- **THEN** Agent는 종료 코드 1을 반환한다

### Requirement: 비용 상한과 재시도 정책
Agent는 이미지 생성 시 팩당 재시도 상한 3회를 적용해야 하며(SHALL), 실시간 비용을 누적 추적해 `manifest.json`의 `total_cost_usd` 필드에 기록해야 한다(MUST).

#### Scenario: 재시도 상한 적용
- **WHEN** 특정 이벤트 생성이 검증 실패로 3회 재시도된다
- **THEN** 4번째 시도는 시도하지 않고 해당 이벤트를 SKIP 처리한다

#### Scenario: 비용 추적
- **WHEN** Agent가 DALL-E 3 호출 8회, GPT-4o Vision 호출 6회를 실행한다
- **THEN** `total_cost_usd`는 실제 호출 단가를 누적한 값(예: `0.36`)으로 기록된다

### Requirement: Non-Goals 명시
본 Agent는 영상·애니메이션 생성을 수행하지 않으며(MUST NOT), 야구봄 앱 또는 Supabase와의 실시간 연동을 포함하지 않는다(MUST NOT). 이벤트 `meta.json`에 영상화 후속 작업용 힌트(`scene_description`, `focal_point`, `mood_tags`)만 남긴다.

#### Scenario: 영상 생성 호출 없음
- **WHEN** Agent의 Workflow가 완료된다
- **THEN** 산출물에는 정적 이미지(PNG)와 JSON만 존재하고 비디오·애니메이션 포맷은 포함되지 않는다

#### Scenario: 영상화 힌트만 제공
- **WHEN** Agent가 HR 이벤트 히어로 스틸을 저장한다
- **THEN** `meta.json`에 `mood_tags: ["impact", "flash", "slow-mo-friendly"]` 같은 후속 영상화 힌트가 포함된다
