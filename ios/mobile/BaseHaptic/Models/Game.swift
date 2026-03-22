import Foundation

enum GameStatus: String, Codable {
    case live = "LIVE"
    case scheduled = "SCHEDULED"
    case finished = "FINISHED"
    case canceled = "CANCELED"
    case postponed = "POSTPONED"
}

struct Game: Identifiable, Codable {
    let id: String
    let homeTeam: String
    let awayTeam: String
    let homeTeamId: Team
    let awayTeamId: Team
    let homeScore: Int
    let awayScore: Int
    let inning: String
    let status: GameStatus
    let time: String?
    let isMyTeam: Bool
    let homePitcher: Pitcher?
    let awayPitcher: Pitcher?

    init(
        id: String,
        homeTeam: String,
        awayTeam: String,
        homeTeamId: Team,
        awayTeamId: Team,
        homeScore: Int,
        awayScore: Int,
        inning: String,
        status: GameStatus,
        time: String? = nil,
        isMyTeam: Bool = false,
        homePitcher: Pitcher? = nil,
        awayPitcher: Pitcher? = nil
    ) {
        self.id = id
        self.homeTeam = homeTeam
        self.awayTeam = awayTeam
        self.homeTeamId = homeTeamId
        self.awayTeamId = awayTeamId
        self.homeScore = homeScore
        self.awayScore = awayScore
        self.inning = inning
        self.status = status
        self.time = time
        self.isMyTeam = isMyTeam
        self.homePitcher = homePitcher
        self.awayPitcher = awayPitcher
    }
}

struct Pitcher: Codable {
    let name: String
    let winStreak: Int
    let record: PitcherRecord
}

struct PitcherRecord: Codable {
    let wins: Int
    let draws: Int
    let losses: Int
}
