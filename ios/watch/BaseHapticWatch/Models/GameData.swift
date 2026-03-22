import SwiftUI

struct GameData {
    let gameId: String
    let homeTeam: String
    let awayTeam: String
    let homeScore: Int
    let awayScore: Int
    let inning: String
    let isLive: Bool
    let ballCount: Int
    let strikeCount: Int
    let outCount: Int
    let bases: BaseStatus
    let pitcher: String
    let batter: String
    let scoreDiff: Int
    let myTeamName: String
}

struct BaseStatus {
    let first: Bool
    let second: Bool
    let third: Bool

    init(first: Bool = false, second: Bool = false, third: Bool = false) {
        self.first = first
        self.second = second
        self.third = third
    }
}

// Mock data for preview
func getMockGameData() -> GameData {
    GameData(
        gameId: "1",
        homeTeam: "SSG",
        awayTeam: "KIA",
        homeScore: 5,
        awayScore: 4,
        inning: "9회말",
        isLive: true,
        ballCount: 3,
        strikeCount: 2,
        outCount: 2,
        bases: BaseStatus(first: true, third: true),
        pitcher: "KIM",
        batter: "LEE",
        scoreDiff: 1,
        myTeamName: "SSG"
    )
}
