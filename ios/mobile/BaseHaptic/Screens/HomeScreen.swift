import SwiftUI

struct HomeScreen: View {
    let selectedTeam: Team
    let todayGames: [Game]
    let activeTheme: ThemeData?
    let syncedGameId: String?
    let activeLiveActivityGameId: String?
    let isWatchAppInstalled: Bool
    let checkinStadium: Stadium?
    let showUpdateHighlights: Bool
    let onDismissUpdateHighlights: () -> Void
    let onConfirmCheckin: () -> Void
    let onDismissCheckin: () -> Void
    let onSelectGame: (Game) -> Void
    let onToggleLiveActivity: (Game) -> Void
    let onToggleWatchSync: (Game) -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var teamRecordStats: TeamRecordStats?
    @State private var upcomingGames: [UpcomingGameSchedule] = []
    @State private var showingStandings = false
    @State private var standingsItems: [TeamRecordStanding] = []
    @State private var standingsLoading = false
    @State private var standingsError: String?
    @State private var showingSchedule = false
    @State private var scheduleItems: [UpcomingGameSchedule] = []
    @State private var scheduleLoading = false
    @State private var scheduleError: String?
    @State private var scheduleMonth = monthStart(for: Date())
    @State private var selectedScheduleDate = Calendar.current.startOfDay(for: Date())
    @State private var updateHighlightStepIndex = 0
    @State private var updateHighlightFrames: [UpdateHighlightStep: CGRect] = [:]

    private var primaryColor: Color {
        activeTheme?.colors.primary ?? teamTheme.primary
    }

    private var games: [Game] {
        let sortedGames = sortHomeGames(todayGames)
        if showUpdateHighlights && sortedGames.isEmpty {
            return [updateHighlightSampleGame(selectedTeam: selectedTeam)]
        }
        return sortedGames
    }

    private var updateHighlightStep: UpdateHighlightStep? {
        guard showUpdateHighlights else { return nil }
        return UpdateHighlightStep.allCases[safe: updateHighlightStepIndex]
    }

    private func advanceUpdateHighlight() {
        if updateHighlightStepIndex >= UpdateHighlightStep.allCases.count - 1 {
            onDismissUpdateHighlights()
        } else {
            updateHighlightStepIndex += 1
        }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ZStack {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        headerSection
                            .id(UpdateHighlightScrollTarget.header)
                        quickStatsSection

                        BannerAdView()
                            .frame(width: 320, height: 50)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AppSpacing.md)

                        gamesListHeader
                        if let checkinStadium {
                            CheerCheckinCard(
                                stadiumName: checkinStadium.name,
                                stadiumRegion: StadiumDirectory.region(forCode: checkinStadium.code),
                                teamLabel: selectedTeam.teamName,
                                onConfirm: onConfirmCheckin,
                                onDismiss: onDismissCheckin
                            )
                            .padding(.horizontal, AppSpacing.xxl)
                            .padding(.bottom, AppSpacing.md)
                        }
                        gamesListSection
                            .id(UpdateHighlightScrollTarget.games)
                        upcomingGamesSection
                        Spacer().frame(height: AppSpacing.bottomSafeSpacer)
                    }
                }
                .coordinateSpace(name: UpdateHighlightCoordinateSpace.name)
                .onPreferenceChange(UpdateHighlightFramePreferenceKey.self) { frames in
                    updateHighlightFrames = frames
                }

                if let step = updateHighlightStep, let targetFrame = updateHighlightFrames[step] {
                    UpdateHighlightOverlay(
                        step: step,
                        targetFrame: targetFrame,
                        onNext: advanceUpdateHighlight,
                        onDismiss: onDismissUpdateHighlights
                    )
                    .zIndex(10)
                }
            }
            .onChange(of: showUpdateHighlights) { _, showing in
                if showing {
                    updateHighlightStepIndex = 0
                    withAnimation(.easeInOut(duration: 0.2)) {
                        proxy.scrollTo(UpdateHighlightScrollTarget.header, anchor: .top)
                    }
                }
            }
            .onChange(of: updateHighlightStep) { _, step in
                withAnimation(.easeInOut(duration: 0.2)) {
                    switch step {
                    case .standings, .schedule:
                        proxy.scrollTo(UpdateHighlightScrollTarget.header, anchor: .top)
                    case .lockScreen, .watch, .score:
                        proxy.scrollTo(UpdateHighlightScrollTarget.games, anchor: .top)
                    case nil:
                        break
                    }
                }
            }
        }
        .background(AppColors.gray950)
        .task(id: selectedTeam) {
            async let record: () = loadTeamRecord()
            async let upcoming: () = loadUpcomingGames()
            _ = await (record, upcoming)
        }
        .sheet(isPresented: $showingStandings) {
            TeamStandingsSheet(
                items: standingsItems,
                loading: standingsLoading,
                error: standingsError,
                onRetry: {
                    Task { await loadTeamStandings() }
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showingSchedule) {
            MyTeamScheduleSheet(
                selectedTeam: selectedTeam,
                schedules: scheduleItems,
                currentMonth: scheduleMonth,
                selectedDate: selectedScheduleDate,
                loading: scheduleLoading,
                error: scheduleError,
                onRetry: {
                    Task { await loadMyTeamSchedule(forceRefresh: true) }
                },
                onPreviousMonth: {
                    scheduleMonth = Calendar.current.date(byAdding: .month, value: -1, to: scheduleMonth) ?? scheduleMonth
                },
                onNextMonth: {
                    scheduleMonth = Calendar.current.date(byAdding: .month, value: 1, to: scheduleMonth) ?? scheduleMonth
                },
                onSelectDate: { date in
                    selectedScheduleDate = Calendar.current.startOfDay(for: date)
                },
                onSelectSchedule: { game in
                    showingSchedule = false
                    onSelectGame(game)
                }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
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
                Button {
                    showingStandings = true
                    Task { await loadTeamStandings() }
                } label: {
                    Circle()
                        .fill(Color.white.opacity(0.2))
                        .frame(width: 48, height: 48)
                        .overlay(
                            Image("kbo_team_standings_icon")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 30, height: 30)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("전체 순위")
                .trackUpdateHighlight(.standings)
            }

            Spacer().frame(height: AppSpacing.lg)

            Button {
                showingSchedule = true
                selectedScheduleDate = Calendar.current.startOfDay(for: Date())
                scheduleMonth = monthStart(for: Date())
                Task { await loadMyTeamSchedule(forceRefresh: false) }
            } label: {
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
            .buttonStyle(.plain)
            .trackUpdateHighlight(.schedule)
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

    @ViewBuilder
    private var gamesListSection: some View {
        if games.isEmpty {
            VStack(spacing: AppSpacing.md) {
                Text("⚾")
                    .font(.system(size: 32))
                Text("오늘은 경기가 없습니다")
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(AppColors.gray400)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.xxxl)
            .background(AppColors.gray900)
            .cornerRadius(AppRadius.lg)
            .padding(.horizontal, AppSpacing.xxl)
            .padding(.vertical, 6)
        } else {
            ForEach(Array(games.enumerated()), id: \.element.id) { index, game in
                let isWatchSynced = syncedGameId == game.id
                let isLiveActivityActive = activeLiveActivityGameId == game.id
                GameCard(
                    game: game,
                    primaryColor: primaryColor,
                    isWatchSynced: isWatchSynced,
                    isLiveActivityActive: isLiveActivityActive,
                    showsWatchToggle: isWatchAppInstalled || showUpdateHighlights,
                    captureUpdateHighlights: showUpdateHighlights && index == 0,
                    onTap: { onSelectGame(game) },
                    onLiveActivityTap: { onToggleLiveActivity(game) },
                    onWatchSyncTap: { onToggleWatchSync(game) }
                )
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

    @MainActor
    private func loadMyTeamSchedule(forceRefresh: Bool = false) async {
        guard selectedTeam != .none else {
            scheduleItems = []
            scheduleError = nil
            scheduleLoading = false
            return
        }

        scheduleLoading = true
        scheduleError = nil
        let today = Date()
        let loaded = await BackendGamesRepository.shared.fetchMyTeamScheduleRangeCached(
            selectedTeam: selectedTeam,
            fromDate: scheduleSeasonStartDate(today),
            toDate: scheduleSeasonEndDate(today),
            forceRefresh: forceRefresh
        )
        if let loaded {
            scheduleItems = loaded
        } else {
            scheduleItems = []
            scheduleError = "응원팀 일정을 불러오지 못했습니다."
        }
        scheduleLoading = false
    }

    @MainActor
    private func loadTeamStandings() async {
        standingsLoading = true
        standingsError = nil
        let loaded = await BackendGamesRepository.shared.fetchTeamRecordStandings()
        if let loaded {
            standingsItems = loaded
        } else {
            standingsItems = []
            standingsError = "팀 순위를 불러오지 못했습니다."
        }
        standingsLoading = false
    }
}

// MARK: - TeamStandingsSheet
private struct TeamStandingsSheet: View {
    let items: [TeamRecordStanding]
    let loading: Bool
    let error: String?
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.lg) {
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("전체 순위")
                    .font(AppFont.h4Bold)
                    .foregroundColor(.white)
            }

            if loading {
                VStack(spacing: AppSpacing.md) {
                    ProgressView()
                        .tint(AppColors.yellow500)
                    Text("순위를 불러오는 중입니다")
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.xxxl)
            } else if let error {
                StandingsMessage(title: error, actionLabel: "다시 시도", onAction: onRetry)
            } else if items.isEmpty {
                StandingsMessage(title: "저장된 팀 순위가 없습니다", actionLabel: "새로고침", onAction: onRetry)
            } else {
                ScrollView {
                    LazyVStack(spacing: AppSpacing.sm) {
                        ForEach(items) { item in
                            TeamStandingRow(item: item)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.top, AppSpacing.lg)
        .padding(.bottom, AppSpacing.xxxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(AppColors.gray950)
    }
}

private struct StandingsMessage: View {
    let title: String
    let actionLabel: String
    let onAction: () -> Void

    var body: some View {
        VStack(spacing: AppSpacing.md) {
            Text(title)
                .font(AppFont.bodyLgMedium)
                .foregroundColor(AppColors.gray300)
            Button(action: onAction) {
                Text(actionLabel)
                    .font(AppFont.bodyMedium)
                    .foregroundColor(AppColors.yellow500)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, AppSpacing.xxxl)
    }
}

// MARK: - MyTeamScheduleSheet
private struct MyTeamScheduleSheet: View {
    let selectedTeam: Team
    let schedules: [UpcomingGameSchedule]
    let currentMonth: Date
    let selectedDate: Date
    let loading: Bool
    let error: String?
    let onRetry: () -> Void
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void
    let onSelectDate: (Date) -> Void
    let onSelectSchedule: (Game) -> Void

    private var schedulesByDay: [Date: [UpcomingGameSchedule]] {
        Dictionary(grouping: schedules) { Calendar.current.startOfDay(for: $0.gameDate) }
    }

    private var selectedSchedules: [UpcomingGameSchedule] {
        schedulesByDay[Calendar.current.startOfDay(for: selectedDate)] ?? []
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.lg) {
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text("응원팀 경기 일정")
                    .font(AppFont.h4Bold)
                    .foregroundColor(.white)
                Text(selectedTeam == .none ? "응원팀을 선택하면 일정을 볼 수 있습니다." : "\(selectedTeam.teamName) 시즌 일정")
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray400)
            }

            if selectedTeam == .none {
                ScheduleMessage(
                    systemImage: "baseball",
                    title: "응원팀이 선택되지 않았습니다",
                    message: "마이팀에서 응원팀을 먼저 선택해주세요."
                )
            } else if loading {
                VStack(spacing: AppSpacing.md) {
                    ProgressView()
                        .tint(.white)
                    Text("일정을 불러오는 중입니다")
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.xxxl)
            } else if let error {
                ScheduleMessage(
                    systemImage: "exclamationmark.triangle",
                    title: error,
                    message: "네트워크 상태를 확인한 뒤 다시 시도해주세요.",
                    actionLabel: "다시 시도",
                    onAction: onRetry
                )
            } else if schedules.isEmpty {
                ScheduleMessage(
                    systemImage: "calendar.badge.exclamationmark",
                    title: "표시할 일정이 없습니다",
                    message: "저장된 응원팀 경기 일정이 없습니다."
                )
            } else {
                ScheduleCalendarView(
                    selectedTeam: selectedTeam,
                    currentMonth: currentMonth,
                    selectedDate: selectedDate,
                    schedulesByDay: schedulesByDay,
                    onPreviousMonth: onPreviousMonth,
                    onNextMonth: onNextMonth,
                    onSelectDate: onSelectDate
                )

                VStack(alignment: .leading, spacing: AppSpacing.sm) {
                    Text(formatScheduleDate(selectedDate))
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)

                    if selectedSchedules.isEmpty {
                        Text("선택한 날짜에 등록된 경기가 없습니다.")
                            .font(AppFont.body)
                            .foregroundColor(AppColors.gray400)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(AppSpacing.lg)
                            .background(AppColors.gray900)
                            .cornerRadius(AppRadius.md)
                    } else {
                        ScrollView {
                            LazyVStack(spacing: AppSpacing.sm) {
                                ForEach(selectedSchedules) { schedule in
                                    MyTeamScheduleRow(
                                        selectedTeam: selectedTeam,
                                        schedule: schedule,
                                        onTap: { onSelectSchedule(schedule.game) }
                                    )
                                }
                            }
                        }
                        .frame(maxHeight: 260)
                    }
                }
            }
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.top, AppSpacing.lg)
        .padding(.bottom, AppSpacing.xxxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(AppColors.gray950)
    }
}

private struct ScheduleCalendarView: View {
    let selectedTeam: Team
    let currentMonth: Date
    let selectedDate: Date
    let schedulesByDay: [Date: [UpcomingGameSchedule]]
    let onPreviousMonth: () -> Void
    let onNextMonth: () -> Void
    let onSelectDate: (Date) -> Void

    private let columns = Array(repeating: GridItem(.flexible(), spacing: AppSpacing.xs), count: 7)

    var body: some View {
        VStack(spacing: AppSpacing.sm) {
            HStack {
                Button(action: onPreviousMonth) {
                    Image(systemName: "chevron.left")
                        .font(AppFont.bodyLgBold)
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)

                Spacer()

                Text(formatScheduleMonth(currentMonth))
                    .font(AppFont.bodyLgBold)
                    .foregroundColor(.white)

                Spacer()

                Button(action: onNextMonth) {
                    Image(systemName: "chevron.right")
                        .font(AppFont.bodyLgBold)
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
            }

            LazyVGrid(columns: columns, spacing: AppSpacing.xs) {
                ForEach(["일", "월", "화", "수", "목", "금", "토"], id: \.self) { weekday in
                    Text(weekday)
                        .font(AppFont.microBold)
                        .foregroundColor(AppColors.gray500)
                        .frame(maxWidth: .infinity)
                }

                ForEach(Array(monthGridDates(for: currentMonth).enumerated()), id: \.offset) { _, date in
                    ScheduleCalendarDayCell(
                        selectedTeam: selectedTeam,
                        date: date,
                        isSelected: date.map { Calendar.current.isDate($0, inSameDayAs: selectedDate) } ?? false,
                        schedules: date.map { schedulesByDay[Calendar.current.startOfDay(for: $0)] ?? [] } ?? [],
                        onSelectDate: onSelectDate
                    )
                }
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.lg)
    }
}

private struct ScheduleCalendarDayCell: View {
    let selectedTeam: Team
    let date: Date?
    let isSelected: Bool
    let schedules: [UpcomingGameSchedule]
    let onSelectDate: (Date) -> Void

    private var label: ScheduleCalendarDayLabel? {
        calendarDayLabel(selectedTeam: selectedTeam, schedules: schedules)
    }

    var body: some View {
        Button {
            if let date {
                onSelectDate(date)
            }
        } label: {
            VStack(spacing: AppSpacing.xs) {
                Text(date.map { "\(Calendar.current.component(.day, from: $0))" } ?? "")
                    .font(isSelected ? AppFont.bodyMedium : AppFont.body)
                    .foregroundColor(.white)
                Spacer(minLength: 0)
                if let label {
                    if let opponentTeam = label.opponentTeam {
                        HStack(spacing: 0) {
                            Text(label.text)
                                .font(AppFont.microBold)
                                .foregroundColor(label.color)
                                .lineLimit(1)
                            TeamLogo(team: opponentTeam, size: 22)
                        }
                        .frame(maxWidth: .infinity)
                    } else {
                        Text(label.text)
                            .font(AppFont.microBold)
                            .foregroundColor(label.color)
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)
                            .frame(maxWidth: .infinity)
                    }
                } else {
                    Spacer()
                        .frame(height: 14)
                }
            }
            .padding(.horizontal, 2)
            .padding(.vertical, AppSpacing.xs)
            .frame(maxWidth: .infinity)
            .frame(height: 72)
            .background(isSelected ? AppColors.gray800 : (schedules.isEmpty ? Color.clear : AppColors.gray950))
            .cornerRadius(AppRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .stroke(isSelected ? AppColors.yellow500 : (schedules.isEmpty ? Color.clear : AppColors.gray700), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(date == nil)
    }
}

private struct ScheduleCalendarDayLabel {
    let text: String
    let color: Color
    var opponentTeam: Team? = nil
}

private struct ScheduleMessage: View {
    let systemImage: String
    let title: String
    let message: String
    var actionLabel: String?
    var onAction: (() -> Void)?

    var body: some View {
        VStack(spacing: AppSpacing.sm) {
            Image(systemName: systemImage)
                .font(.system(size: 30, weight: .semibold))
                .foregroundColor(AppColors.gray500)
            Text(title)
                .font(AppFont.bodyLgMedium)
                .foregroundColor(.white)
            Text(message)
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
                .multilineTextAlignment(.center)
            if let actionLabel, let onAction {
                Button(action: onAction) {
                    Text(actionLabel)
                        .font(AppFont.bodyMedium)
                        .foregroundColor(AppColors.yellow500)
                }
                .buttonStyle(.plain)
                .padding(.top, AppSpacing.xs)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(AppSpacing.xxl)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.lg)
    }
}

private struct MyTeamScheduleRow: View {
    let selectedTeam: Team
    let schedule: UpcomingGameSchedule
    let onTap: () -> Void

    private var game: Game { schedule.game }
    private var isMyTeamHome: Bool { game.homeTeamId == selectedTeam }
    private var opponent: String { isMyTeamHome ? game.awayTeamId.teamName : game.homeTeamId.teamName }
    private var opponentTeam: Team { isMyTeamHome ? game.awayTeamId : game.homeTeamId }
    private var venueText: String { isMyTeamHome ? "홈 경기" : "원정 경기" }
    private var venueWithStadium: String { "\(venueText) (\(stadiumName(forHomeTeam: game.homeTeamId)))" }
    private var resultLabel: ScheduleCalendarDayLabel? { myTeamResultLabel(selectedTeam: selectedTeam, game: game) }
    private var scoreText: String? { myTeamScoreText(selectedTeam: selectedTeam, game: game) }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: AppSpacing.md) {
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text(formatScheduleDateTime(schedule.gameDate, time: game.time))
                        .font(AppFont.captionBold)
                        .foregroundColor(AppColors.gray400)
                    HStack(spacing: AppSpacing.xs) {
                        TeamLogo(team: selectedTeam, size: 22)
                        Text(selectedTeam.teamName)
                            .font(AppFont.bodyLgMedium)
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Text("vs")
                            .font(AppFont.bodyLgMedium)
                            .foregroundColor(AppColors.gray400)
                        TeamLogo(team: opponentTeam, size: 22)
                        Text(opponent)
                            .font(AppFont.bodyLgMedium)
                            .foregroundColor(.white)
                            .lineLimit(1)
                    }
                    if let resultLabel, let scoreText {
                        HStack(spacing: AppSpacing.xs) {
                            Text(resultLabel.text)
                                .font(AppFont.microBold)
                                .foregroundColor(resultLabel.color)
                                .padding(.horizontal, AppSpacing.sm)
                                .padding(.vertical, AppSpacing.xxs)
                                .background(resultLabel.color.opacity(0.16))
                                .clipShape(Capsule())
                            Text("\(scoreText) · \(venueWithStadium)")
                                .font(AppFont.micro)
                                .foregroundColor(AppColors.gray400)
                        }
                    } else {
                        Text(venueWithStadium)
                            .font(AppFont.micro)
                            .foregroundColor(AppColors.gray500)
                    }
                }
                Spacer(minLength: AppSpacing.md)
                ScheduleStatusBadge(status: game.status)
            }
            .padding(AppSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppColors.gray900)
            .cornerRadius(AppRadius.md)
        }
        .buttonStyle(.plain)
    }
}

private struct ScheduleStatusBadge: View {
    let status: GameStatus

    private var label: String {
        switch status {
        case .live: return "LIVE"
        case .finished: return "종료"
        case .scheduled: return "예정"
        case .postponed: return "연기"
        case .canceled: return "취소"
        }
    }

    private var color: Color {
        switch status {
        case .live: return AppColors.red500
        case .finished: return AppColors.gray500
        case .scheduled: return AppColors.blue500
        case .postponed: return AppColors.yellow500
        case .canceled: return AppColors.gray500
        }
    }

    var body: some View {
        Text(label)
            .font(AppFont.microBold)
            .foregroundColor(color)
            .padding(.horizontal, AppSpacing.sm)
            .padding(.vertical, AppSpacing.xs)
            .background(color.opacity(0.16))
            .clipShape(Capsule())
    }
}

private struct TeamStandingRow: View {
    let item: TeamRecordStanding

    private var team: Team? {
        teamFromKboTeamId(item.teamId)
    }

    private var displayName: String {
        team?.teamName ?? item.teamName
    }

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Text(item.ranking.map(String.init) ?? "-")
                .font(AppFont.bodyLgBold)
                .foregroundColor((item.ranking ?? 99) <= 3 ? AppColors.yellow500 : AppColors.gray300)
                .frame(width: 30, alignment: .leading)

            if let team {
                TeamLogo(team: team, size: 36)
            } else {
                Circle()
                    .fill(AppColors.gray800)
                    .frame(width: 36, height: 36)
                    .overlay(
                        Text(String(displayName.prefix(1)))
                            .font(AppFont.microBold)
                            .foregroundColor(.white)
                    )
            }

            VStack(alignment: .leading, spacing: AppSpacing.xxs) {
                Text(displayName)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)
                    .lineLimit(1)
                Text(teamRecordLine(item))
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray400)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: AppSpacing.xxs) {
                Text("승률 \(item.wra.map { String(format: "%.3f", $0) } ?? "-.--")")
                    .font(AppFont.bodyMedium)
                    .foregroundColor(AppColors.blue400)
                Text("게임차 \(formatGameBehind(item.gameBehind))")
                    .font(AppFont.tiny)
                    .foregroundColor(AppColors.gray500)
            }
        }
        .padding(AppSpacing.md)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
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

// MARK: - Update Highlight
private enum UpdateHighlightCoordinateSpace {
    static let name = "homeUpdateHighlight"
}

private enum UpdateHighlightScrollTarget: Hashable {
    case header
    case games
}

private enum UpdateHighlightStep: CaseIterable, Hashable {
    case lockScreen
    case watch
    case standings
    case schedule
    case score

    var title: String {
        switch self {
        case .lockScreen: return "잠금화면 토글"
        case .watch: return "Watch 토글"
        case .standings: return "전체 순위 보기"
        case .schedule: return "전체 일정 보기"
        case .score: return "점수 보기"
        }
    }

    var body: String {
        switch self {
        case .lockScreen:
            return "LIVE 경기를 휴대폰 잠금화면에서 볼 수 있어요."
        case .watch:
            return "LIVE 경기를 스마트워치에서 볼 수 있어요."
        case .standings:
            return "아이콘을 누르면 전체 순위를 바로 확인할 수 있어요."
        case .schedule:
            return "이 카드를 누르면 응원팀 시즌 일정을 달력으로 한눈에 볼 수 있어요."
        case .score:
            return "경기 카드에서 최신 점수와 진행 상황을 바로 확인할 수 있어요."
        }
    }
}

private struct UpdateHighlightFramePreferenceKey: PreferenceKey {
    static var defaultValue: [UpdateHighlightStep: CGRect] = [:]

    static func reduce(value: inout [UpdateHighlightStep: CGRect], nextValue: () -> [UpdateHighlightStep: CGRect]) {
        value.merge(nextValue(), uniquingKeysWith: { _, newValue in newValue })
    }
}

private extension View {
    @ViewBuilder
    func trackUpdateHighlight(_ step: UpdateHighlightStep?) -> some View {
        if let step {
            background(
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: UpdateHighlightFramePreferenceKey.self,
                        value: [step: proxy.frame(in: .named(UpdateHighlightCoordinateSpace.name))]
                    )
                }
            )
        } else {
            self
        }
    }
}

private struct UpdateHighlightOverlay: View {
    let step: UpdateHighlightStep
    let targetFrame: CGRect
    let onNext: () -> Void
    let onDismiss: () -> Void

    private var index: Int {
        (UpdateHighlightStep.allCases.firstIndex(of: step) ?? 0) + 1
    }

    private var isLast: Bool {
        index == UpdateHighlightStep.allCases.count
    }

    var body: some View {
        GeometryReader { proxy in
            let paddedFrame = targetFrame.insetBy(dx: -8, dy: -8)
            let cardEstimatedHeight: CGFloat = 156
            let cardSpacing: CGFloat = 12
            let cardTopBelow = min(
                paddedFrame.maxY + cardSpacing,
                proxy.size.height - cardEstimatedHeight - AppSpacing.xxl
            )
            let cardTopAbove = max(
                AppSpacing.xxl,
                paddedFrame.minY - cardEstimatedHeight - cardSpacing
            )
            let shouldPlaceAbove = paddedFrame.maxY + cardSpacing + cardEstimatedHeight > proxy.size.height - AppSpacing.xxl
            let cardTop = shouldPlaceAbove ? cardTopAbove : cardTopBelow

            ZStack {
                Path { path in
                    path.addRect(CGRect(origin: .zero, size: proxy.size))
                    path.addRoundedRect(in: paddedFrame, cornerSize: CGSize(width: 18, height: 18))
                }
                .fill(Color.black.opacity(0.76), style: FillStyle(eoFill: true))

                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(AppColors.yellow500, lineWidth: 2)
                    .frame(width: paddedFrame.width, height: paddedFrame.height)
                    .position(x: paddedFrame.midX, y: paddedFrame.midY)

                VStack(alignment: .leading, spacing: AppSpacing.sm) {
                        Text("\(index)/\(UpdateHighlightStep.allCases.count)  \(step.title)")
                            .font(AppFont.captionBold)
                            .foregroundColor(AppColors.gray950)
                            .padding(.horizontal, AppSpacing.sm)
                            .padding(.vertical, AppSpacing.xs)
                            .background(AppColors.yellow400)
                            .clipShape(Capsule())
                        Text(step.body)
                            .font(AppFont.body)
                            .foregroundColor(.white)
                        HStack {
                            Spacer()
                            Button("건너뛰기", action: onDismiss)
                                .font(AppFont.captionBold)
                                .foregroundColor(AppColors.gray300)
                            Button(isLast ? "끝" : "다음", action: onNext)
                                .font(AppFont.captionBold)
                                .foregroundColor(AppColors.yellow400)
                        }
                }
                .padding(AppSpacing.lg)
                .frame(maxWidth: 360)
                .background(Color(red: 0.08, green: 0.13, blue: 0.24))
                .cornerRadius(AppRadius.lg)
                .overlay(
                    RoundedRectangle(cornerRadius: AppRadius.lg, style: .continuous)
                        .stroke(Color(red: 0.38, green: 0.70, blue: 1.0).opacity(0.9), lineWidth: 1.5)
                )
                .shadow(color: .black.opacity(0.36), radius: 18, x: 0, y: 10)
                .padding(.horizontal, AppSpacing.xxl)
                .frame(maxWidth: .infinity, alignment: .center)
                .position(x: proxy.size.width / 2, y: cardTop + cardEstimatedHeight / 2)
            }
            .contentShape(Rectangle())
        }
        .ignoresSafeArea(edges: .bottom)
    }
}

private func updateHighlightSampleGame(selectedTeam: Team) -> Game {
    let homeTeam = selectedTeam == .none ? Team.lg : selectedTeam
    let awayTeam: Team = homeTeam == .ssg ? .lg : .ssg
    return Game(
        id: "update-highlight-sample-game",
        homeTeam: homeTeam.teamName,
        awayTeam: awayTeam.teamName,
        homeTeamId: homeTeam,
        awayTeamId: awayTeam,
        homeScore: 10,
        awayScore: 1,
        inning: "4회말",
        status: .live,
        time: "18:30",
        isMyTeam: selectedTeam != .none
    )
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

// MARK: - GameCard
private struct GameCard: View {
    let game: Game
    let primaryColor: Color
    let isWatchSynced: Bool
    let isLiveActivityActive: Bool
    let showsWatchToggle: Bool
    let captureUpdateHighlights: Bool
    let onTap: () -> Void
    let onLiveActivityTap: () -> Void
    let onWatchSyncTap: () -> Void

    var body: some View {
        VStack(spacing: 0) {
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
                    .trackUpdateHighlight(captureUpdateHighlights ? .score : nil)
                }
                .padding(AppSpacing.xl)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .frame(maxWidth: .infinity)
            .buttonStyle(.plain)

            Divider()
                .background(AppColors.gray800)
            liveActionRow
                .padding(.horizontal, AppSpacing.xl)
                .padding(.vertical, AppSpacing.md)
        }
        .background(game.isMyTeam ? primaryColor.opacity(0.15) : AppColors.gray900)
        .cornerRadius(AppRadius.lg)
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.lg)
                .stroke(
                    (isLiveActivityActive || isWatchSynced) ? AppColors.green500 :
                        (game.isMyTeam ? AppColors.yellow500.opacity(0.5) : Color.clear),
                    lineWidth: (isLiveActivityActive || isWatchSynced) ? 2 : 1
                )
        )
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
                if isLiveActivityActive || isWatchSynced {
                    Text("(중계중)")
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

    private var liveActionRow: some View {
        HStack(spacing: AppSpacing.sm) {
            gameToggleCell(
                title: "잠금화면",
                systemImage: isLiveActivityActive ? "lock.fill" : "lock",
                isActive: isLiveActivityActive,
                highlightStep: captureUpdateHighlights ? .lockScreen : nil,
                action: onLiveActivityTap
            )
            if showsWatchToggle {
                Rectangle()
                    .fill(AppColors.gray800)
                    .frame(width: 1, height: 34)
                gameToggleCell(
                    title: "Watch",
                    systemImage: isWatchSynced ? "applewatch.radiowaves.left.and.right" : "applewatch",
                    isActive: isWatchSynced,
                    highlightStep: captureUpdateHighlights ? .watch : nil,
                    action: onWatchSyncTap
                )
            }
        }
    }

    private func gameToggleCell(
        title: String,
        systemImage: String,
        isActive: Bool,
        highlightStep: UpdateHighlightStep?,
        action: @escaping () -> Void
    ) -> some View {
        HStack(spacing: AppSpacing.sm) {
            HStack(spacing: AppSpacing.sm) {
                Image(systemName: systemImage)
                    .font(AppFont.captionBold)
                    .foregroundColor(isActive ? AppColors.green400 : AppColors.gray300)
                    .frame(width: 26, height: 26)
                    .background(
                        Circle()
                            .fill(isActive ? AppColors.green500.opacity(0.18) : AppColors.gray800)
                    )
                Text(title)
                    .font(AppFont.captionBold)
                    .foregroundColor(isActive ? AppColors.green400 : AppColors.gray100)
                    .lineLimit(1)
            }
            Spacer()
            Toggle(
                "",
                isOn: Binding(
                    get: { isActive },
                    set: { _ in action() }
                )
            )
            .labelsHidden()
            .tint(AppColors.green500)
            .scaleEffect(0.72)
            .frame(width: 38, height: 26)
        }
        .frame(maxWidth: .infinity)
        .padding(.leading, AppSpacing.sm)
        .padding(.trailing, AppSpacing.md)
        .padding(.vertical, AppSpacing.sm)
        .background(
            RoundedRectangle(cornerRadius: AppRadius.md, style: .continuous)
                .fill(isActive ? AppColors.green500.opacity(0.12) : AppColors.gray950.opacity(game.isMyTeam ? 0.34 : 0.46))
        )
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.md, style: .continuous)
                .stroke(isActive ? AppColors.green500.opacity(0.38) : AppColors.gray800.opacity(0.8), lineWidth: 1)
        )
        .accessibilityLabel(isActive ? "\(title) 활성화됨" : title)
        .trackUpdateHighlight(highlightStep)
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

private func teamRecordLine(_ item: TeamRecordStanding) -> String {
    let games = item.gameCount.map { "\($0)경기" } ?? "-경기"
    let wins = item.winGameCount ?? 0
    let draws = item.drawnGameCount ?? 0
    let losses = item.loseGameCount ?? 0
    return "\(games) \(wins)승 \(draws)무 \(losses)패"
}

private func formatGameBehind(_ value: Double?) -> String {
    guard let value else { return "-" }
    if value == 0 { return "0" }
    let whole = Int(value)
    if value == Double(whole) { return "\(whole)" }
    return String(format: "%.1f", value)
}

private func formatScheduleDateTime(_ date: Date, time: String?) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "ko_KR")
    formatter.dateFormat = "M월 d일"
    let dateText = formatter.string(from: date)
    let timeText = (time?.isEmpty ?? true) ? "--:--" : time!
    return "\(dateText) \(timeText)"
}

private func calendarDayLabel(selectedTeam: Team, schedules: [UpcomingGameSchedule]) -> ScheduleCalendarDayLabel? {
    guard !schedules.isEmpty else { return nil }
    if schedules.count > 1 {
        return ScheduleCalendarDayLabel(text: "\(schedules.count)경기", color: AppColors.yellow500)
    }

    let game = schedules[0].game
    switch game.status {
    case .finished:
        return myTeamResultLabel(selectedTeam: selectedTeam, game: game)
    case .live:
        return ScheduleCalendarDayLabel(text: "LIVE", color: AppColors.red500)
    case .canceled:
        return ScheduleCalendarDayLabel(text: "취소", color: AppColors.gray400)
    case .postponed:
        return ScheduleCalendarDayLabel(text: "연기", color: AppColors.yellow500)
    case .scheduled:
        let opponent = game.homeTeamId == selectedTeam ? game.awayTeamId : game.homeTeamId
        return ScheduleCalendarDayLabel(text: "vs", color: AppColors.gray100, opponentTeam: opponent)
    }
}

private func myTeamResultLabel(selectedTeam: Team, game: Game) -> ScheduleCalendarDayLabel? {
    guard game.status == .finished else { return nil }

    let isHome = game.homeTeamId == selectedTeam
    let myScore = isHome ? game.homeScore : game.awayScore
    let opponentScore = isHome ? game.awayScore : game.homeScore
    if myScore > opponentScore {
        return ScheduleCalendarDayLabel(text: "승", color: AppColors.green500)
    }
    if myScore < opponentScore {
        return ScheduleCalendarDayLabel(text: "패", color: AppColors.red500)
    }
    return ScheduleCalendarDayLabel(text: "무", color: AppColors.gray400)
}

private func myTeamScoreText(selectedTeam: Team, game: Game) -> String? {
    guard game.status == .finished else { return nil }

    let isHome = game.homeTeamId == selectedTeam
    let myScore = isHome ? game.homeScore : game.awayScore
    let opponentScore = isHome ? game.awayScore : game.homeScore
    return "\(myScore) : \(opponentScore)"
}

private func stadiumName(forHomeTeam team: Team) -> String {
    switch team {
    case .doosan, .lg:
        return "잠실야구장"
    case .kiwoom:
        return "고척스카이돔"
    case .ssg:
        return "인천SSG랜더스필드"
    case .kt:
        return "수원KT위즈파크"
    case .hanwha:
        return "대전한화생명이글스파크"
    case .samsung:
        return "대구삼성라이온즈파크"
    case .lotte:
        return "사직야구장"
    case .kia:
        return "광주기아챔피언스필드"
    case .nc:
        return "창원NC파크"
    case .none:
        return "오늘 경기장"
    }
}

private func formatScheduleDate(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "ko_KR")
    formatter.dateFormat = "M월 d일 (E)"
    return formatter.string(from: date)
}

private func formatScheduleMonth(_ date: Date) -> String {
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "ko_KR")
    formatter.dateFormat = "yyyy년 M월"
    return formatter.string(from: date)
}

private func monthStart(for date: Date) -> Date {
    let calendar = Calendar.current
    let components = calendar.dateComponents([.year, .month], from: date)
    return calendar.date(from: components) ?? calendar.startOfDay(for: date)
}

private func monthGridDates(for month: Date) -> [Date?] {
    let calendar = Calendar.current
    let start = monthStart(for: month)
    guard let range = calendar.range(of: .day, in: .month, for: start) else { return [] }
    let weekday = calendar.component(.weekday, from: start)
    var dates = Array<Date?>(repeating: nil, count: weekday - 1)
    for day in range {
        if let date = calendar.date(byAdding: .day, value: day - 1, to: start) {
            dates.append(calendar.startOfDay(for: date))
        }
    }
    while dates.count % 7 != 0 {
        dates.append(nil)
    }
    return dates
}

private func scheduleSeasonEndDate(_ today: Date) -> Date {
    let calendar = Calendar.current
    let year = calendar.component(.year, from: today)
    let septemberEnd = calendar.date(from: DateComponents(year: year, month: 9, day: 30)) ?? today
    if calendar.startOfDay(for: today) <= septemberEnd {
        return septemberEnd
    }
    return calendar.date(byAdding: .day, value: 30, to: today) ?? today
}

private func scheduleSeasonStartDate(_ today: Date) -> Date {
    let calendar = Calendar.current
    let year = calendar.component(.year, from: today)
    return calendar.date(from: DateComponents(year: year, month: 3, day: 1)) ?? monthStart(for: today)
}

private func teamFromKboTeamId(_ teamId: String) -> Team? {
    switch teamId.uppercased() {
    case "OB": return .doosan
    case "LG": return .lg
    case "WO": return .kiwoom
    case "SS": return .samsung
    case "LT": return .lotte
    case "SK": return .ssg
    case "KT": return .kt
    case "HH": return .hanwha
    case "HT": return .kia
    case "NC": return .nc
    default: return nil
    }
}
