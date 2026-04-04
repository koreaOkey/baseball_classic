import SwiftUI

enum Team: String, CaseIterable, Codable, Identifiable {
    case none = "NONE"
    case doosan = "DOOSAN"
    case lg = "LG"
    case kiwoom = "KIWOOM"
    case samsung = "SAMSUNG"
    case lotte = "LOTTE"
    case ssg = "SSG"
    case kt = "KT"
    case hanwha = "HANWHA"
    case kia = "KIA"
    case nc = "NC"

    var id: String { rawValue }

    var teamName: String {
        switch self {
        case .none: return "없음"
        case .doosan: return "베어스"
        case .lg: return "트윈스"
        case .kiwoom: return "히어로즈"
        case .samsung: return "라이온즈"
        case .lotte: return "자이언츠"
        case .ssg: return "랜더스"
        case .kt: return "위즈"
        case .hanwha: return "이글스"
        case .kia: return "타이거즈"
        case .nc: return "다이노스"
        }
    }

    var color: Color {
        switch self {
        case .none: return Color(hex: 0x3B82F6)
        case .doosan: return Color(hex: 0x131230)
        case .lg: return Color(hex: 0xC30452)
        case .kiwoom: return Color(hex: 0x820024)
        case .samsung: return Color(hex: 0x074CA1)
        case .lotte: return Color(hex: 0x041E42)
        case .ssg: return Color(hex: 0xCE0E2D)
        case .kt: return Color(hex: 0x000000)
        case .hanwha: return Color(hex: 0xFF6600)
        case .kia: return Color(hex: 0xEA0029)
        case .nc: return Color(hex: 0x315288)
        }
    }

    /// KBO 팀 ID (백엔드 API용)
    var kboTeamId: String? {
        switch self {
        case .doosan: return "OB"
        case .lg: return "LG"
        case .kiwoom: return "WO"
        case .samsung: return "SS"
        case .lotte: return "LT"
        case .ssg: return "SK"
        case .kt: return "KT"
        case .hanwha: return "HH"
        case .kia: return "HT"
        case .nc: return "NC"
        case .none: return nil
        }
    }

    /// 선택 가능한 팀 목록 (NONE 제외)
    static var selectableTeams: [Team] {
        allCases.filter { $0 != .none }
    }

    static func fromString(_ value: String) -> Team {
        Team(rawValue: value) ?? .none
    }

    static func fromBackendName(_ value: String) -> Team {
        let normalized = value.trimmingCharacters(in: .whitespaces).lowercased()
        if normalized.isEmpty { return .none }

        if normalized.contains("doosan") || normalized.contains("두산") || normalized.contains("베어스") { return .doosan }
        if normalized.contains("lg") || normalized.contains("엘지") || normalized.contains("트윈스") { return .lg }
        if normalized.contains("kiwoom") || normalized.contains("키움") || normalized.contains("히어로즈") || normalized.contains("넥센") { return .kiwoom }
        if normalized.contains("samsung") || normalized.contains("삼성") || normalized.contains("라이온즈") { return .samsung }
        if normalized.contains("lotte") || normalized.contains("롯데") || normalized.contains("자이언츠") { return .lotte }
        if normalized.contains("ssg") || normalized.contains("lander") || normalized.contains("에스에스지") || normalized.contains("랜더스") { return .ssg }
        if normalized.contains("kt") || normalized.contains("wiz") || normalized.contains("케이티") || normalized.contains("위즈") { return .kt }
        if normalized.contains("hanwha") || normalized.contains("한화") || normalized.contains("이글스") { return .hanwha }
        if normalized.contains("kia") || normalized.contains("기아") || normalized.contains("타이거즈") { return .kia }
        if normalized.contains("nc") || normalized.contains("dinos") || normalized.contains("엔씨") || normalized.contains("다이노스") { return .nc }
        return .none
    }
}
