import SwiftUI

/// 팀 로고 컴포넌트 (텍스트 기반 플레이스홀더)
struct TeamLogo: View {
    let team: Team
    var size: CGFloat = 40

    var body: some View {
        ZStack {
            Circle()
                .fill(team.color)
                .frame(width: size, height: size)
            Text(teamInitial)
                .font(.system(size: size * 0.4, weight: .bold))
                .foregroundColor(.white)
        }
    }

    private var teamInitial: String {
        switch team {
        case .none: return "?"
        case .doosan: return "두"
        case .lg: return "LG"
        case .kiwoom: return "키"
        case .samsung: return "삼"
        case .lotte: return "롯"
        case .ssg: return "SS"
        case .kt: return "KT"
        case .hanwha: return "한"
        case .kia: return "KI"
        case .nc: return "NC"
        }
    }
}
