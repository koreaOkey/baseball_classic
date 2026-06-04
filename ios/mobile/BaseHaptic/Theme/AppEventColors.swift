import SwiftUI

/// 게임 이벤트 타입별 시맨틱 색상.
/// 화면·플랫폼 간 일관성을 위해 항상 이 헬퍼를 사용한다.
/// 매핑 정의는 openspec/specs/design-system/spec.md 참고.
enum AppEventColors {

    /// 이벤트 타입 문자열(대소문자 무관)에 대응하는 색상을 반환한다.
    /// 라이브 상세 PitchChip 가독성을 위해 S=노랑, B=초록, 안타=파랑으로 분리.
    static func color(for eventType: String) -> Color {
        switch eventType.uppercased() {
        case "STRIKE":
            return AppColors.yellow500
        case "BALL":
            return AppColors.green500
        case "HIT":
            return AppColors.blue500
        case "FOUL":
            return AppColors.orange500
        case "HOMERUN", "SCORE", "SAC_FLY_SCORE", "VICTORY", "MOUND_VISIT":
            return AppColors.yellow500
        case "WALK", "STEAL", "TAG_UP_ADVANCE", "PITCHER_CHANGE":
            return AppColors.green500
        case "DOUBLE_PLAY", "TRIPLE_PLAY":
            return AppColors.orange500
        case "OUT":
            return AppColors.red500
        default:
            return AppColors.gray500
        }
    }
}
