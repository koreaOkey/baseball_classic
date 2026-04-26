import SwiftUI

/// 게임 이벤트 타입별 시맨틱 색상.
/// 화면·플랫폼 간 일관성을 위해 항상 이 헬퍼를 사용한다.
/// 매핑 정의는 openspec/specs/design-system/spec.md 참고.
enum AppEventColors {

    /// 이벤트 타입 문자열(대소문자 무관)에 대응하는 색상을 반환한다.
    static func color(for eventType: String) -> Color {
        switch eventType.uppercased() {
        case "HOMERUN", "SCORE", "SAC_FLY_SCORE", "VICTORY":
            return AppColors.yellow500
        case "HIT", "WALK", "STEAL", "TAG_UP_ADVANCE":
            return AppColors.green500
        case "DOUBLE_PLAY", "TRIPLE_PLAY", "STRIKE":
            return AppColors.orange500
        case "OUT":
            return AppColors.red500
        case "BALL":
            return AppColors.gray400
        default:
            return AppColors.gray500
        }
    }
}
