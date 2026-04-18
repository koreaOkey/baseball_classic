import SwiftUI

struct WatchTeamTheme {
    let teamName: String
    let primary: Color
    let primaryDark: Color
    let secondary: Color
    let accent: Color
    let gradientStart: Color
    let gradientEnd: Color
    let backgroundImage: String?

    init(teamName: String, primary: Color, primaryDark: Color, secondary: Color, accent: Color, gradientStart: Color, gradientEnd: Color, backgroundImage: String? = nil) {
        self.teamName = teamName
        self.primary = primary
        self.primaryDark = primaryDark
        self.secondary = secondary
        self.accent = accent
        self.gradientStart = gradientStart
        self.gradientEnd = gradientEnd
        self.backgroundImage = backgroundImage
    }
}

enum WatchTeamThemes {
    static let defaultTheme = WatchTeamTheme(
        teamName: "DEFAULT",
        primary: WatchColors.blue500,
        primaryDark: WatchColors.blue600,
        secondary: WatchColors.blue400,
        accent: WatchColors.blue400,
        gradientStart: WatchColors.blue500,
        gradientEnd: WatchColors.blue600
    )

    static let doosan = WatchTeamTheme(teamName: "DOOSAN", primary: Color(red: 19/255, green: 18/255, blue: 48/255), primaryDark: Color(red: 10/255, green: 9/255, blue: 24/255), secondary: Color(red: 239/255, green: 68/255, blue: 68/255), accent: Color(red: 96/255, green: 165/255, blue: 250/255), gradientStart: Color(red: 19/255, green: 18/255, blue: 48/255), gradientEnd: Color(red: 30/255, green: 28/255, blue: 75/255))

    static let lg = WatchTeamTheme(teamName: "LG", primary: Color(red: 195/255, green: 4/255, blue: 82/255), primaryDark: Color(red: 142/255, green: 2/255, blue: 59/255), secondary: .black, accent: Color(red: 244/255, green: 114/255, blue: 182/255), gradientStart: Color(red: 195/255, green: 4/255, blue: 82/255), gradientEnd: Color(red: 142/255, green: 2/255, blue: 59/255))

    static let kiwoom = WatchTeamTheme(teamName: "KIWOOM", primary: Color(red: 130/255, green: 0/255, blue: 36/255), primaryDark: Color(red: 92/255, green: 0/255, blue: 26/255), secondary: Color(red: 212/255, green: 168/255, blue: 67/255), accent: Color(red: 252/255, green: 165/255, blue: 165/255), gradientStart: Color(red: 130/255, green: 0/255, blue: 36/255), gradientEnd: Color(red: 92/255, green: 0/255, blue: 26/255))

    static let samsung = WatchTeamTheme(teamName: "SAMSUNG", primary: Color(red: 7/255, green: 76/255, blue: 161/255), primaryDark: Color(red: 5/255, green: 54/255, blue: 120/255), secondary: .white, accent: Color(red: 147/255, green: 197/255, blue: 253/255), gradientStart: Color(red: 7/255, green: 76/255, blue: 161/255), gradientEnd: Color(red: 5/255, green: 54/255, blue: 120/255))

    static let lotte = WatchTeamTheme(teamName: "LOTTE", primary: Color(red: 4/255, green: 30/255, blue: 66/255), primaryDark: Color(red: 2/255, green: 18/255, blue: 48/255), secondary: Color(red: 227/255, green: 27/255, blue: 35/255), accent: Color(red: 147/255, green: 197/255, blue: 253/255), gradientStart: Color(red: 4/255, green: 30/255, blue: 66/255), gradientEnd: Color(red: 2/255, green: 18/255, blue: 48/255))

    static let ssg = WatchTeamTheme(teamName: "SSG", primary: Color(red: 206/255, green: 14/255, blue: 45/255), primaryDark: Color(red: 150/255, green: 10/255, blue: 32/255), secondary: Color(red: 255/255, green: 215/255, blue: 0/255), accent: Color(red: 252/255, green: 165/255, blue: 165/255), gradientStart: Color(red: 206/255, green: 14/255, blue: 45/255), gradientEnd: Color(red: 150/255, green: 10/255, blue: 32/255))

    static let kt = WatchTeamTheme(teamName: "KT", primary: Color(red: 26/255, green: 26/255, blue: 26/255), primaryDark: .black, secondary: Color(red: 237/255, green: 28/255, blue: 36/255), accent: Color(red: 163/255, green: 163/255, blue: 163/255), gradientStart: Color(red: 26/255, green: 26/255, blue: 26/255), gradientEnd: .black)

    static let hanwha = WatchTeamTheme(teamName: "HANWHA", primary: Color(red: 255/255, green: 102/255, blue: 0/255), primaryDark: Color(red: 204/255, green: 82/255, blue: 0/255), secondary: .black, accent: Color(red: 253/255, green: 186/255, blue: 116/255), gradientStart: Color(red: 255/255, green: 102/255, blue: 0/255), gradientEnd: Color(red: 204/255, green: 82/255, blue: 0/255))

    static let kia = WatchTeamTheme(teamName: "KIA", primary: Color(red: 234/255, green: 0/255, blue: 41/255), primaryDark: Color(red: 181/255, green: 0/255, blue: 31/255), secondary: .black, accent: Color(red: 252/255, green: 165/255, blue: 165/255), gradientStart: Color(red: 234/255, green: 0/255, blue: 41/255), gradientEnd: Color(red: 181/255, green: 0/255, blue: 31/255))

    static let nc = WatchTeamTheme(teamName: "NC", primary: Color(red: 49/255, green: 82/255, blue: 136/255), primaryDark: Color(red: 33/255, green: 58/255, blue: 97/255), secondary: Color(red: 207/255, green: 181/255, blue: 59/255), accent: Color(red: 147/255, green: 197/255, blue: 253/255), gradientStart: Color(red: 49/255, green: 82/255, blue: 136/255), gradientEnd: Color(red: 33/255, green: 58/255, blue: 97/255))

    static let puppy = WatchTeamTheme(teamName: "PUPPY", primary: Color(red: 220/255, green: 38/255, blue: 38/255), primaryDark: Color(red: 160/255, green: 20/255, blue: 20/255), secondary: Color(red: 255/255, green: 182/255, blue: 193/255), accent: Color(red: 255/255, green: 182/255, blue: 193/255), gradientStart: Color(red: 220/255, green: 38/255, blue: 38/255), gradientEnd: Color(red: 160/255, green: 20/255, blue: 20/255), backgroundImage: "theme_puppy")

    static let puppy2 = WatchTeamTheme(teamName: "PUPPY2", primary: Color(red: 220/255, green: 38/255, blue: 38/255), primaryDark: Color(red: 160/255, green: 20/255, blue: 20/255), secondary: Color(red: 255/255, green: 182/255, blue: 193/255), accent: Color(red: 255/255, green: 182/255, blue: 193/255), gradientStart: Color(red: 220/255, green: 38/255, blue: 38/255), gradientEnd: Color(red: 160/255, green: 20/255, blue: 20/255), backgroundImage: "theme_puppy2")

    static func theme(for teamName: String) -> WatchTeamTheme {
        switch teamName.uppercased() {
        case "DOOSAN": return doosan
        case "LG": return lg
        case "KIWOOM": return kiwoom
        case "SAMSUNG": return samsung
        case "LOTTE": return lotte
        case "SSG": return ssg
        case "KT": return kt
        case "HANWHA": return hanwha
        case "KIA": return kia
        case "NC": return nc
        default: return defaultTheme
        }
    }

    // MARK: - Store Themes (팀과 무관한 독립 테마)

    static let storeMidnightIndigo = WatchTeamTheme(teamName: "STORE", primary: Color(red: 19/255, green: 18/255, blue: 48/255), primaryDark: Color(red: 10/255, green: 9/255, blue: 24/255), secondary: Color(red: 30/255, green: 28/255, blue: 75/255), accent: Color(red: 96/255, green: 165/255, blue: 250/255), gradientStart: Color(red: 19/255, green: 18/255, blue: 48/255), gradientEnd: Color(red: 10/255, green: 9/255, blue: 24/255))

    static let storeCherryRose = WatchTeamTheme(teamName: "STORE", primary: Color(red: 195/255, green: 4/255, blue: 82/255), primaryDark: Color(red: 142/255, green: 2/255, blue: 59/255), secondary: Color(red: 142/255, green: 2/255, blue: 59/255), accent: Color(red: 244/255, green: 114/255, blue: 182/255), gradientStart: Color(red: 195/255, green: 4/255, blue: 82/255), gradientEnd: Color(red: 142/255, green: 2/255, blue: 59/255))

    static let storeBurgundyGold = WatchTeamTheme(teamName: "STORE", primary: Color(red: 130/255, green: 0/255, blue: 36/255), primaryDark: Color(red: 92/255, green: 0/255, blue: 26/255), secondary: Color(red: 212/255, green: 168/255, blue: 67/255), accent: Color(red: 252/255, green: 165/255, blue: 165/255), gradientStart: Color(red: 130/255, green: 0/255, blue: 36/255), gradientEnd: Color(red: 92/255, green: 0/255, blue: 26/255))

    static let storeRoyalBlue = WatchTeamTheme(teamName: "STORE", primary: Color(red: 7/255, green: 76/255, blue: 161/255), primaryDark: Color(red: 5/255, green: 54/255, blue: 120/255), secondary: Color.white, accent: Color(red: 147/255, green: 197/255, blue: 253/255), gradientStart: Color(red: 7/255, green: 76/255, blue: 161/255), gradientEnd: Color(red: 5/255, green: 54/255, blue: 120/255))

    static let storeDeepNavy = WatchTeamTheme(teamName: "STORE", primary: Color(red: 4/255, green: 30/255, blue: 66/255), primaryDark: Color(red: 2/255, green: 18/255, blue: 48/255), secondary: Color(red: 227/255, green: 27/255, blue: 35/255), accent: Color(red: 147/255, green: 197/255, blue: 253/255), gradientStart: Color(red: 4/255, green: 30/255, blue: 66/255), gradientEnd: Color(red: 2/255, green: 18/255, blue: 48/255))

    static let storeCrimsonRed = WatchTeamTheme(teamName: "STORE", primary: Color(red: 206/255, green: 14/255, blue: 45/255), primaryDark: Color(red: 150/255, green: 10/255, blue: 32/255), secondary: Color(red: 255/255, green: 215/255, blue: 0/255), accent: Color(red: 252/255, green: 165/255, blue: 165/255), gradientStart: Color(red: 206/255, green: 14/255, blue: 45/255), gradientEnd: Color(red: 150/255, green: 10/255, blue: 32/255))

    static let storeCharcoalBlack = WatchTeamTheme(teamName: "STORE", primary: Color(red: 26/255, green: 26/255, blue: 26/255), primaryDark: .black, secondary: Color(red: 237/255, green: 28/255, blue: 36/255), accent: Color(red: 163/255, green: 163/255, blue: 163/255), gradientStart: Color(red: 26/255, green: 26/255, blue: 26/255), gradientEnd: .black)

    static let storeSunsetOrange = WatchTeamTheme(teamName: "STORE", primary: Color(red: 255/255, green: 102/255, blue: 0/255), primaryDark: Color(red: 204/255, green: 82/255, blue: 0/255), secondary: .black, accent: Color(red: 253/255, green: 186/255, blue: 116/255), gradientStart: Color(red: 255/255, green: 102/255, blue: 0/255), gradientEnd: Color(red: 204/255, green: 82/255, blue: 0/255))

    static let storeFireRed = WatchTeamTheme(teamName: "STORE", primary: Color(red: 234/255, green: 0/255, blue: 41/255), primaryDark: Color(red: 181/255, green: 0/255, blue: 31/255), secondary: .black, accent: Color(red: 252/255, green: 165/255, blue: 165/255), gradientStart: Color(red: 234/255, green: 0/255, blue: 41/255), gradientEnd: Color(red: 181/255, green: 0/255, blue: 31/255))

    static let storeSlateBlue = WatchTeamTheme(teamName: "STORE", primary: Color(red: 49/255, green: 82/255, blue: 136/255), primaryDark: Color(red: 33/255, green: 58/255, blue: 97/255), secondary: Color(red: 207/255, green: 181/255, blue: 59/255), accent: Color(red: 147/255, green: 197/255, blue: 253/255), gradientStart: Color(red: 49/255, green: 82/255, blue: 136/255), gradientEnd: Color(red: 33/255, green: 58/255, blue: 97/255))

    static func theme(forStoreId id: String) -> WatchTeamTheme? {
        switch id {
        case "default": return defaultTheme
        case "midnight_indigo": return storeMidnightIndigo
        case "cherry_rose": return storeCherryRose
        case "burgundy_gold": return storeBurgundyGold
        case "royal_blue": return storeRoyalBlue
        case "deep_navy": return storeDeepNavy
        case "crimson_red": return storeCrimsonRed
        case "charcoal_black": return storeCharcoalBlack
        case "sunset_orange": return storeSunsetOrange
        case "fire_red": return storeFireRed
        case "slate_blue": return storeSlateBlue
        case "puppy": return puppy
        case "puppy2": return puppy2
        default: return nil
        }
    }
}

// MARK: - Environment Key
private struct WatchTeamThemeKey: EnvironmentKey {
    static let defaultValue: WatchTeamTheme = WatchTeamThemes.defaultTheme
}

extension EnvironmentValues {
    var watchTeamTheme: WatchTeamTheme {
        get { self[WatchTeamThemeKey.self] }
        set { self[WatchTeamThemeKey.self] = newValue }
    }
}
