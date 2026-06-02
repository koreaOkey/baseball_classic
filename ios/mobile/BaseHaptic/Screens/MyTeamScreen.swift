import SwiftUI
import CoreLocation

struct MyTeamScreen: View {
    let selectedTeam: Team
    let todayGames: [Game]
    let checkinStadium: Stadium?
    let currentLocation: CLLocation?
    let onConfirmCheckin: () -> Void
    let onDismissCheckin: () -> Void
    @State private var activeInfoPopup: MyTeamInfoPopup?
    @State private var localCheckinSnapshot = LocalCheckinSnapshot.empty
    @State private var showVenueMismatchAlert = false

    private var hasTeam: Bool {
        selectedTeam != .none
    }

    private var checkinState: CheerCheckinState {
        if !hasTeam { return .permissionNeeded }
        if localCheckinSnapshot.checkedInToday { return .checkedIn }
        if todayGames.checkinGame(for: selectedTeam) == nil { return .noGameToday }
        if isOutsideCheckinVenue { return .outsideVenue }
        return .ready
    }

    private var checkinVenue: CheckinVenue {
        if let checkinStadium {
            return CheckinVenue(
                name: checkinStadium.name,
                region: StadiumDirectory.region(forCode: checkinStadium.code),
                location: CLLocation(latitude: checkinStadium.latitude, longitude: checkinStadium.longitude)
            )
        }
        return todayGames.checkinVenue(for: selectedTeam) ?? CheckinVenue(name: "오늘 경기장", region: "경기 일정 확인 중")
    }

    private var isOutsideCheckinVenue: Bool {
        guard let currentLocation,
              let venueLocation = checkinVenue.location else { return false }
        return currentLocation.distance(from: venueLocation) > 500
    }

    var body: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: AppSpacing.lg) {
                    MyTeamHeader(
                        selectedTeam: selectedTeam,
                        activeInfoPopup: activeInfoPopup,
                        onInfoTap: { activeInfoPopup = $0 }
                    )
                        .padding(.top, AppSpacing.lg)

                    CheerCheckinCard(
                        stadiumName: checkinVenue.name,
                        stadiumRegion: checkinVenue.region,
                        teamLabel: hasTeam ? selectedTeam.teamName : "응원팀",
                        state: checkinState,
                        onConfirm: {
                            if checkinState == .outsideVenue {
                                showVenueMismatchAlert = true
                            } else {
                                recordLocalCheckin()
                                onConfirmCheckin()
                            }
                        },
                        onDismiss: onDismissCheckin
                    )

                    VStack(alignment: .leading, spacing: AppSpacing.md) {
                        SectionTitle(
                            icon: "trophy.fill",
                            title: "팀 체크인 랭킹",
                            subtitle: "직관 인증으로 쌓이는 팬덤 순위"
                        )
                        TeamCheckinRankingView(
                            selectedTeam: selectedTeam,
                            localWeeklyBoost: localCheckinSnapshot.checkedInToday ? 1 : 0
                        )
                    }
                    .padding(AppSpacing.lg)
                    .background(AppColors.gray900)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                    MyCheckinSummaryCard(
                        enabled: hasTeam,
                        checkedInToday: localCheckinSnapshot.checkedInToday,
                        weeklyCount: localCheckinSnapshot.weeklyCount,
                        seasonCount: localCheckinSnapshot.seasonCount,
                        lastVenue: localCheckinSnapshot.lastVenue
                    )

                    Spacer().frame(height: AppSpacing.bottomSafeSpacer)
                }
                .padding(.horizontal, AppSpacing.xxl)
            }
        }
        .sheet(item: $activeInfoPopup) { popup in
            MyTeamInfoPopupView(popup: popup, selectedTeam: selectedTeam)
                .presentationDetents([.height(220)])
                .presentationDragIndicator(.visible)
        }
        .onAppear {
            loadLocalCheckinState()
        }
        .onChange(of: selectedTeam) { _, _ in
            loadLocalCheckinState()
        }
        .onChange(of: checkinVenue.name) { _, _ in
            loadLocalCheckinState()
        }
        .alert("체크인할 수 없습니다", isPresented: $showVenueMismatchAlert) {
            Button("확인", role: .cancel) {}
        } message: {
            Text("\(selectedTeam.teamName) 경기 구장이 아닙니다.")
        }
    }

    private func loadLocalCheckinState() {
        localCheckinSnapshot = Self.loadLocalCheckinSnapshot(for: selectedTeam, today: Date())
    }

    private func recordLocalCheckin() {
        guard hasTeam, !localCheckinSnapshot.checkedInToday else { return }
        localCheckinSnapshot = Self.recordLocalCheckin(
            for: selectedTeam,
            venueName: checkinVenue.name,
            today: Date()
        )
    }

    private static func loadLocalCheckinSnapshot(for team: Team, today: Date) -> LocalCheckinSnapshot {
        guard team != .none else { return .empty }
        let defaults = UserDefaults.standard
        let dates = defaults.stringArray(forKey: localCheckinDatesKey(team)) ?? []
        let todayString = localCheckinDateFormatter.string(from: today)
        let parsedDates = dates.compactMap { localCheckinDateFormatter.date(from: $0) }
        let weeklyCount = parsedDates.filter {
            Calendar.current.isDate($0, equalTo: today, toGranularity: .weekOfYear)
        }.count

        return LocalCheckinSnapshot(
            checkedInToday: dates.contains(todayString),
            weeklyCount: weeklyCount,
            seasonCount: dates.count,
            lastVenue: defaults.string(forKey: localCheckinLastVenueKey(team)) ?? ""
        )
    }

    private static func recordLocalCheckin(for team: Team, venueName: String, today: Date) -> LocalCheckinSnapshot {
        guard team != .none else { return .empty }
        let defaults = UserDefaults.standard
        let todayString = localCheckinDateFormatter.string(from: today)
        var dates = defaults.stringArray(forKey: localCheckinDatesKey(team)) ?? []
        if !dates.contains(todayString) {
            dates.append(todayString)
        }
        defaults.set(dates, forKey: localCheckinDatesKey(team))
        defaults.set(venueName, forKey: localCheckinLastVenueKey(team))
        return loadLocalCheckinSnapshot(for: team, today: today)
    }

    private static func localCheckinDatesKey(_ team: Team) -> String {
        "my_team_local_checkin_dates_\(team.rawValue)"
    }

    private static func localCheckinLastVenueKey(_ team: Team) -> String {
        "my_team_local_checkin_last_venue_\(team.rawValue)"
    }

    private static let localCheckinDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

private struct LocalCheckinSnapshot {
    let checkedInToday: Bool
    let weeklyCount: Int
    let seasonCount: Int
    let lastVenue: String

    static let empty = LocalCheckinSnapshot(
        checkedInToday: false,
        weeklyCount: 0,
        seasonCount: 0,
        lastVenue: ""
    )
}

private struct MyTeamHeader: View {
    let selectedTeam: Team
    let activeInfoPopup: MyTeamInfoPopup?
    let onInfoTap: (MyTeamInfoPopup) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.lg) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: AppSpacing.xs) {
                    Text("내 팀")
                        .font(AppFont.h2)
                        .foregroundColor(.white)
                    Text("체크인, 워치 응원, 랭킹을 한 곳에서 관리해요")
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                }
                Spacer()
                Text(selectedTeam == .none ? "응원팀 미설정" : selectedTeam.teamName)
                    .font(AppFont.captionBold)
                    .foregroundColor(.white)
                    .padding(.horizontal, AppSpacing.md)
                    .padding(.vertical, AppSpacing.sm)
                    .background(selectedTeam.color.opacity(0.24))
                    .clipShape(Capsule())
            }

            HStack(spacing: AppSpacing.sm) {
                StatusPill(
                    icon: "mappin.and.ellipse",
                    text: "구장 체크인",
                    selected: activeInfoPopup == .checkin,
                    action: { onInfoTap(.checkin) }
                )
                StatusPill(
                    icon: "applewatch",
                    text: "워치 응원",
                    selected: activeInfoPopup == .watchCheer,
                    action: { onInfoTap(.watchCheer) }
                )
                StatusPill(
                    icon: "trophy.fill",
                    text: "랭킹",
                    selected: activeInfoPopup == .ranking,
                    action: { onInfoTap(.ranking) }
                )
            }
            .padding(AppSpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(AppColors.gray900)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
}

private enum MyTeamInfoPopup: Identifiable {
    case checkin
    case watchCheer
    case ranking

    var id: String {
        switch self {
        case .checkin: return "checkin"
        case .watchCheer: return "watchCheer"
        case .ranking: return "ranking"
        }
    }
}

private struct CheckinVenue {
    let name: String
    let region: String
    var location: CLLocation? = nil
}

private extension Array where Element == Game {
    func checkinGame(for selectedTeam: Team) -> Game? {
        guard selectedTeam != .none else { return nil }
        return first { game in
            game.status != .canceled &&
                game.status != .postponed &&
                (game.homeTeamId == selectedTeam || game.awayTeamId == selectedTeam)
        }
    }

    func checkinVenue(for selectedTeam: Team) -> CheckinVenue? {
        checkinGame(for: selectedTeam)?.homeTeamId.homeVenue
    }
}

private extension Team {
    var homeVenue: CheckinVenue {
        switch self {
        case .doosan, .lg:
            return CheckinVenue(name: "잠실야구장", region: "서울", location: CLLocation(latitude: 37.5121, longitude: 127.0719))
        case .kiwoom:
            return CheckinVenue(name: "고척스카이돔", region: "서울", location: CLLocation(latitude: 37.4982, longitude: 126.8670))
        case .ssg:
            return CheckinVenue(name: "인천SSG랜더스필드", region: "서울시 세종대로 67", location: CLLocation(latitude: 37.5628, longitude: 126.9752))
        case .kt:
            return CheckinVenue(name: "수원KT위즈파크", region: "수원", location: CLLocation(latitude: 37.2997, longitude: 127.0097))
        case .hanwha:
            return CheckinVenue(name: "대전한화생명이글스파크", region: "대전", location: CLLocation(latitude: 36.3170, longitude: 127.4291))
        case .samsung:
            return CheckinVenue(name: "대구삼성라이온즈파크", region: "대구", location: CLLocation(latitude: 35.8411, longitude: 128.6817))
        case .lotte:
            return CheckinVenue(name: "사직야구장", region: "부산", location: CLLocation(latitude: 35.1939, longitude: 129.0617))
        case .kia:
            return CheckinVenue(name: "광주기아챔피언스필드", region: "광주", location: CLLocation(latitude: 35.1681, longitude: 126.8889))
        case .nc:
            return CheckinVenue(name: "창원NC파크", region: "창원", location: CLLocation(latitude: 35.2225, longitude: 128.5822))
        case .none:
            return CheckinVenue(name: "오늘 경기장", region: "지역 확인 중")
        }
    }
}

private struct MyTeamInfoPopupView: View {
    let popup: MyTeamInfoPopup
    let selectedTeam: Team

    var body: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()
            Group {
                switch popup {
                case .checkin:
                    CheckinInfoCard(selectedTeam: selectedTeam)
                case .watchCheer:
                    WatchCheerPreviewCard(selectedTeam: selectedTeam)
                case .ranking:
                    RankingInfoCard(selectedTeam: selectedTeam)
                }
            }
            .padding(AppSpacing.xxl)
        }
    }
}

private struct CheckinInfoCard: View {
    let selectedTeam: Team

    private var teamLabel: String {
        selectedTeam == .none ? "응원팀" : selectedTeam.teamName
    }

    var body: some View {
        InfoCard(
            icon: "mappin.and.ellipse",
            title: "구장 체크인 안내",
            subtitle: "오늘 직관 인증은 경기장 근처에서 위치 확인 후 진행돼요",
            bodyText: "\(teamLabel) 경기 당일 구장 반경 안에 있으면 체크인할 수 있고, 인증 결과는 팀 랭킹에 반영됩니다."
        )
    }
}

private struct RankingInfoCard: View {
    let selectedTeam: Team

    private var teamLabel: String {
        selectedTeam == .none ? "내 팀" : selectedTeam.teamName
    }

    var body: some View {
        InfoCard(
            icon: "trophy.fill",
            title: "랭킹 안내",
            subtitle: "팬들의 직관 인증을 모아 주간·시즌 순위를 보여줘요",
            bodyText: "\(teamLabel)의 위치를 강조해서 보여주고, iOS와 Android 체크인 기록을 합산해 집계합니다."
        )
    }
}

private struct InfoCard: View {
    let icon: String
    let title: String
    let subtitle: String
    let bodyText: String

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            SectionTitle(icon: icon, title: title, subtitle: subtitle)
            Text(bodyText)
                .font(AppFont.body)
                .foregroundColor(AppColors.gray300)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct WatchCheerPreviewCard: View {
    let selectedTeam: Team

    private var teamLabel: String {
        selectedTeam == .none ? "응원팀" : selectedTeam.teamName
    }

    var body: some View {
        HStack(spacing: AppSpacing.lg) {
            ZStack {
                RoundedRectangle(cornerRadius: 24)
                    .fill(
                        LinearGradient(
                            colors: [
                                selectedTeam.color.opacity(0.95),
                                selectedTeam.color.opacity(0.62),
                                AppColors.gray800
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                VStack(spacing: AppSpacing.xs) {
                    Image(systemName: "bell.and.waves.left.and.right.fill")
                        .font(AppFont.h4)
                    Text("같이")
                        .font(AppFont.microBold)
                    Text("응원!")
                        .font(AppFont.microBold)
                }
                .foregroundColor(.white)
            }
            .frame(width: 76, height: 96)

            VStack(alignment: .leading, spacing: AppSpacing.md) {
                SectionTitle(
                    icon: "applewatch",
                    title: "워치 응원 안내",
                    subtitle: "체크인과 별개로 경기 시작 시 워치에서만 울려요"
                )
                Text("\(teamLabel) 팬들, 지금 함께 응원해요!")
                    .font(AppFont.bodyLgBold)
                    .foregroundColor(.white)
                Text("예정 시각 18:30 · 강한 햅틱 3회")
                    .font(AppFont.caption)
                    .foregroundColor(AppColors.gray400)
            }
            Spacer(minLength: 0)
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct MyCheckinSummaryCard: View {
    let enabled: Bool
    let checkedInToday: Bool
    let weeklyCount: Int
    let seasonCount: Int
    let lastVenue: String

    private var summaryText: String {
        if !enabled {
            return "응원팀을 선택하면 체크인 기록을 모을 수 있어요"
        }
        if checkedInToday {
            let venueText = lastVenue.isEmpty ? "오늘 경기장" : lastVenue
            return "\(venueText) 체크인 완료. 앱을 다시 켜도 오늘은 완료 상태로 유지돼요"
        }
        return "오늘 체크인하면 주간 랭킹과 내 직관 기록에 바로 반영돼요"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("내 직관 기록")
                .font(AppFont.h5Bold)
                .foregroundColor(.white)
            Text(summaryText)
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
            HStack(spacing: AppSpacing.sm) {
                SummaryMetric(label: "이번 주", value: "\(weeklyCount)회")
                SummaryMetric(label: "시즌", value: "\(seasonCount)회")
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

private struct SummaryMetric: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.xs) {
            Text(label)
                .font(AppFont.tiny)
                .foregroundColor(AppColors.gray500)
            Text(value)
                .font(AppFont.h5Bold)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(AppSpacing.md)
        .background(AppColors.gray800)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct SectionTitle: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: AppSpacing.sm) {
            Image(systemName: icon)
                .font(AppFont.bodyLg)
                .foregroundColor(AppColors.blue400)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(AppFont.h5Bold)
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray400)
            }
        }
    }
}

private struct StatusPill: View {
    let icon: String
    let text: String
    var selected: Bool = false
    var action: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: AppSpacing.xs) {
            Image(systemName: icon)
                .font(AppFont.tinyBold)
            Text(text)
                .font(AppFont.tinyBold)
        }
        .foregroundColor(selected ? .white : AppColors.gray300)
        .padding(.horizontal, AppSpacing.sm)
        .padding(.vertical, AppSpacing.xs)
        .background(selected ? AppColors.blue600.opacity(0.42) : AppColors.gray800)
        .clipShape(Capsule())
        .contentShape(Capsule())
        .onTapGesture {
            action?()
        }
    }
}
