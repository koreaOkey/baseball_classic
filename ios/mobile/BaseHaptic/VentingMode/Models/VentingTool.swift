#if DEBUG
import Foundation

// MARK: - VentingTool

/// 분풀이 도구.
///
/// 룸 화면 하단 트레이에 표시되며, 선택된 도구가 인형 탭 시
/// 타격 연출(도구 내려치기 + 히트 이펙트)에 사용된다.
enum VentingTool: String, CaseIterable, Identifiable {
    case fist
    case hammer
    case bat
    case slipper
    case frypan

    var id: String { rawValue }

    /// 트레이에 표시되는 한국어 라벨.
    var label: String {
        switch self {
        case .fist: return "맨손"
        case .hammer: return "뿅망치"
        case .bat: return "야구방망이"
        case .slipper: return "슬리퍼"
        case .frypan: return "프라이팬"
        }
    }

    /// Asset Catalog 스프라이트 이름 (투명 배경 PNG).
    var imageName: String {
        switch self {
        case .fist: return "VentingToolFist"
        case .hammer: return "VentingToolHammer"
        case .bat: return "VentingToolBat"
        case .slipper: return "VentingToolSlipper"
        case .frypan: return "VentingToolFrypan"
        }
    }

    /// 타격 히트 이펙트 스프라이트 (별·충격파, 투명 배경).
    static let hitEffectImageName = "VentingHitEffect"
}
#endif
