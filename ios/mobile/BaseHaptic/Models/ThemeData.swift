import SwiftUI

struct ThemeData: Identifiable {
    let id: String
    let teamId: Team
    let name: String
    let colors: ThemeColors
    let animation: String?

    init(id: String, teamId: Team, name: String, colors: ThemeColors, animation: String? = nil) {
        self.id = id
        self.teamId = teamId
        self.name = name
        self.colors = colors
        self.animation = animation
    }
}

struct ThemeColors {
    let primary: Color
    let secondary: Color
    let accent: Color
}
