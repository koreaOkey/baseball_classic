import ActivityKit
import SwiftUI
import WidgetKit

struct BaseHapticLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        let configuration = ActivityConfiguration(for: BaseballGameAttributes.self) { context in
            ActivityContentView(attributes: context.attributes, state: context.state, isStale: context.isStale)
                .activityBackgroundTint(.black.opacity(0.88))
                .activitySystemActionForegroundColor(.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    TeamScoreColumn(
                        code: context.attributes.awayTeam,
                        score: context.state.awayScore,
                        isMyTeam: context.attributes.awayTeam == context.attributes.myTeam
                    )
                }

                DynamicIslandExpandedRegion(.trailing) {
                    TeamScoreColumn(
                        code: context.attributes.homeTeam,
                        score: context.state.homeScore,
                        isMyTeam: context.attributes.homeTeam == context.attributes.myTeam
                    )
                }

                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(context.state.inning.isEmpty ? "LIVE" : context.state.inning)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.secondary)
                        if let type = context.state.lastEventType, !type.isEmpty {
                            Text(eventLabel(type))
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(eventColor(type))
                        }
                    }
                }

                DynamicIslandExpandedRegion(.bottom) {
                    if let highlight = context.state.highlightEventText, !highlight.isEmpty {
                        HighlightEventView(text: highlight, type: context.state.highlightEventType)
                    } else {
                        GameProgressView(
                            state: context.state,
                            diamondSize: CGSize(width: 50, height: 44),
                            compact: true
                        )
                        .padding(.top, 2)
                    }
                }
            } compactLeading: {
                CompactScoreView(
                    code: context.attributes.awayTeam,
                    score: context.state.awayScore
                )
            } compactTrailing: {
                CompactScoreView(
                    code: context.attributes.homeTeam,
                    score: context.state.homeScore
                )
            } minimal: {
                Text("\(context.state.awayScore)-\(context.state.homeScore)")
                    .font(.caption2.weight(.heavy))
                    .monospacedDigit()
                    .foregroundStyle(.white)
            }
        }

        if #available(iOS 18.0, *) {
            return configuration.supplementalActivityFamilies([.small, .medium])
        } else {
            return configuration
        }
    }
}

private struct ActivityContentView: View {
    let attributes: BaseballGameAttributes
    let state: BaseballGameAttributes.ContentState
    let isStale: Bool

    var body: some View {
        if #available(iOS 18.0, *) {
            SupplementalAwareLiveActivityView(attributes: attributes, state: state, isStale: isStale)
        } else {
            LockScreenLiveActivityView(attributes: attributes, state: state, isStale: isStale)
        }
    }
}

@available(iOS 18.0, *)
private struct SupplementalAwareLiveActivityView: View {
    @Environment(\.activityFamily) private var activityFamily

    let attributes: BaseballGameAttributes
    let state: BaseballGameAttributes.ContentState
    let isStale: Bool

    var body: some View {
        switch activityFamily {
        case .small:
            WatchSmallLiveActivityView(attributes: attributes, state: state)
        case .medium:
            LockScreenLiveActivityView(attributes: attributes, state: state, isStale: isStale)
        @unknown default:
            LockScreenLiveActivityView(attributes: attributes, state: state, isStale: isStale)
        }
    }
}

private struct WatchSmallLiveActivityView: View {
    let attributes: BaseballGameAttributes
    let state: BaseballGameAttributes.ContentState

    var body: some View {
        VStack(spacing: 6) {
            Text(state.inning.isEmpty ? "LIVE" : state.inning)
                .font(.caption.weight(.bold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .padding(.top, 6)

            HStack(alignment: .center, spacing: 8) {
                WatchTeamScoreView(
                    code: attributes.awayTeam,
                    score: state.awayScore,
                    alignment: .leading,
                    reversed: false
                )

                Text("-")
                    .font(.title2.weight(.heavy))
                    .foregroundStyle(.secondary)
                    .frame(width: 10)

                WatchTeamScoreView(
                    code: attributes.homeTeam,
                    score: state.homeScore,
                    alignment: .trailing,
                    reversed: true
                )
            }
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 6)
    }
}

private struct WatchTeamScoreView: View {
    let code: String
    let score: Int
    let alignment: HorizontalAlignment
    let reversed: Bool

    var body: some View {
        HStack(spacing: 5) {
            if reversed {
                scoreText
            }

            TeamLogoImage(code: code, size: 32, showsFallback: true)

            if !reversed {
                scoreText
            }
        }
        .frame(maxWidth: .infinity, alignment: frameAlignment)
    }

    private var scoreText: some View {
        Text("\(score)")
            .font(.system(size: 34, weight: .heavy, design: .rounded))
            .monospacedDigit()
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.72)
            .allowsTightening(true)
            .fixedSize(horizontal: false, vertical: true)
            .frame(minHeight: 38, alignment: .center)
    }

    private var frameAlignment: Alignment {
        alignment == .trailing ? .trailing : .leading
    }
}

private struct LockScreenLiveActivityView: View {
    let attributes: BaseballGameAttributes
    let state: BaseballGameAttributes.ContentState
    var isStale: Bool = false

    var body: some View {
        VStack(spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                TeamScoreRow(
                    code: attributes.awayTeam,
                    score: state.awayScore,
                    isMyTeam: attributes.awayTeam == attributes.myTeam
                )

                Spacer(minLength: 8)

                VStack(spacing: 3) {
                    Text(state.inning.isEmpty ? "LIVE" : state.inning)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    if isStale {
                        Text("동기화 지연")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(.orange)
                    } else if let type = state.lastEventType, !type.isEmpty {
                        Text(eventLabel(type))
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(eventColor(type))
                    }
                }

                Spacer(minLength: 8)

                TeamScoreRow(
                    code: attributes.homeTeam,
                    score: state.homeScore,
                    isMyTeam: attributes.homeTeam == attributes.myTeam
                )
            }

            if let highlight = state.highlightEventText, !highlight.isEmpty {
                HighlightEventView(text: highlight, type: state.highlightEventType)
            } else {
                GameProgressView(
                    state: state,
                    diamondSize: CGSize(width: 60, height: 54),
                    compact: false
                )
            }
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
    }
}

private struct GameProgressView: View {
    let state: BaseballGameAttributes.ContentState
    let diamondSize: CGSize
    let compact: Bool

    var body: some View {
        HStack(spacing: 14) {
            BSOCountView(balls: state.ball, strikes: state.strike, outs: state.out)
            BaseDiamondView(first: state.baseFirst, second: state.baseSecond, third: state.baseThird)
                .frame(width: diamondSize.width, height: diamondSize.height)

            VStack(alignment: .leading, spacing: 3) {
                if !state.pitcher.isEmpty || !state.batter.isEmpty {
                    Text(matchupText)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.78)
                }
                Text(state.lastEventDescription?.isEmpty == false ? state.lastEventDescription! : "경기 진행 상황을 업데이트 중입니다")
                    .font(compact ? .caption2.weight(.semibold) : .caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(compact ? 0.72 : 0.8)
            }
            Spacer(minLength: 0)
        }
    }

    private var matchupText: String {
        let pitcher = state.pitcher.isEmpty ? "-" : state.pitcher
        let batter = state.batter.isEmpty ? "-" : state.batter
        return "P \(pitcher)  |  B \(batter)"
    }
}

private struct TeamScoreRow: View {
    let code: String
    let score: Int
    let isMyTeam: Bool

    var body: some View {
        HStack(spacing: 7) {
            if isMyTeam {
                Circle()
                    .fill(teamColor(code))
                    .frame(width: 6, height: 6)
            }
            TeamMascotIcon(code: code, size: 22)
            Text(teamDisplayName(code))
                .font(.subheadline.weight(.bold))
                .foregroundStyle(isMyTeam ? teamColor(code) : .white)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text("\(score)")
                .font(.title2.weight(.heavy))
                .monospacedDigit()
                .foregroundStyle(.white)
        }
    }
}

private struct TeamScoreColumn: View {
    let code: String
    let score: Int
    let isMyTeam: Bool

    var body: some View {
        VStack(spacing: 2) {
            TeamLogoImage(code: code, size: 22, showsFallback: false)
            Text("\(score)")
                .font(.title3.weight(.heavy))
                .monospacedDigit()
                .foregroundStyle(.white)
        }
    }
}

private struct CompactScoreView: View {
    let code: String
    let score: Int

    var body: some View {
        HStack(spacing: 3) {
            TeamLogoImage(code: code, size: 14, showsFallback: false)
            Text("\(score)")
                .font(.caption.weight(.heavy))
                .monospacedDigit()
                .foregroundStyle(.white)
        }
    }
}

private struct HighlightEventView: View {
    let text: String
    let type: String?

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(eventColor(type))
                .frame(width: 8, height: 8)
            Text(text)
                .font(.headline.weight(.heavy))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(eventColor(type).opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct BSOCountView: View {
    let balls: Int
    let strikes: Int
    let outs: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            countRow(label: "B", count: balls, max: 3, color: .green)
            countRow(label: "S", count: strikes, max: 2, color: .yellow)
            countRow(label: "O", count: outs, max: 2, color: .red)
        }
    }

    private func countRow(label: String, count: Int, max: Int, color: Color) -> some View {
        HStack(spacing: 3) {
            Text(label)
                .font(.caption2.weight(.heavy))
                .foregroundStyle(.secondary)
                .frame(width: 10)
            ForEach(0..<max, id: \.self) { index in
                Circle()
                    .fill(index < count ? color : Color.white.opacity(0.22))
                    .frame(width: 7, height: 7)
            }
        }
    }
}

private struct BaseDiamondView: View {
    let first: Bool
    let second: Bool
    let third: Bool

    var body: some View {
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let metric = min(size.width, size.height)
            let offset = metric * 0.23
            let baseSize = metric * 0.46

            drawBase(
                context: context,
                center: CGPoint(x: center.x, y: center.y - offset),
                baseSize: baseSize,
                occupied: second
            )
            drawBase(
                context: context,
                center: CGPoint(x: center.x + offset, y: center.y),
                baseSize: baseSize,
                occupied: first
            )
            drawBase(
                context: context,
                center: CGPoint(x: center.x - offset, y: center.y),
                baseSize: baseSize,
                occupied: third
            )
            drawBase(
                context: context,
                center: CGPoint(x: center.x, y: center.y + offset),
                baseSize: baseSize,
                occupied: false
            )
        }
    }

    private func drawBase(context: GraphicsContext, center: CGPoint, baseSize: CGFloat, occupied: Bool) {
        var path = Path()
        path.move(to: CGPoint(x: center.x, y: center.y - baseSize / 2))
        path.addLine(to: CGPoint(x: center.x + baseSize / 2, y: center.y))
        path.addLine(to: CGPoint(x: center.x, y: center.y + baseSize / 2))
        path.addLine(to: CGPoint(x: center.x - baseSize / 2, y: center.y))
        path.closeSubpath()

        context.fill(path, with: .color(occupied ? Color.yellow : Color.white.opacity(0.22)))
        context.stroke(path, with: .color(Color.black.opacity(0.36)), lineWidth: 1)
    }
}

private struct TeamMascotIcon: View {
    let code: String
    let size: CGFloat

    var body: some View {
        TeamLogoImage(code: code, size: size, showsFallback: true)
    }
}

private struct TeamLogoImage: View {
    let code: String
    let size: CGFloat
    let showsFallback: Bool

    var body: some View {
        if let imageName = teamIconName(code) {
            Image(imageName)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else if showsFallback {
            Circle()
                .fill(teamColor(code).opacity(0.85))
                .frame(width: size, height: size)
                .overlay(
                    Text(String(teamDisplayName(code).prefix(1)))
                        .font(.system(size: size * 0.45, weight: .heavy))
                        .foregroundStyle(.white)
                )
        } else {
            Color.clear
                .frame(width: size, height: size)
        }
    }
}

private func teamDisplayName(_ raw: String) -> String {
    switch raw.uppercased() {
    case "DOOSAN": return "베어스"
    case "LG": return "트윈스"
    case "KIWOOM": return "히어로즈"
    case "SAMSUNG": return "라이온즈"
    case "LOTTE": return "자이언츠"
    case "SSG": return "랜더스"
    case "KT": return "위즈"
    case "HANWHA": return "이글스"
    case "KIA": return "타이거즈"
    case "NC": return "다이노스"
    default: return raw.isEmpty ? "-" : raw
    }
}

private func teamIconName(_ raw: String) -> String? {
    switch raw.uppercased() {
    case "DOOSAN": return "team_doosan"
    case "LG": return "team_lg"
    case "KIWOOM": return "team_kiwoom"
    case "SAMSUNG": return "team_samsung"
    case "LOTTE": return "team_lotte"
    case "SSG": return "team_ssg"
    case "KT": return "team_kt"
    case "HANWHA": return "team_hanwha"
    case "KIA": return "team_kia"
    case "NC": return "team_nc"
    default: return nil
    }
}

private func teamColor(_ raw: String) -> Color {
    switch raw.uppercased() {
    case "DOOSAN": return Color(red: 19/255, green: 18/255, blue: 48/255)
    case "LG": return Color(red: 195/255, green: 4/255, blue: 82/255)
    case "KIWOOM": return Color(red: 130/255, green: 0, blue: 36/255)
    case "SAMSUNG": return Color(red: 7/255, green: 76/255, blue: 161/255)
    case "LOTTE": return Color(red: 4/255, green: 30/255, blue: 66/255)
    case "SSG": return Color(red: 206/255, green: 14/255, blue: 45/255)
    case "KT": return .white
    case "HANWHA": return Color(red: 1, green: 102/255, blue: 0)
    case "KIA": return Color(red: 234/255, green: 0, blue: 41/255)
    case "NC": return Color(red: 49/255, green: 82/255, blue: 136/255)
    default: return Color(red: 96/255, green: 165/255, blue: 250/255)
    }
}

private func eventLabel(_ type: String) -> String {
    switch type.uppercased() {
    case "HOMERUN": return "홈런"
    case "SCORE": return "득점"
    case "HIT": return "안타"
    case "WALK": return "볼넷"
    case "STEAL": return "도루"
    case "OUT": return "아웃"
    case "DOUBLE_PLAY": return "병살"
    case "TRIPLE_PLAY": return "삼중살"
    case "PITCHER_CHANGE": return "투수 교체"
    default: return type
    }
}

private func eventColor(_ type: String?) -> Color {
    switch type?.uppercased() {
    case "HOMERUN": return Color(red: 251/255, green: 146/255, blue: 60/255)
    case "SCORE": return Color(red: 250/255, green: 204/255, blue: 21/255)
    case "HIT": return Color(red: 96/255, green: 165/255, blue: 250/255)
    case "WALK", "STEAL": return Color(red: 74/255, green: 222/255, blue: 128/255)
    case "OUT", "DOUBLE_PLAY", "TRIPLE_PLAY": return Color(red: 248/255, green: 113/255, blue: 113/255)
    default: return Color(red: 250/255, green: 204/255, blue: 21/255)
    }
}
