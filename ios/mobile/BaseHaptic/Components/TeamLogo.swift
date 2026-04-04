import SwiftUI

/// 팀 로고 컴포넌트 (이미지 기반, 폴백으로 텍스트 사용)
struct TeamLogo: View {
    let team: Team
    var size: CGFloat = 40

    var body: some View {
        if let imageName = teamImageName, UIImage(named: imageName) != nil {
            Image(imageName)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            ZStack {
                Circle()
                    .fill(team.color)
                    .frame(width: size, height: size)
                Text(teamInitial)
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundColor(.white)
            }
        }
    }

    private var teamImageName: String? {
        switch team {
        case .none: return nil
        case .doosan: return "team_doosan"
        case .lg: return "team_lg"
        case .kiwoom: return "team_kiwoom"
        case .samsung: return "team_samsung"
        case .lotte: return "team_lotte"
        case .ssg: return "team_ssg"
        case .kt: return "team_kt"
        case .hanwha: return "team_hanwha"
        case .kia: return "team_kia"
        case .nc: return "team_nc"
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
