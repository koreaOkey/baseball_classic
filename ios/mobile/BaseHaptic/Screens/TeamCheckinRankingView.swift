import SwiftUI

struct TeamCheckinRankingView: View {
    let selectedTeam: Team

    @State private var period: RankingPeriod = .weekly
    @State private var fetchedRows: [TeamCheckinRank] = []

    enum RankingPeriod: String, CaseIterable, Identifiable {
        case weekly
        case season
        var id: String { rawValue }
        var label: String {
            switch self {
            case .weekly: return "주간"
            case .season: return "시즌"
            }
        }
    }

    private var rows: [TeamCheckinRank] {
        let order: [Team] = [.doosan, .lg, .kia, .samsung, .lotte, .ssg, .hanwha, .nc, .kt, .kiwoom]
        if fetchedRows.isEmpty {
            return order.enumerated().map { idx, team in
                TeamCheckinRank(rank: idx + 1, team: team, count: 0)
            }
        }
        let fetchedByTeam = Dictionary(uniqueKeysWithValues: fetchedRows.map { ($0.team, $0) })
        return order.enumerated().map { idx, team in
            fetchedByTeam[team] ?? TeamCheckinRank(rank: idx + 1, team: team, count: 0)
        }.sorted { lhs, rhs in
            if lhs.count == rhs.count { return lhs.rank < rhs.rank }
            return lhs.count > rhs.count
        }.enumerated().map { idx, row in
            TeamCheckinRank(rank: idx + 1, team: row.team, count: row.count)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            HStack(spacing: AppSpacing.sm) {
                ForEach(RankingPeriod.allCases) { p in
                    Button(action: { period = p }) {
                        Text(p.label)
                            .font(AppFont.label)
                            .foregroundColor(.white)
                            .padding(.horizontal, AppSpacing.md)
                            .padding(.vertical, 6)
                            .background(period == p ? AppColors.gray700 : AppColors.gray800)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            Text("iOS · Android 합산 집계")
                .font(.system(size: 11))
                .foregroundColor(AppColors.gray300)
            VStack(spacing: 8) {
                ForEach(rows) { row in
                    rowView(row)
                }
            }
        }
        .task(id: period) {
            await loadRankings()
        }
    }

    @ViewBuilder
    private func rowView(_ row: TeamCheckinRank) -> some View {
        let isMine = row.team == selectedTeam
        HStack {
            Text("#\(row.rank)")
                .font(AppFont.label)
                .foregroundColor(AppColors.gray300)
                .frame(width: 36, alignment: .leading)
            Text(row.team.teamName)
                .font(isMine ? AppFont.labelBold : AppFont.label)
                .foregroundColor(.white)
            Spacer()
            Text("\(row.count)회")
                .font(AppFont.label)
                .foregroundColor(AppColors.gray300)
        }
        .padding(AppSpacing.md)
        .background(isMine ? row.team.color.opacity(0.35) : AppColors.gray800)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func loadRankings() async {
        if let rows = await CheerSignalsLoader.shared.fetchTeamRankings(period: period.rawValue) {
            fetchedRows = rows
        }
    }
}
