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

                BannerAdView()
                    .frame(width: 320, height: 50)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, AppSpacing.md)

                gamesListHeader
                gamesListSection
                upcomingGamesSection
                Spacer().frame(height: AppSpacing.bottomSafeSpacer)
            }
        }
        .background(AppColors.gray950)
        .task(id: selectedTeam) {
            async let record: () = loadTeamRecord()
            async let upcoming: () = loadUpcomingGames()
            _ = await (record, upcoming)
        }
    }

    // MARK: - Header
    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: AppSpacing.md) {
                    TeamLogo(team: selectedTeam, size: 92)
                    VStack(alignment: .leading) {
                        Text("BaseHaptic Live")
                            .font(AppFont.micro)
                            .foregroundColor(.white.opacity(0.7))
                        Text(selectedTeam.teamName)
                            .font(AppFont.h3Bold)
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

            Spacer().frame(height: AppSpacing.lg)

            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                HStack(spacing: AppSpacing.sm) {
                    Image(systemName: "calendar")
                        .foregroundColor(.white)
                        .font(AppFont.bodyLg)
                    Text(todayDateString)
                        .font(AppFont.body)
                        .foregroundColor(.white)
                }
                Text("오늘의 경기 \(games.filter { isPlayableGameStatus($0.status) }.count)개")
                    .font(AppFont.body)
                    .foregroundColor(.white.opacity(0.8))
            }
            .padding(AppSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.white.opacity(0.15))
            .cornerRadius(AppRadius.lg)
        }
        .padding(AppSpacing.xxl)
        .padding(.bottom, AppSpacing.xxxl)
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
        HStack(spacing: AppSpacing.md) {
            StatCard(value: recentWinsText, label: "최근 5경기", valueColor: AppColors.green500)
            StatCard(value: rankingText, label: "현재 순위", valueColor: AppColors.yellow500)
            StatCard(value: wraText, label: "승률", valueColor: AppColors.blue500)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .offset(y: -AppSpacing.lg)
    }

    // MARK: - Games List
    private var gamesListHeader: some View {
        HStack {
            Text("오늘의 경기")
                .font(AppFont.h5Bold)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.vertical, AppSpacing.lg)
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
            Spacer().frame(height: AppSpacing.xxxl)
            Text("다가오는 경기")
                .font(AppFont.h5Bold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.vertical, AppSpacing.lg)

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

    private var recentWinsText: String {
        guard let games = teamRecordStats?.lastFiveGames else { return "-" }
        let wins = games.filter { $0 == "W" }.count
        return "\(wins)승"
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
        VStack(spacing: AppSpacing.xs) {
            Text(value)
                .font(AppFont.h4Bold)
                .foregroundColor(valueColor)
            Text(label)
                .font(AppFont.tiny)
                .foregroundColor(AppColors.gray400)
        }
        .frame(maxWidth: .infinity)
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
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

                Spacer().frame(height: AppSpacing.lg)

                // Score rows
                VStack(spacing: AppSpacing.md) {
                    TeamScoreRow(team: game.awayTeamId, teamName: game.awayTeamId.teamName, score: game.awayScore,
                                 isScheduled: isNotStartedStatus(game.status),
                                 isWinner: game.status == .finished && game.awayScore > game.homeScore,
                                 isMyTeam: game.isMyTeam)
                    TeamScoreRow(team: game.homeTeamId, teamName: game.homeTeamId.teamName, score: game.homeScore,
                                 isScheduled: isNotStartedStatus(game.status),
                                 isWinner: game.status == .finished && game.homeScore > game.awayScore,
                                 isMyTeam: game.isMyTeam)
                }
            }
            .padding(AppSpacing.xl)
            .background(game.isMyTeam ? primaryColor.opacity(0.15) : AppColors.gray900)
            .cornerRadius(AppRadius.lg)
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.lg)
                    .stroke(
                        isWatchSynced ? AppColors.yellow500 :
                            (game.isMyTeam ? AppColors.yellow500.opacity(0.5) : Color.clear),
                        lineWidth: isWatchSynced ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
        .padding(.horizontal, AppSpacing.xxl)
        // Reason: 카드 간 간격을 최소화하기 위한 미세 조정값
        .padding(.vertical, 6)
    }

    @ViewBuilder
    private var statusView: some View {
        HStack(spacing: AppSpacing.sm) {
            switch game.status {
            case .live:
                Circle().fill(AppColors.red500).frame(width: 8, height: 8)
                Text("LIVE")
                    .font(AppFont.bodyMedium)
                    .foregroundColor(AppColors.red500)
                Text(game.inning)
                    .font(AppFont.body)
                    .foregroundColor(game.isMyTeam ? .white.opacity(0.9) : AppColors.gray400)
                if isWatchSynced {
                    Text("(워치에서 중계중)")
                        .font(AppFont.microMedium)
                        .foregroundColor(AppColors.yellow400)
                }
            case .scheduled:
                Image(systemName: "clock")
                    .foregroundColor(AppColors.gray400)
                    .font(AppFont.bodyLg)
                if let time = game.time, !time.isEmpty {
                    Text("경기 시작 시간 \(time)")
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                }
            case .finished:
                Text("경기 종료")
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray500)
            case .canceled:
                Circle().fill(AppColors.red500).frame(width: 8, height: 8)
                Text("경기 취소")
                    .font(AppFont.bodyMedium)
                    .foregroundColor(AppColors.red500)
            case .postponed:
                Circle().fill(AppColors.orange500).frame(width: 8, height: 8)
                Text("경기 연기")
                    .font(AppFont.bodyMedium)
                    .foregroundColor(AppColors.orange500)
            }
        }
    }

    private var myTeamBadge: some View {
        HStack(spacing: AppSpacing.xs) {
            Image(systemName: "star.fill")
                .font(AppFont.body)
                .foregroundColor(.white)
            Text("응원팀")
                .font(AppFont.microBold)
                .foregroundColor(.white)
        }
        .padding(.horizontal, AppSpacing.md)
        // Reason: 배지 높이를 살짝 압축하기 위한 미세 조정값
        .padding(.vertical, 6)
        .background(AppColors.yellow500)
        .cornerRadius(AppRadius.xl)
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
            HStack(spacing: AppSpacing.md) {
                TeamLogo(team: team, size: 56)
                Text(teamName)
                    .font(isMyTeam ? AppFont.h5Bold : AppFont.bodyLgMedium)
                    .foregroundColor(isWinner ? .white : (isScheduled ? .white : AppColors.gray500))
            }
            Spacer()
            Text(isScheduled ? "-" : "\(score)")
                .font(isMyTeam ? AppFont.h2 : AppFont.h3Bold)
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
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text(formatUpcomingDateTime(upcoming.gameDate, time: game.time))
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)

            HStack {
                Text(isMyTeamHome ? game.homeTeamId.teamName : game.awayTeamId.teamName)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)
                Text(" vs ")
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray500)
                Text(isMyTeamHome ? game.awayTeamId.teamName : game.homeTeamId.teamName)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)
            }

            Text(isMyTeamHome ? "\(game.homeTeamId.teamName) 홈경기" : "\(game.homeTeamId.teamName) 원정경기")
                .font(AppFont.micro)
                .foregroundColor(AppColors.gray500)
        }
        .padding(AppSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.lg)
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.bottom, AppSpacing.md)
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
