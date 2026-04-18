import SwiftUI

enum ThemeCategory {
    case free       // 기본형 (처음부터 잠금 해제)
    case adReward   // 광고 시청 후 잠금 해제
    case premium    // 인앱 결제
}

struct ThemeData: Identifiable {
    let id: String
    let name: String
    let category: ThemeCategory
    let colors: ThemeColors
    let backgroundImage: String?
    let price: String

    init(id: String, name: String, category: ThemeCategory, colors: ThemeColors, backgroundImage: String? = nil, price: String = "") {
        self.id = id
        self.name = name
        self.category = category
        self.colors = colors
        self.backgroundImage = backgroundImage
        self.price = price
    }
}

struct ThemeColors {
    let primary: Color
    let secondary: Color
    let accent: Color
}

// Reason: 각 테마가 고유 색 조합을 가지므로 토큰 팔레트 밖 색상 사용 허용.
extension ThemeData {
    static let allThemes: [ThemeData] = [
        // 기본형 (무료)
        ThemeData(
            id: "default",
            name: "기본형",
            category: .free,
            colors: ThemeColors(
                primary: Color(red: 59/255, green: 130/255, blue: 246/255),
                secondary: Color(red: 37/255, green: 99/255, blue: 235/255),
                accent: Color(red: 96/255, green: 165/255, blue: 250/255)
            )
        ),
        // 광고 시청 무료 테마 (10종)
        ThemeData(
            id: "midnight_indigo",
            name: "미드나이트 인디고",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 19/255, green: 18/255, blue: 48/255),
                secondary: Color(red: 30/255, green: 28/255, blue: 75/255),
                accent: Color(red: 96/255, green: 165/255, blue: 250/255)
            )
        ),
        ThemeData(
            id: "cherry_rose",
            name: "체리 로즈",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 195/255, green: 4/255, blue: 82/255),
                secondary: Color(red: 142/255, green: 2/255, blue: 59/255),
                accent: Color(red: 244/255, green: 114/255, blue: 182/255)
            )
        ),
        ThemeData(
            id: "burgundy_gold",
            name: "버건디 골드",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 130/255, green: 0/255, blue: 36/255),
                secondary: Color(red: 92/255, green: 0/255, blue: 26/255),
                accent: Color(red: 252/255, green: 165/255, blue: 165/255)
            )
        ),
        ThemeData(
            id: "royal_blue",
            name: "로열 블루",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 7/255, green: 76/255, blue: 161/255),
                secondary: Color(red: 5/255, green: 54/255, blue: 120/255),
                accent: Color(red: 147/255, green: 197/255, blue: 253/255)
            )
        ),
        ThemeData(
            id: "deep_navy",
            name: "딥 네이비",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 4/255, green: 30/255, blue: 66/255),
                secondary: Color(red: 2/255, green: 18/255, blue: 48/255),
                accent: Color(red: 147/255, green: 197/255, blue: 253/255)
            )
        ),
        ThemeData(
            id: "crimson_red",
            name: "크림슨 레드",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 206/255, green: 14/255, blue: 45/255),
                secondary: Color(red: 150/255, green: 10/255, blue: 32/255),
                accent: Color(red: 252/255, green: 165/255, blue: 165/255)
            )
        ),
        ThemeData(
            id: "charcoal_black",
            name: "차콜 블랙",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 26/255, green: 26/255, blue: 26/255),
                secondary: Color.black,
                accent: Color(red: 163/255, green: 163/255, blue: 163/255)
            )
        ),
        ThemeData(
            id: "sunset_orange",
            name: "선셋 오렌지",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 255/255, green: 102/255, blue: 0/255),
                secondary: Color(red: 204/255, green: 82/255, blue: 0/255),
                accent: Color(red: 253/255, green: 186/255, blue: 116/255)
            )
        ),
        ThemeData(
            id: "fire_red",
            name: "파이어 레드",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 234/255, green: 0/255, blue: 41/255),
                secondary: Color(red: 181/255, green: 0/255, blue: 31/255),
                accent: Color(red: 252/255, green: 165/255, blue: 165/255)
            )
        ),
        ThemeData(
            id: "slate_blue",
            name: "슬레이트 블루",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 49/255, green: 82/255, blue: 136/255),
                secondary: Color(red: 33/255, green: 58/255, blue: 97/255),
                accent: Color(red: 147/255, green: 197/255, blue: 253/255)
            )
        ),
        // 프리미엄 유료 테마
        ThemeData(
            id: "puppy",
            name: "멍멍이",
            category: .premium,
            colors: ThemeColors(
                primary: Color(red: 220/255, green: 38/255, blue: 38/255),
                secondary: Color(red: 160/255, green: 20/255, blue: 20/255),
                accent: Color(red: 255/255, green: 182/255, blue: 193/255)
            ),
            backgroundImage: "theme_puppy",
            price: "₩1,200"
        ),
        ThemeData(
            id: "puppy2",
            name: "멍멍이2",
            category: .premium,
            colors: ThemeColors(
                primary: Color(red: 220/255, green: 38/255, blue: 38/255),
                secondary: Color(red: 160/255, green: 20/255, blue: 20/255),
                accent: Color(red: 255/255, green: 182/255, blue: 193/255)
            ),
            backgroundImage: "theme_puppy2",
            price: "₩1,200"
        ),
    ]
}
