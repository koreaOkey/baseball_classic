import SwiftUI

// 사용자가 자동 알림을 못 봤거나 권한 거부 사용자에게도 앱 내에서 체크인 진입점 제공.
struct CheerCheckinCard: View {
    let stadiumName: String
    let teamLabel: String
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("\(stadiumName)에 오신 \(teamLabel) 팬이신가요?")
                .font(AppFont.labelBold)
                .foregroundColor(.white)
            Text("응원 시각에 워치로 함께 응원해요.")
                .font(AppFont.label)
                .foregroundColor(AppColors.gray300)
            HStack(spacing: AppSpacing.sm) {
                Button(action: onConfirm) {
                    Text("확인")
                        .font(AppFont.labelBold)
                        .foregroundColor(.white)
                        .padding(.horizontal, AppSpacing.lg)
                        .padding(.vertical, 8)
                        .background(AppColors.gray700)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                Button(action: onDismiss) {
                    Text("닫기")
                        .font(AppFont.label)
                        .foregroundColor(AppColors.gray300)
                        .padding(.horizontal, AppSpacing.md)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 4)
        }
        .padding(AppSpacing.md)
        .background(AppColors.gray800)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
