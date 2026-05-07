import SwiftUI

struct WatchInstallCard: View {
    let status: WatchCompanionStatus
    let onInstall: () -> Void
    let onRecheck: () -> Void
    let onOpenWatchApp: () -> Void
    let onWatchTest: () -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var showFallbackAlert = false
    @State private var showInstructions = false

    private static let successGreen = Color(red: 76/255, green: 175/255, blue: 80/255)
    private static let warnRed = Color(red: 229/255, green: 57/255, blue: 53/255)

    var body: some View {
        Group {
            switch status {
            case .installed:
                stateBody(
                    glowColor: Self.successGreen,
                    badgeColor: Self.successGreen,
                    badgeIcon: "checkmark",
                    title: "워치 앱 설치됨",
                    description: "[워치 테스트]에서 체험해보세요",
                    primary: ActionSpec(
                        label: "워치 테스트",
                        sfSymbol: "applewatch.radiowaves.left.and.right",
                        action: onWatchTest,
                        enabled: true,
                        color: teamTheme.primary
                    ),
                    secondary: nil
                )
            case .pairedNoApp:
                stateBody(
                    glowColor: Self.warnRed,
                    badgeColor: Self.warnRed,
                    badgeIcon: "exclamationmark",
                    title: "워치 앱 설치 필요",
                    description: "Apple Watch앱에서 야구봄을 설치해 주세요.\n자동 설치가 켜져 있다면 잠시 후 자동으로 설치됩니다.",
                    primary: ActionSpec(
                        label: "연결 방법 상세 보기",
                        sfSymbol: "info.circle.fill",
                        action: { showInstructions = true },
                        enabled: true,
                        color: teamTheme.primary
                    ),
                    secondary: nil
                )
            case .pairedNone:
                stateBody(
                    glowColor: AppColors.gray500,
                    badgeColor: AppColors.gray700,
                    badgeIcon: "exclamationmark",
                    title: "워치 페어링 필요",
                    description: "iPhone에 Apple Watch를 먼저 페어링해 주세요.",
                    primary: ActionSpec(
                        label: "Watch 앱 열기",
                        sfSymbol: "arrow.up.right.square.fill",
                        action: onOpenWatchApp,
                        enabled: true,
                        color: teamTheme.primary
                    ),
                    secondary: ActionSpec(
                        label: "연결 확인",
                        sfSymbol: "link",
                        action: onRecheck,
                        enabled: true,
                        color: .clear
                    )
                )
            case .loading:
                compactLoadingRow
            }
        }
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
        .alert("설치 안내", isPresented: $showFallbackAlert) {
            Button("확인", role: .cancel) {}
        } message: {
            Text("iPhone의 Apple Watch 앱을 직접 열어 사용 가능한 앱 목록에서 야구봄을 설치해 주세요.")
        }
        .sheet(isPresented: $showInstructions) {
            WatchInstallInstructionsSheet(onDismiss: { showInstructions = false })
        }
    }

    private struct ActionSpec {
        let label: String
        let sfSymbol: String
        let action: () -> Void
        let enabled: Bool
        let color: Color
    }

    private var compactLoadingRow: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: "applewatch")
                .foregroundColor(AppColors.gray400)
                .font(AppFont.h4)
            Text("워치 상태 확인 중…")
                .font(AppFont.bodyLgMedium)
                .foregroundColor(AppColors.gray400)
            Spacer()
        }
        .padding(AppSpacing.lg)
    }

    private func stateBody(
        glowColor: Color,
        badgeColor: Color,
        badgeIcon: String,
        title: String,
        description: String,
        primary: ActionSpec?,
        secondary: ActionSpec?
    ) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("워치 앱")
                    .font(AppFont.bodyMedium)
                    .foregroundColor(AppColors.gray400)
                Spacer()
            }
            .padding(.bottom, AppSpacing.md)

            watchIllustration(glowColor: glowColor, badgeColor: badgeColor, badgeIcon: badgeIcon)

            Text(title)
                .font(AppFont.bodyLgBold)
                .foregroundColor(.white)
                .padding(.top, AppSpacing.md)

            Text(description)
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
                .multilineTextAlignment(.center)
                .padding(.top, AppSpacing.xs)

            if let primary = primary {
                primaryButton(primary)
                    .padding(.top, AppSpacing.lg)
            }
            if let secondary = secondary {
                secondaryButton(secondary)
                    .padding(.top, AppSpacing.sm)
            }
        }
        .padding(AppSpacing.lg)
    }

    private func watchIllustration(glowColor: Color, badgeColor: Color, badgeIcon: String) -> some View {
        ZStack {
            // 상태색 radial glow — 워치 아이콘 뒤에서 부드럽게 퍼지는 효과
            Circle()
                .fill(
                    RadialGradient(
                        gradient: Gradient(colors: [
                            glowColor.opacity(0.55),
                            glowColor.opacity(0.0)
                        ]),
                        center: .center,
                        startRadius: 0,
                        endRadius: 70
                    )
                )
                .frame(width: 140, height: 140)
                .blur(radius: 8)

            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.1))
                    .frame(width: 96, height: 96)
                Image(systemName: "applewatch")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 56, height: 56)
                    .foregroundColor(.white)
            }

            ZStack(alignment: .topTrailing) {
                Color.clear
                    .frame(width: 120, height: 120)
                ZStack {
                    Circle()
                        .fill(badgeColor)
                        .frame(width: 28, height: 28)
                    Image(systemName: badgeIcon)
                        .foregroundColor(.white)
                        .font(.system(size: 14, weight: .bold))
                }
                .offset(x: 8, y: -8)
            }
        }
        .frame(width: 140, height: 140)
    }

    private func primaryButton(_ spec: ActionSpec) -> some View {
        Button(action: spec.action) {
            HStack(spacing: AppSpacing.sm) {
                Image(systemName: spec.sfSymbol)
                Text(spec.label)
                    .font(AppFont.bodyLgMedium)
            }
            .foregroundColor(spec.enabled ? .white : AppColors.gray400)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(spec.enabled ? spec.color : AppColors.gray800)
            .cornerRadius(AppRadius.sm)
        }
        .disabled(!spec.enabled)
    }

    private func secondaryButton(_ spec: ActionSpec) -> some View {
        Button(action: spec.action) {
            HStack(spacing: AppSpacing.sm) {
                Image(systemName: spec.sfSymbol)
                Text(spec.label)
                    .font(AppFont.body)
            }
            .foregroundColor(AppColors.gray300)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.sm)
                    .stroke(AppColors.gray700, lineWidth: 1)
            )
        }
    }
}
