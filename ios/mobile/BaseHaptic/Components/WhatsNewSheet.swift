import SwiftUI

struct WhatsNewSheet: View {
    let note: ReleaseNote
    let onConfirm: () -> Void

    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: AppSpacing.xl) {
                    header
                    bulletList
                }
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.top, AppSpacing.xxl)
                .padding(.bottom, AppSpacing.lg)
            }

            confirmButton
        }
        .background(AppColors.gray950.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("NEW v\(note.version)")
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.gray950)
                .padding(.horizontal, AppSpacing.sm)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.yellow400)
                .cornerRadius(AppRadius.sm)

            Text("업데이트 안내")
                .font(AppFont.h2)
                .foregroundColor(.white)

            Text(note.subtitle)
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
        }
    }

    private var bulletList: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            ForEach(note.bullets, id: \.self) { bullet in
                HStack(alignment: .top, spacing: AppSpacing.sm) {
                    ZStack {
                        Circle()
                            .fill(teamTheme.primary)
                            .frame(width: 20, height: 20)
                        Image(systemName: "checkmark")
                            .font(AppFont.tinyBold)
                            .foregroundColor(.white)
                    }
                    Text(bullet)
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(AppColors.gray100)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var confirmButton: some View {
        Button(action: onConfirm) {
            Text("확인")
                .font(AppFont.bodyLgBold)
                .foregroundColor(teamTheme.primary)
                .frame(maxWidth: .infinity)
                .frame(height: AppSpacing.buttonHeight)
        }
        .background(AppColors.gray900)
        .overlay(
            Rectangle()
                .fill(AppColors.gray800)
                .frame(height: 1),
            alignment: .top
        )
    }
}
