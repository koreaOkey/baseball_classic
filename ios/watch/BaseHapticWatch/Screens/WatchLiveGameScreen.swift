import SwiftUI

struct WatchLiveGameScreen: View {
    let gameData: GameData

    private var isGameFinished: Bool {
        gameData.inning.contains("경기 종료") || gameData.inning.lowercased().contains("finished")
    }

    var body: some View {
        VStack(spacing: 0) {
            // Score Card
            HStack(alignment: .center) {
                // Away team
                ScoreSide(team: gameData.awayTeam, score: gameData.awayScore)

                // Inning
                VStack(spacing: 2) {
                    if isGameFinished {
                        Text(gameData.inning)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(WatchColors.orange500)
                    } else {
                        Text(gameData.inning)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(WatchColors.orange500)
                        Text(inningHalfIcon(gameData.inning))
                            .font(.system(size: 8))
                            .foregroundColor(WatchColors.orange500)
                    }
                }
                .frame(minWidth: 40, minHeight: 30)
                .background(Color.white.opacity(0.05))
                .cornerRadius(10)
                .padding(.horizontal, 4)

                // Home team
                ScoreSide(team: gameData.homeTeam, score: gameData.homeScore)
            }
            .padding(.horizontal, 8)
            .padding(.top, 4)

            Spacer().frame(height: 8)

            // BSO + Base Diamond
            HStack(spacing: 8) {
                BaseDiamond(bases: gameData.bases)

                VStack(alignment: .leading, spacing: 3) {
                    CountIndicator(label: "B", current: gameData.ballCount, max: 3, activeColor: WatchColors.green500)
                    CountIndicator(label: "S", current: gameData.strikeCount, max: 2, activeColor: WatchColors.orange500)
                    CountIndicator(label: "O", current: gameData.outCount, max: 2, activeColor: WatchColors.red500)
                }
            }

            Spacer().frame(height: 6)

            // Player info
            Text("P \(gameData.pitcher)  B \(gameData.batter)")
                .font(.system(size: 10))
                .foregroundColor(.white.opacity(0.62))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(WatchColors.gray950)
    }

    private func inningHalfIcon(_ inning: String) -> String {
        if inning.contains("TOP") || inning.contains("초") { return "▲" }
        if inning.contains("BOT") || inning.contains("말") { return "▼" }
        return ""
    }
}

// MARK: - ScoreSide
private struct ScoreSide: View {
    let team: String
    let score: Int

    var body: some View {
        VStack(spacing: 0) {
            Text("\(score)")
                .font(.system(size: 28, weight: .black))
                .foregroundColor(.white)
                .minimumScaleFactor(0.7)

            Text(team.uppercased())
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.white.opacity(0.76))
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - CountIndicator
private struct CountIndicator: View {
    let label: String
    let current: Int
    let max: Int
    let activeColor: Color

    var body: some View {
        HStack(spacing: 3) {
            Text(label)
                .font(.system(size: 9, weight: .black))
                .foregroundColor(.white.opacity(0.35))
                .frame(width: 12)

            HStack(spacing: 3) {
                ForEach(0..<max, id: \.self) { index in
                    Circle()
                        .fill(index < current ? activeColor : activeColor.opacity(0.2))
                        .frame(width: 7, height: 7)
                }
            }
        }
    }
}

// MARK: - BaseDiamond
private struct BaseDiamond: View {
    let bases: BaseStatus

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 2) {
                BaseCell(isOccupied: bases.second)
                BaseCell(isOccupied: bases.first)
            }
            HStack(spacing: 2) {
                BaseCell(isOccupied: bases.third)
                BaseCell(isOccupied: false, isHome: true)
            }
        }
        .rotationEffect(.degrees(45))
        .frame(width: 30, height: 30)
    }
}

private struct BaseCell: View {
    let isOccupied: Bool
    var isHome: Bool = false

    var body: some View {
        Rectangle()
            .fill(isOccupied ? WatchColors.yellow400 : (isHome ? Color.white.opacity(0.08) : WatchColors.gray800))
            .frame(width: 10, height: 10)
            .cornerRadius(2)
    }
}

#Preview {
    WatchLiveGameScreen(gameData: getMockGameData())
}
