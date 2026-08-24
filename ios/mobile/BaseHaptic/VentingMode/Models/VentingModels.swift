import Foundation

// MARK: - RegretCandidate (아쉬운 순간 후보)

/// 분풀이 모드의 선수 후보 1명.
/// 실명·등번호·실제 외형 정보를 포함하지 않으며, 사건 문구만 노출한다.
struct RegretCandidate: Identifiable, Codable, Equatable {
    let id: String
    /// 역할·포지션 레이블 (예: "3번 타자") — 실명·등번호 금지
    let roleLabel: String
    /// 사건 문구 (예: "8회 2사 만루 삼진")
    let eventDescription: String
    // --- 서버 regret-top5 페이로드 소비용 (선택 화면 실명 표기 전용) ---
    /// 서버 항목 종류 (예: "batter" / "pitcher").
    var kind: String? = nil
    /// 팀 사이드 ("home" / "away"). 박스스코어 명단 선택에 사용.
    var teamSide: String? = nil
    /// 타순 (타자 항목). 박스스코어 조인 키.
    var battingOrder: Int? = nil
    /// 등판 순서 (투수 항목). 박스스코어 조인 키.
    var appearanceOrder: Int? = nil
    /// 박스스코어에서 해소된 실명 — 선택 화면 TargetRow에서만 노출한다.
    /// 룸·완파 화면은 절대 이 값을 읽지 않는다(VentingTarget.roleLabel/eventDescription 가 익명 방화벽).
    var playerName: String? = nil
}

// MARK: - VentingManagerOption (감독 고정 선택지)

/// 항상 마지막(6번째)에 고정되는 감독 선택지.
struct VentingManagerOption: Identifiable, Equatable {
    let id: String = "venting_manager_fixed"
    let label: String = "감독"
    let eventDescription: String
}

// MARK: - VentingGameResult (경기 결과)

enum VentingGameResult: String, Codable, Equatable {
    case win        // 승
    case loss       // 패
    case draw       // 무승부
    case canceled   // 취소
    case postponed  // 연기
    case inProgress // 경기 진행 중 (라이브 진입 전용 — 홈카드 오픈 조건에는 해당 없음)
}

// MARK: - VentingGameContext (경기 컨텍스트)

/// 분풀이 모드 오픈 판정에 사용하는 경기 입력 데이터.
/// Phase 1: 번들 JSON에서 목업 값 주입.
/// Phase 2: 백엔드 API 응답으로 교체 — `RegretCandidateProviding` 프로토콜 뒤에서만 생성.
struct VentingGameContext: Codable, Equatable {
    let gameId: String
    /// 경기 날짜 (KST, "yyyy-MM-dd")
    let gameDate: String
    let gameResult: VentingGameResult
    /// 마이팀 코드 (예: "HH")
    let myTeamId: String
    let myScore: Int
    let opponentScore: Int
    /// 선수 후보 목록 (최대 5명, 부분 표시 허용)
    let candidates: [RegretCandidate]
    /// 감독 선택지 사건 문구
    let managerEventDescription: String
    /// 라이브 진입 시 현재 이닝 라벨 (예: "7회말"). nil이면 종료 경기("최종")로 표시.
    var inningLabel: String? = nil
}
