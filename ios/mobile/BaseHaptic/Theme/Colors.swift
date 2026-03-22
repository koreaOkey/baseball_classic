import SwiftUI

// MARK: - Color Hex Extension
extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

// MARK: - BaseHaptic Colors
enum AppColors {
    static let gray950 = Color(hex: 0x0A0A0B)
    static let gray900 = Color(hex: 0x18181B)
    static let gray800 = Color(hex: 0x27272A)
    static let gray700 = Color(hex: 0x3F3F46)
    static let gray600 = Color(hex: 0x52525B)
    static let gray500 = Color(hex: 0x71717A)
    static let gray400 = Color(hex: 0xA1A1AA)
    static let gray300 = Color(hex: 0xD4D4D8)
    static let gray200 = Color(hex: 0xE4E4E7)
    static let gray100 = Color(hex: 0xF4F4F5)

    static let blue500 = Color(hex: 0x3B82F6)
    static let blue600 = Color(hex: 0x2563EB)
    static let blue700 = Color(hex: 0x1D4ED8)
    static let blue400 = Color(hex: 0x60A5FA)
    static let blue200 = Color(hex: 0xBFDBFE)

    static let yellow500 = Color(hex: 0xEAB308)
    static let yellow400 = Color(hex: 0xFACC15)
    static let orange500 = Color(hex: 0xF97316)

    static let green500 = Color(hex: 0x22C55E)
    static let green400 = Color(hex: 0x4ADE80)

    static let red500 = Color(hex: 0xEF4444)
    static let red400 = Color(hex: 0xF87171)
}
