import SwiftUI

// TODO(stadium-cheer): 활성화 시 응원 발화 풀스크린(워치)에 선택된 테마 컬러/배경 반영.
// 현재는 상점 mock 데이터만 제공. 구매·적용 콜백은 기존 ThemeData 흐름과 동일.
enum StadiumCheerThemes {
    static let allThemes: [ThemeData] = [
        ThemeData(
            id: "cheer_default",
            name: "기본 응원",
            category: .free,
            colors: ThemeColors(
                primary: Color(red: 59/255, green: 130/255, blue: 246/255),
                secondary: Color(red: 30/255, green: 58/255, blue: 138/255),
                accent: .white
            )
        ),
        ThemeData(
            id: "cheer_team_color_flash",
            name: "팀 컬러 플래시",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 220/255, green: 20/255, blue: 30/255),
                secondary: .white,
                accent: Color(red: 255/255, green: 215/255, blue: 0/255)
            ),
            backgroundImage: "theme_baseball_love"
        ),
        ThemeData(
            id: "cheer_spotlight",
            name: "스포트라이트",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 17/255, green: 24/255, blue: 39/255),
                secondary: Color(red: 251/255, green: 191/255, blue: 36/255),
                accent: .white
            )
        ),
        ThemeData(
            id: "cheer_festival",
            name: "축제 모드",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 255/255, green: 107/255, blue: 107/255),
                secondary: Color(red: 255/255, green: 230/255, blue: 109/255),
                accent: Color(red: 78/255, green: 205/255, blue: 196/255)
            )
        ),
        ThemeData(
            id: "cheer_neon_pulse",
            name: "네온 펄스",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 0/255, green: 217/255, blue: 255/255),
                secondary: Color(red: 255/255, green: 0/255, blue: 255/255),
                accent: .white
            )
        ),
        ThemeData(
            id: "cheer_sunrise_chant",
            name: "선라이즈 챈트",
            category: .adReward,
            colors: ThemeColors(
                primary: Color(red: 255/255, green: 153/255, blue: 102/255),
                secondary: Color(red: 255/255, green: 94/255, blue: 98/255),
                accent: .white
            )
        ),
    ]
}
