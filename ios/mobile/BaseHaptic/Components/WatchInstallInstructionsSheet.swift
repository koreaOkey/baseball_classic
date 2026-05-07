import SwiftUI

struct WatchInstallInstructionsSheet: View {
    let onDismiss: () -> Void

    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppSpacing.xl) {
                header

                stepCard(
                    stepNumber: 1,
                    imageName: "watch_guide_open_watch_app",
                    description: "iPhone에서 'Watch' 앱을 여세요.",
                    imageMaxWidth: 96
                )

                stepCard(
                    stepNumber: 2,
                    imageName: "watch_guide_available_apps",
                    description: "사용 가능한 앱에서 \"야구봄\"을 설치합니다.",
                    imageMaxWidth: nil
                )

                Button(action: onDismiss) {
                    Text("확인")
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(teamTheme.primary)
                        .cornerRadius(AppRadius.sm)
                }
                .padding(.top, AppSpacing.sm)
            }
            .padding(AppSpacing.xxl)
        }
        .background(AppColors.gray950.ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: AppSpacing.sm) {
            Text("워치 앱 연결 방법")
                .font(AppFont.h2)
                .foregroundColor(.white)
            Text("아래 두 단계로 워치에 야구봄을 설치할 수 있어요.")
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
        }
    }

    private func stepCard(stepNumber: Int, imageName: String, description: String, imageMaxWidth: CGFloat?) -> some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            HStack(spacing: AppSpacing.sm) {
                ZStack {
                    Circle()
                        .fill(teamTheme.primary)
                        .frame(width: 28, height: 28)
                    Text("\(stepNumber)")
                        .font(AppFont.bodyBold)
                        .foregroundColor(.white)
                }
                Text(description)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)
            }

            HStack {
                if imageMaxWidth != nil {
                    Spacer(minLength: 0)
                }
                Image(imageName)
                    .resizable()
                    .scaledToFit()
                    .frame(maxWidth: imageMaxWidth ?? .infinity)
                    .cornerRadius(AppRadius.sm)
                if imageMaxWidth != nil {
                    Spacer(minLength: 0)
                }
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }
}
