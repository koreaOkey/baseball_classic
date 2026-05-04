import SwiftUI

// TODO(stadium-cheer): 활성화 시 mock 데이터를 백엔드 GET /rankings/teams 응답으로 교체.
struct TeamCheckinRankingView: View {
    let selectedTeam: Team

    @State private var period: RankingPeriod = .season

    enum RankingPeriod: String, CaseIterable, Identifiable {
        case season
        case weekly
        var id: String { rawValue }
        var label: String {
            switch self {
            case .season: return "시즌"
            case .weekly: return "주간"
            }
        }
    }

    private struct CheerRankRow: Identifiable {
        let rank: Int
        let team: Team
        let count: Int
        var id: String { team.rawValue }
    }

    private var rows: [CheerRankRow] {
        let order: [Team] = [.doosan, .lg, .kia, .samsung, .lotte, .ssg, .hanwha, .nc, .kt, .kiwoom]
        return order.enumerated().map { idx, team in
            CheerRankRow(rank: idx + 1, team: team, count: 0)
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
    }

    @ViewBuilder
    private func rowView(_ row: CheerRankRow) -> some View {
        let isMine = row.team == selectedTeam
        HStack {
            Text("#\(row.rank)")
                .font(AppFont.label)
                .foregroundColor(AppColors.gray300)
                .frame(width: 36, alignment: .leading)
            Text("\(row.team.rawValue) \(row.team.teamName)")
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
}
