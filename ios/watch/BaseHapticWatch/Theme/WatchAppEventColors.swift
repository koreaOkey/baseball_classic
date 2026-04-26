import SwiftUI

/// 워치 게임 이벤트 타입별 시맨틱 색상.
/// iPhone `AppEventColors`와 동일 매핑 원칙이지만 워치 전용 예외(STEAL=cyan)가 포함된다.
enum WatchAppEventColors {

    /// 기본 매핑 (iPhone과 동일 그룹)
    static func color(for eventType: String) -> Color {
        switch eventType.uppercased() {
        case "HOMERUN", "SCORE", "SAC_FLY_SCORE", "VICTORY":
            return WatchColors.yellow500
        case "HIT", "WALK", "STEAL", "TAG_UP_ADVANCE":
            return WatchColors.green500
        case "DOUBLE_PLAY", "TRIPLE_PLAY", "STRIKE":
            return WatchColors.orange500
        case "OUT":
            return WatchColors.red500
        case "BALL":
            return WatchColors.gray400
        default:
            return WatchColors.gray500
        }
    }

    /// 워치 EventOverlay 전용 아이콘 세트 (label, SF Symbol, 강조색)
    /// EventOverlay는 작은 화면에서 시인성을 위해 이벤트 종류를 더 세분화된 색으로 구분한다.
    static func overlayStyle(for eventType: String) -> (label: String, icon: String, color: Color)? {
        switch eventType.uppercased() {
        case "HIT": return ("HIT", "bolt.fill", WatchColors.green500)
        case "WALK": return ("WALK", "bolt.fill", WatchColors.green400)
        case "STEAL": return ("STEAL", "bolt.fill", WatchColors.cyan500)
        case "TAG_UP_ADVANCE": return ("STEAL", "bolt.fill", WatchColors.cyan500)
        case "SCORE": return ("SCORE", "trophy.fill", WatchColors.yellow500)
        case "HOMERUN": return ("HOMERUN", "trophy.fill", WatchColors.yellow500)
        case "OUT": return ("OUT", "xmark.circle.fill", WatchColors.red500)
        case "DOUBLE_PLAY": return ("DOUBLE PLAY", "xmark.circle.fill", WatchColors.orange500)
        case "TRIPLE_PLAY": return ("TRIPLE PLAY", "xmark.circle.fill", WatchColors.red600)
        default: return nil
        }
    }
}
