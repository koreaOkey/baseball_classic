## MODIFIED Requirements

### Requirement: 팀별 컬러 프리셋
시스템은 KBO 10개 구단의 고유 컬러 프리셋을 제공해야 한다(MUST).

#### Scenario: 팀 테마 적용
- GIVEN 사용자가 관심 팀을 선택했을 때
- WHEN 앱 테마가 적용되면
- THEN 해당 팀의 primary, primaryDark, secondary, accent, gradient 색상이 적용된다

#### Scenario: 지원 구단 목록
- GIVEN 테마 시스템이 초기화될 때
- WHEN 구단 목록을 로드하면
- THEN 두산, LG, 키움, 삼성, 롯데, SSG, KT, 한화, KIA, NC 10개 구단을 지원한다

#### Scenario: 어두운 배경 위 컨트롤 상태 표시
- GIVEN primary 가 어두운 팀(KT 검정, 두산·롯데 짙은 남색 등) 테마가 적용된 상태에서
- WHEN 어두운 카드(gray900) 위에 토글 ON 상태·선택 아이콘 등 상태 표시 컨트롤을 렌더링하면
- THEN primary 대신 대비가 보장되는 `controlAccent`(= `navIndicator`)를 사용해 ON/OFF 가 시각적으로 구분된다. 밝은 primary 팀은 `controlAccent == primary` 로 기존과 동일하다.
