import SwiftUI

struct HomeScreen: View {
    let selectedTeam: Team
    let todayGames: [Game]
    let activeTheme: ThemeData?
    let syncedGameId: String?
    let onSelectGame: (Game) -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var teamRecordStats: TeamRecordStats?
    @State private var upcomingGames: [UpcomingGameSchedule] = []

    private var primaryColor: Color {
        activeTheme?.colors.primary ?? teamTheme.primary
    }

    private var games: [Game] {
        sortHomeGames(todayGames)
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                headerSection
                quickStatsSection
                gamesListHeader
                gamesListSection
                upcomingGamesSection
                Spacer().frame(height: 80)
            }
        }
        .background(AppColors.gray950)
        .task(id: selectedTeam) {
            await loadTeamRecord()
            await loadUpcomingGames()
        }
    }

    // MARK: - Header
    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 12) {
                    TeamLogo(team: selectedTeam, size: 56)
                    VStack(alignment: .leading) {
                        Text("BaseHaptic Live")
                            .font(.system(size: 12))
                            .foregroundColor(.white.opacity(0.7))
                        Text(selectedTeam.teamName)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                Spacer()
                Circle()
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 48, height: 48)
                    .overlay(
                        Image(systemName: "bolt.fill")
                            .foregroundColor(.white)
                    )
            }

            Spacer().frame(height: 16)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Image(systemName: "calendar")
                        .foregroundColor(.white)
                        .font(.system(size: 16))
                    Text(todayDateString)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                }
                Text("오늘의 경기 \(games.filter { isPlayableGameStatus($0.status) }.count)개")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.15))
            .cornerRadius(16)
        }
        .padding(24)
        .padding(.bottom, 32)
        .background(
            LinearGradient(
                colors: [primaryColor, primaryColor.opacity(0.9)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    // MARK: - Quick Stats
    private var quickStatsSection: some View {
        HStack(spacing: 12) {
            StatCard(value: "5승", label: "최근 10경기", valueColor: AppColors.green500)
            StatCard(value: rankingText, label: "현재 순위", valueColor: AppColors.yellow500)
            StatCard(value: wraText, label: "승률", valueColor: AppColors.blue500)
        }
        .padding(.horizontal, 24)
        .offset(y: -16)
    }

    // MARK: - Games List
    private var gamesListHeader: some View {
        HStack {
            Text("오늘의 경기")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
        .padding(.vertical, 16)
    }

    private var gamesListSection: some View {
        ForEach(games) { game in
            let isWatchSynced = game.status == .live && syncedGameId == game.id
            GameCard(game: game, primaryColor: primaryColor, isWatchSynced: isWatchSynced) {
                onSelectGame(game)
            }
        }
    }

    // MARK: - Upcoming Games
    @ViewBuilder
    private var upcomingGamesSection: some View {
        if !upcomingGames.isEmpty {
            Spacer().frame(height: 32)
            Text("다가오는 경기")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.vertical, 16)

            ForEach(upcomingGames) { upcoming in
                UpcomingGameCard(selectedTeam: selectedTeam, upcoming: upcoming)
            }
        }
    }

    // MARK: - Helpers
    private var rankingText: String {
        teamRecordStats?.ranking.map { "\($0)위" } ?? "-"
    }

    private var wraText: String {
        teamRecordStats?.wra.map { String(format: "%.3f", $0) } ?? "-.--"
    }

    private var todayDateString: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M월 d일 (E)"
        return formatter.string(from: Date())
    }

    private func loadTeamRecord() async {
        guard selectedTeam != .none else {
            teamRecordStats = nil
            return
        }
        teamRecordStats = await BackendGamesRepository.shared.fetchTodayTeamRecordCached(selectedTeam: selectedTeam)
    }

    private func loadUpcomingGames() async {
        guard selectedTeam != .none else {
            upcomingGames = []
            return
        }
        upcomingGames = await BackendGamesRepository.shared.fetchUpcomingMyTeamGames(selectedTeam: selectedTeam) ?? []
    }
}

// MARK: - StatCard
private struct StatCard: View {
    let value: String
    let label: String
    let valueColor: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(valueColor)
            Text(label)
                .font(.system(size: 11))
                .foregroundColor(AppColors.gray400)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(AppColors.gray900)
        .cornerRadius(12)
    }
}

// MARK: - GameCard
private struct GameCard: View {
    let game: Game
    let primaryColor: Color
    let isWatchSynced: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 0) {
                // Status row
                HStack {
                    statusView
                    Spacer()
                    if game.isMyTeam {
                        myTeamBadge
                    }
                }

                Spacer().frame(height: 16)

                // Score rows
                VStack(spacing: 12) {
                    TeamScoreRow(team: game.awayTeamId, teamName: game.awayTeam, score: game.awayScore,
                                 isScheduled: isNotStartedStatus(game.status),
                                 isWinner: game.status == .finished && game.awayScore > game.homeScore,
                                 isMyTeam: game.isMyTeam)
                    TeamScoreRow(team: game.homeTeamId, teamName: game.homeTeam, score: game.homeScore,
                                 isScheduled: isNotStartedStatus(game.status),
                                 isWinner: game.status == .finished && game.homeScore > game.awayScore,
                                 isMyTeam: game.isMyTeam)
                }
            }
            .padding(20)
            .background(game.isMyTeam ? primaryColor.opacity(0.15) : AppColors.gray900)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(
                        isWatchSynced ? AppColors.yellow500 :
                            (game.isMyTeam ? AppColors.yellow500.opacity(0.5) : Color.clear),
                        lineWidth: isWatchSynced ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 24)
        .padding(.vertical, 6)
    }

    @ViewBuilder
    private var statusView: some View {
        HStack(spacing: 8) {
            switch game.status {
            case .live:
                Circle().fill(AppColors.red500).frame(width: 8, height: 8)
                Text("LIVE")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppColors.red500)
                Text(game.inning)
                    .font(.system(size: 14))
                    .foregroundColor(game.isMyTeam ? .white.opacity(0.9) : AppColors.gray400)
                if isWatchSynced {
                    Text("(워치에서 중계중)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(AppColors.yellow400)
                }
            case .scheduled:
                Image(systemName: "clock")
                    .foregroundColor(AppColors.gray400)
                    .font(.system(size: 16))
                if let time = game.time, !time.isEmpty {
                    Text("경기 시작 시간 \(time)")
                        .font(.system(size: 14))
                        .foregroundColor(AppColors.gray400)
                }
            case .finished:
                Text("경기 종료")
                    .font(.system(size: 14))
                    .foregroundColor(AppColors.gray500)
            case .canceled:
                Circle().fill(AppColors.red500).frame(width: 8, height: 8)
                Text("경기 취소")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppColors.red500)
            case .postponed:
                Circle().fill(AppColors.orange500).frame(width: 8, height: 8)
                Text("경기 연기")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(AppColors.orange500)
            }
        }
    }

    private var myTeamBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill")
                .font(.system(size: 14))
                .foregroundColor(.white)
            Text("응원팀")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(AppColors.yellow500)
        .cornerRadius(20)
    }
}

// MARK: - TeamScoreRow
private struct TeamScoreRow: View {
    let team: Team
    let teamName: String
    let score: Int
    let isScheduled: Bool
    let isWinner: Bool
    let isMyTeam: Bool

    var body: some View {
        HStack {
            HStack(spacing: 12) {
                TeamLogo(team: team, size: 32)
                Text(teamName)
                    .font(.system(size: isMyTeam ? 18 : 16, weight: .medium))
                    .foregroundColor(isWinner ? .white : (isScheduled ? .white : AppColors.gray500))
            }
            Spacer()
            Text(isScheduled ? "-" : "\(score)")
                .font(.system(size: isMyTeam ? 28 : 24, weight: .bold))
                .foregroundColor(isWinner ? .white : (isScheduled ? .white : AppColors.gray500))
        }
    }
}

// MARK: - UpcomingGameCard
private struct UpcomingGameCard: View {
    let selectedTeam: Team
    let upcoming: UpcomingGameSchedule

    private var game: Game { upcoming.game }
    private var isMyTeamHome: Bool { game.homeTeamId == selectedTeam }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(formatUpcomingDateTime(upcoming.gameDate, time: game.time))
                .font(.system(size: 14))
                .foregroundColor(AppColors.gray400)

            HStack {
                Text(isMyTeamHome ? game.homeTeam : game.awayTeam)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                Text(" vs ")
                    .font(.system(size: 14))
                    .foregroundColor(AppColors.gray500)
                Text(isMyTeamHome ? game.awayTeam : game.homeTeam)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
            }

            Text(isMyTeamHome ? "\(game.homeTeam) 홈경기" : "\(game.homeTeam) 원정경기")
                .font(.system(size: 12))
                .foregroundColor(AppColors.gray500)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppColors.gray900)
        .cornerRadius(16)
        .padding(.horizontal, 24)
        .padding(.bottom, 12)
    }

    private func formatUpcomingDateTime(_ date: Date, time: String?) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M월 d일"
        let dateText = formatter.string(from: date)
        let timeText = (time?.isEmpty ?? true) ? "--:--" : time!
        return "\(dateText) \(timeText)"
    }
}

// MARK: - Helper Functions
private func sortHomeGames(_ games: [Game]) -> [Game] {
    games.sorted { a, b in
        if a.isMyTeam != b.isMyTeam { return a.isMyTeam }
        let aPriority = statusPriority(a.status)
        let bPriority = statusPriority(b.status)
        if aPriority != bPriority { return aPriority < bPriority }
        return a.id < b.id
    }
}

private func statusPriority(_ status: GameStatus) -> Int {
    switch status {
    case .live: return 0
    case .finished: return 1
    case .scheduled: return 2
    case .postponed: return 3
    case .canceled: return 4
    }
}

private func isNotStartedStatus(_ status: GameStatus) -> Bool {
    switch status {
    case .scheduled, .postponed, .canceled: return true
    case .live, .finished: return false
    }
}

private func isPlayableGameStatus(_ status: GameStatus) -> Bool {
    switch status {
    case .live, .scheduled: return true
    case .finished, .canceled, .postponed: return false
    }
}
