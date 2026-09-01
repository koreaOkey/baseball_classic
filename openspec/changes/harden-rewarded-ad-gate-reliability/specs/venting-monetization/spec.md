# venting-monetization — 광고 게이트 신뢰성 델타

## ADDED Requirements

### Requirement: Rewarded 광고 종단 콜백 신뢰성
Rewarded 광고 매니저는 요청 1건당 종단 콜백(onComplete)을 정확히 한 번만 MUST
전달하고, 어떤 경로(콜백 유실 포함)로든 로딩 상태가 영구히 남지 않아야 한다.

#### Scenario: 이중 delegate 콜백 무해화
- GIVEN 광고 present 실패와 dismiss 콜백이 겹쳐 도착했을 때
- WHEN 매니저가 종단 콜백을 전달하면
- THEN 첫 번째 판정만 유효하고 두 번째는 무시된다(게이트 continuation 이중 resume 없음)

#### Scenario: 로드 콜백 유실 시 워치독 마감
- GIVEN 광고 로드 콜백이 60초 내에 도착하지 않았을 때
- WHEN 워치독이 발화하면
- THEN loadFailed(무료 허용 정책)로 마감하고 로딩 상태·스피너를 해제하며, 그 후 늦게 도착한 광고는 표시하지 않는다

#### Scenario: 광고 표시 중 중복 요청 busy 처리
- GIVEN 광고가 화면에 표시되고 있는 동안
- WHEN 새 광고 요청이 들어오면
- THEN busy로 거부하고 진행 중인 요청의 콜백 경로는 손상되지 않는다

### Requirement: 광고 중도 이탈 사용자 피드백
완파 화면에서 재도전이 거부(denied)되면 무반응 대신 사용자에게 사유를 MUST
안내한다.

#### Scenario: 보상 전 이탈 안내
- GIVEN 사용자가 "광고 보고 재도전하기"를 탭한 뒤 보상 전에 광고를 닫았을 때
- WHEN 게이트가 denied를 반환하면
- THEN "광고를 끝까지 보면 재도전할 수 있어요" 안내를 표시하고 재시도를 허용한다(iOS 캡션·Android Toast)
