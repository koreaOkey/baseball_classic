import ActivityKit
import Foundation

struct BaseballGameAttributes: ActivityAttributes {
    /// 경기 중 실시간으로 변경되는 데이터
    struct ContentState: Codable, Hashable {
        let homeScore: Int
        let awayScore: Int
        let inning: String
        let ball: Int
        let strike: Int
        let out: Int
        let baseFirst: Bool
        let baseSecond: Bool
        let baseThird: Bool
        let pitcher: String
        let batter: String
        let status: String       // "LIVE", "FINISHED" 등
        let lastEventType: String?
    }

    // 경기 시작 시 고정 (변경 불가)
    let gameId: String
    let homeTeam: String   // Team rawValue (예: "doosan")
    let awayTeam: String   // Team rawValue (예: "lg")
    let myTeam: String     // Team rawValue
}
