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

    /// 타격 연출 시 스프라이트에 적용하는 기본 회전(도).
    ///
    /// 장작패기(수직 내리찍기) 모션 기준: 스프라이트 원본 방향이 도구마다
    /// 달라서, 타격면(주먹 끝·망치 헤드·배트 배럴·슬리퍼 바닥·팬 바닥)이
    /// 바로 아래의 인형 정수리를 향하도록 보정한다.
    var strikeBaseRotation: Double {
        switch self {
        case .fist: return 90       // 주먹 끝(오른쪽) → 아래
        case .hammer: return 180    // 헤드(위) → 아래
        case .bat: return 135       // 배럴(우상단 대각) → 아래
        case .slipper: return 10    // (좌우 반전 후) 바닥이 아래
        case .frypan: return 10     // 팬 바닥이 아래
        }
    }

    /// 타격 연출 시 좌우 반전 여부.
    /// 슬리퍼 스프라이트는 발등(장식 면)이 좌하단을 향해 있어,
    /// 반전해야 바닥면이 인형 쪽을 향한다.
    var strikeFlipsHorizontally: Bool {
        self == .slipper
    }

    /// 타격 히트 이펙트 스프라이트 (별·충격파, 투명 배경).
    static let hitEffectImageName = "VentingHitEffect"
}
#endif
