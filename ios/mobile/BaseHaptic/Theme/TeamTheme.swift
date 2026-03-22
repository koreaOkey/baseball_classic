import SwiftUI

struct TeamTheme {
    let team: Team
    let primary: Color
    let primaryDark: Color
    let secondary: Color
    let accent: Color
    let gradientStart: Color
    let gradientEnd: Color
    let navIndicator: Color
}

enum TeamThemes {
    static let none = TeamTheme(
        team: .none,
        primary: AppColors.blue500,
        primaryDark: AppColors.blue700,
        secondary: AppColors.blue400,
        accent: AppColors.blue200,
        gradientStart: AppColors.blue500,
        gradientEnd: AppColors.blue700,
        navIndicator: AppColors.blue500
    )

    static let doosan = TeamTheme(
        team: .doosan,
        primary: Color(hex: 0x131230),
        primaryDark: Color(hex: 0x0A0918),
        secondary: Color(hex: 0xEF4444),
        accent: Color(hex: 0x60A5FA),
        gradientStart: Color(hex: 0x131230),
        gradientEnd: Color(hex: 0x1E1C4B),
        navIndicator: Color(hex: 0xEF4444)
    )

    static let lg = TeamTheme(
        team: .lg,
        primary: Color(hex: 0xC30452),
        primaryDark: Color(hex: 0x8E023B),
        secondary: Color.black,
        accent: Color(hex: 0xF472B6),
        gradientStart: Color(hex: 0xC30452),
        gradientEnd: Color(hex: 0x8E023B),
        navIndicator: Color(hex: 0xC30452)
    )

    static let kiwoom = TeamTheme(
        team: .kiwoom,
        primary: Color(hex: 0x820024),
        primaryDark: Color(hex: 0x5C001A),
        secondary: Color(hex: 0xD4A843),
        accent: Color(hex: 0xFCA5A5),
        gradientStart: Color(hex: 0x820024),
        gradientEnd: Color(hex: 0x5C001A),
        navIndicator: Color(hex: 0xD4A843)
    )

    static let samsung = TeamTheme(
        team: .samsung,
        primary: Color(hex: 0x074CA1),
        primaryDark: Color(hex: 0x053678),
        secondary: Color.white,
        accent: Color(hex: 0x93C5FD),
        gradientStart: Color(hex: 0x074CA1),
        gradientEnd: Color(hex: 0x053678),
        navIndicator: Color(hex: 0x074CA1)
    )

    static let lotte = TeamTheme(
        team: .lotte,
        primary: Color(hex: 0x041E42),
        primaryDark: Color(hex: 0x021230),
        secondary: Color(hex: 0xE31B23),
        accent: Color(hex: 0x93C5FD),
        gradientStart: Color(hex: 0x041E42),
        gradientEnd: Color(hex: 0x021230),
        navIndicator: Color(hex: 0xE31B23)
    )

    static let ssg = TeamTheme(
        team: .ssg,
        primary: Color(hex: 0xCE0E2D),
        primaryDark: Color(hex: 0x960A20),
        secondary: Color(hex: 0xFFD700),
        accent: Color(hex: 0xFCA5A5),
        gradientStart: Color(hex: 0xCE0E2D),
        gradientEnd: Color(hex: 0x960A20),
        navIndicator: Color(hex: 0xCE0E2D)
    )

    static let kt = TeamTheme(
        team: .kt,
        primary: Color.black,
        primaryDark: Color(hex: 0x1A1A1A),
        secondary: Color(hex: 0xED1C24),
        accent: Color(hex: 0xA3A3A3),
        gradientStart: Color(hex: 0x1A1A1A),
        gradientEnd: Color.black,
        navIndicator: Color(hex: 0xED1C24)
    )

    static let hanwha = TeamTheme(
        team: .hanwha,
        primary: Color(hex: 0xFF6600),
        primaryDark: Color(hex: 0xCC5200),
        secondary: Color.black,
        accent: Color(hex: 0xFDBA74),
        gradientStart: Color(hex: 0xFF6600),
        gradientEnd: Color(hex: 0xCC5200),
        navIndicator: Color(hex: 0xFF6600)
    )

    static let kia = TeamTheme(
        team: .kia,
        primary: Color(hex: 0xEA0029),
        primaryDark: Color(hex: 0xB5001F),
        secondary: Color.black,
        accent: Color(hex: 0xFCA5A5),
        gradientStart: Color(hex: 0xEA0029),
        gradientEnd: Color(hex: 0xB5001F),
        navIndicator: Color(hex: 0xEA0029)
    )

    static let nc = TeamTheme(
        team: .nc,
        primary: Color(hex: 0x315288),
        primaryDark: Color(hex: 0x213A61),
        secondary: Color(hex: 0xCFB53B),
        accent: Color(hex: 0x93C5FD),
        gradientStart: Color(hex: 0x315288),
        gradientEnd: Color(hex: 0x213A61),
        navIndicator: Color(hex: 0x315288)
    )

    static func theme(for team: Team) -> TeamTheme {
        switch team {
        case .none: return none
        case .doosan: return doosan
        case .lg: return lg
        case .kiwoom: return kiwoom
        case .samsung: return samsung
        case .lotte: return lotte
        case .ssg: return ssg
        case .kt: return kt
        case .hanwha: return hanwha
        case .kia: return kia
        case .nc: return nc
        }
    }
}

// MARK: - Environment Key
private struct TeamThemeKey: EnvironmentKey {
    static let defaultValue: TeamTheme = TeamThemes.none
}

extension EnvironmentValues {
    var teamTheme: TeamTheme {
        get { self[TeamThemeKey.self] }
        set { self[TeamThemeKey.self] = newValue }
    }
}
