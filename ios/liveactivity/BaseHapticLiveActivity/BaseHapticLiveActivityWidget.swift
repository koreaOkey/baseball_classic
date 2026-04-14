import ActivityKit
import SwiftUI
import WidgetKit

struct BaseHapticLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: BaseballGameAttributes.self) { context in
            // 잠금화면 Live Activity
            LockScreenLiveActivityView(
                attributes: context.attributes,
                state: context.state
            )
            .activityBackgroundTint(Color.black.opacity(0.85))
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded
                DynamicIslandExpandedRegion(.leading) {
                    teamScoreColumn(
                        team: Team(rawValue: context.attributes.awayTeam) ?? .none,
                        score: context.state.awayScore,
                        isMyTeam: context.attributes.awayTeam == context.attributes.myTeam
                    )
                }
                DynamicIslandExpandedRegion(.trailing) {
                    teamScoreColumn(
                        team: Team(rawValue: context.attributes.homeTeam) ?? .none,
                        score: context.state.homeScore,
                        isMyTeam: context.attributes.homeTeam == context.attributes.myTeam
                    )
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.state.inning)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: AppSpacing.lg) {
                        BSOCountView(
                            balls: context.state.ball,
                            strikes: context.state.strike,
                            outs: context.state.out
                        )
                        BaseDiamondView(
                            first: context.state.baseFirst,
                            second: context.state.baseSecond,
                            third: context.state.baseThird
                        )
                    }
                    .padding(.top, AppSpacing.xs)
                }
            } compactLeading: {
                // Compact Leading: 원정팀 점수
                let away = Team(rawValue: context.attributes.awayTeam) ?? .none
                HStack(spacing: 3) {
                    Text(away.shortName)
                        .font(.caption2)
                        .fontWeight(.bold)
                    Text("\(context.state.awayScore)")
                        .font(.caption)
                        .fontWeight(.semibold)
                }
                .foregroundStyle(.white)
            } compactTrailing: {
                // Compact Trailing: 홈팀 점수
                let home = Team(rawValue: context.attributes.homeTeam) ?? .none
                HStack(spacing: 3) {
                    Text(home.shortName)
                        .font(.caption2)
                        .fontWeight(.bold)
                    Text("\(context.state.homeScore)")
                        .font(.caption)
                        .fontWeight(.semibold)
                }
                .foregroundStyle(.white)
            } minimal: {
                // Minimal: 점수만
                Text("\(context.state.awayScore)-\(context.state.homeScore)")
                    .font(.caption2)
                    .fontWeight(.bold)
            }
        }
    }

    @ViewBuilder
    private func teamScoreColumn(team: Team, score: Int, isMyTeam: Bool) -> some View {
        VStack(spacing: 2) {
            Text(team.shortName)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(isMyTeam ? team.color : .white)
            Text("\(score)")
                .font(.title2)
                .fontWeight(.heavy)
                .foregroundStyle(.white)
        }
    }
}

// MARK: - Lock Screen View

struct LockScreenLiveActivityView: View {
    let attributes: BaseballGameAttributes
    let state: BaseballGameAttributes.ContentState

    private var awayTeam: Team { Team(rawValue: attributes.awayTeam) ?? .none }
    private var homeTeam: Team { Team(rawValue: attributes.homeTeam) ?? .none }
    private var isMyTeamHome: Bool { attributes.homeTeam == attributes.myTeam }

    var body: some View {
        VStack(spacing: AppSpacing.sm) {
            // 상단: 팀 + 점수
            HStack {
                // 원정팀
                teamRow(team: awayTeam, score: state.awayScore, isMyTeam: !isMyTeamHome)

                Spacer()

                // 이닝
                VStack(spacing: AppSpacing.xxs) {
                    Text(state.inning)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundStyle(.secondary)

                    if let event = state.lastEventType {
                        Text(eventLabel(event))
                            .font(AppFont.liveActivity10Bold)
                            .foregroundStyle(AppEventColors.color(for: event))
                    }
                }

                Spacer()

                // 홈팀
                teamRow(team: homeTeam, score: state.homeScore, isMyTeam: isMyTeamHome)
            }

            // 하단: BSO + 베이스 + 투수/타자
            HStack(spacing: AppSpacing.md) {
                BSOCountView(
                    balls: state.ball,
                    strikes: state.strike,
                    outs: state.out
                )

                BaseDiamondView(
                    first: state.baseFirst,
                    second: state.baseSecond,
                    third: state.baseThird
                )

                Spacer()

                VStack(alignment: .trailing, spacing: AppSpacing.xxs) {
                    if !state.pitcher.isEmpty {
                        HStack(spacing: AppSpacing.xs) {
                            Text("P")
                                .font(AppFont.liveActivity9Bold)
                                .foregroundStyle(.secondary)
                            Text(state.pitcher)
                                .font(AppFont.liveActivity11)
                                .foregroundStyle(.white)
                        }
                    }
                    if !state.batter.isEmpty {
                        HStack(spacing: AppSpacing.xs) {
                            Text("B")
                                .font(AppFont.liveActivity9Bold)
                                .foregroundStyle(.secondary)
                            Text(state.batter)
                                .font(AppFont.liveActivity11)
                                .foregroundStyle(.white)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, AppSpacing.md)
    }

    @ViewBuilder
    private func teamRow(team: Team, score: Int, isMyTeam: Bool) -> some View {
        HStack(spacing: AppSpacing.sm) {
            if isMyTeam {
                Circle()
                    .fill(team.color)
                    .frame(width: 6, height: 6)
            }

            Text(team.shortName)
                .font(AppFont.liveActivity15Bold)
                .foregroundStyle(isMyTeam ? team.color : .white)

            Text("\(score)")
                .font(AppFont.liveActivity24Heavy)
                .foregroundStyle(.white)
                .monospacedDigit()
        }
    }

    private func eventLabel(_ type: String) -> String {
        switch type.uppercased() {
        case "HOMERUN": return "홈런!"
        case "HIT": return "안타"
        case "SCORE": return "득점"
        case "WALK": return "볼넷"
        case "STEAL": return "도루"
        case "OUT": return "아웃"
        case "DOUBLE_PLAY": return "병살"
        case "STRIKE": return "스트라이크"
        case "BALL": return "볼"
        default: return type
        }
    }
}

// MARK: - BSO Count View

struct BSOCountView: View {
    let balls: Int
    let strikes: Int
    let outs: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            countRow(label: "B", count: balls, max: 4, color: AppColors.green500)
            countRow(label: "S", count: strikes, max: 3, color: AppColors.yellow400)
            countRow(label: "O", count: outs, max: 3, color: AppColors.red500)
        }
    }

    @ViewBuilder
    private func countRow(label: String, count: Int, max: Int, color: Color) -> some View {
        HStack(spacing: 3) {
            Text(label)
                .font(AppFont.liveActivity9Bold)
                .foregroundStyle(.secondary)
                .frame(width: 10)
            ForEach(0..<max, id: \.self) { i in
                Circle()
                    .fill(i < count ? color : AppColors.gray500.opacity(0.3))
                    .frame(width: 7, height: 7)
            }
        }
    }
}

// MARK: - Base Diamond View

struct BaseDiamondView: View {
    let first: Bool
    let second: Bool
    let third: Bool

    var body: some View {
        let size: CGFloat = 28
        let baseSize: CGFloat = 8

        ZStack {
            // 2루 (상단)
            baseMark(occupied: second, size: baseSize)
                .offset(y: -size / 2)
            // 3루 (좌측)
            baseMark(occupied: third, size: baseSize)
                .offset(x: -size / 2)
            // 1루 (우측)
            baseMark(occupied: first, size: baseSize)
                .offset(x: size / 2)
        }
        .frame(width: size + baseSize, height: size + baseSize)
    }

    @ViewBuilder
    private func baseMark(occupied: Bool, size: CGFloat) -> some View {
        Diamond()
            .fill(occupied ? AppColors.yellow400 : AppColors.gray500.opacity(0.3))
            .frame(width: size, height: size)
    }
}

// MARK: - Diamond Shape

struct Diamond: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.closeSubpath()
        return path
    }
}

// MARK: - Team Extension (Widget용 약칭)

extension Team {
    var shortName: String {
        switch self {
        case .none: return "-"
        case .doosan: return "두산"
        case .lg: return "LG"
        case .kiwoom: return "키움"
        case .samsung: return "삼성"
        case .lotte: return "롯데"
        case .ssg: return "SSG"
        case .kt: return "KT"
        case .hanwha: return "한화"
        case .kia: return "KIA"
        case .nc: return "NC"
        }
    }
}
