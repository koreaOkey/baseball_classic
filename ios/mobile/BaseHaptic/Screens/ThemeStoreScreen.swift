import SwiftUI

enum ThemeStoreTab: String, CaseIterable {
    case watch = "Watch Themes"
    case phone = "Phone App Themes"
}

struct ThemeStoreScreen: View {
    let activeTheme: ThemeData?
    let unlockedThemeIds: Set<String>
    let onApplyTheme: (ThemeData?) -> Void
    let onUnlockTheme: (ThemeData) -> Void
    let onPurchaseTheme: (ThemeData) -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var selectedTab: ThemeStoreTab = .watch

    private var freeAndAdThemes: [ThemeData] {
        ThemeData.allThemes.filter { $0.category == .free || $0.category == .adReward }
    }

    private var premiumThemes: [ThemeData] {
        ThemeData.allThemes.filter { $0.category == .premium }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            tabBar
            tabContent
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.gray950)
    }

    // MARK: - Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("테마 상점")
                .font(AppFont.h3Bold)
                .foregroundColor(.white)
            Text("현재 적용: \(activeTheme?.name ?? "기본형")")
                .font(AppFont.caption)
                .foregroundColor(teamTheme.primary)
                .padding(.top, AppSpacing.sm)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.vertical, AppSpacing.lg)
    }

    // MARK: - Tab Bar

    private var tabBar: some View {
        HStack(spacing: 0) {
            ForEach(ThemeStoreTab.allCases, id: \.self) { tab in
                Button(action: { withAnimation(.easeInOut(duration: 0.2)) { selectedTab = tab } }) {
                    VStack(spacing: AppSpacing.sm) {
                        Text(tab.rawValue)
                            .font(AppFont.bodyBold)
                            .foregroundColor(selectedTab == tab ? AppColors.red500 : AppColors.gray500)
                        Rectangle()
                            .fill(selectedTab == tab ? AppColors.red500 : Color.clear)
                            .frame(height: 2)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, AppSpacing.xxl)
        .background(AppColors.gray950)
    }

    // MARK: - Tab Content

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .watch:
            watchThemesTab
        case .phone:
            phoneThemesTab
        }
    }

    private var watchThemesTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                sectionHeader("베이직 테마")
                themeGrid(freeAndAdThemes)

                Spacer().frame(height: AppSpacing.bottomSafeSpacer)
            }
        }
    }

    private var phoneThemesTab: some View {
        VStack {
            Spacer()
            Image(systemName: "iphone")
                .font(.system(size: 40))
                .foregroundColor(AppColors.gray600)
            Text("준비 중")
                .font(AppFont.bodyLgMedium)
                .foregroundColor(AppColors.gray500)
                .padding(.top, AppSpacing.md)
            Text("폰 앱 테마는 곧 추가될 예정입니다.")
                .font(AppFont.body)
                .foregroundColor(AppColors.gray600)
                .padding(.top, AppSpacing.xs)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Section

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(AppFont.h5Bold)
            .foregroundColor(.white)
            .padding(.horizontal, AppSpacing.xxl)
            .padding(.top, AppSpacing.xl)
            .padding(.bottom, AppSpacing.md)
    }

    private func themeGrid(_ themes: [ThemeData]) -> some View {
        let columns = [GridItem(.flexible(), spacing: AppSpacing.md), GridItem(.flexible(), spacing: AppSpacing.md)]
        return LazyVGrid(columns: columns, spacing: AppSpacing.md) {
            ForEach(themes) { theme in
                ThemeCard(
                    theme: theme,
                    isUnlocked: theme.category == .free || unlockedThemeIds.contains(theme.id),
                    isApplied: activeTheme?.id == theme.id,
                    onApply: { onApplyTheme(theme) },
                    onUnlock: { onUnlockTheme(theme) },
                    onPurchase: { onPurchaseTheme(theme) }
                )
            }
        }
        .padding(.horizontal, AppSpacing.xxl)
    }
}

// MARK: - ThemeCard

private struct ThemeCard: View {
    let theme: ThemeData
    let isUnlocked: Bool
    let isApplied: Bool
    let onApply: () -> Void
    let onUnlock: () -> Void
    let onPurchase: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Watch face preview
            ZStack {
                // Background
                if let bgImage = theme.backgroundImage {
                    Image(bgImage)
                        .resizable()
                        .scaledToFill()
                        .frame(height: 130)
                        .clipped()
                        .overlay(Color.black.opacity(0.45))
                } else if theme.id == "default" {
                    Color(red: 10/255, green: 10/255, blue: 11/255)
                        .frame(height: 130)
                } else {
                    LinearGradient(
                        colors: [theme.colors.primary.opacity(0.25), Color(red: 10/255, green: 10/255, blue: 11/255)],
                        startPoint: .top,
                        endPoint: .center
                    )
                    .frame(height: 130)
                }

                // Mini watch UI
                MiniWatchPreview(theme: theme)

                // Badges
                if isApplied {
                    VStack {
                        HStack {
                            Spacer()
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.white)
                                .font(.system(size: 18))
                                .shadow(radius: 2)
                                .padding(6)
                        }
                        Spacer()
                    }
                }

                if !isUnlocked {
                    VStack {
                        HStack {
                            Image(systemName: "lock.fill")
                                .foregroundColor(.white.opacity(0.8))
                                .font(.system(size: 12))
                                .padding(6)
                            Spacer()
                        }
                        Spacer()
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: AppRadius.md, style: .continuous))

            // Info + action
            VStack(spacing: AppSpacing.sm) {
                Text(theme.name)
                    .font(AppFont.bodyBold)
                    .foregroundColor(.white)
                    .lineLimit(1)

                actionButton
            }
            .padding(.top, AppSpacing.md)
            .padding(.bottom, AppSpacing.sm)
        }
        .padding(AppSpacing.md)
        .background(AppColors.gray900)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
    }

    @ViewBuilder
    private var actionButton: some View {
        if isApplied {
            Text("적용 중")
                .font(AppFont.micro)
                .foregroundColor(AppColors.gray400)
                .padding(.horizontal, AppSpacing.lg)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.gray800)
                .clipShape(Capsule())
        } else if isUnlocked {
            Button(action: onApply) {
                Text("적용하기")
                    .font(AppFont.microBold)
                    .foregroundColor(.white)
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, AppSpacing.xs)
                    .background(AppColors.blue500)
                    .clipShape(Capsule())
            }
        } else if theme.category == .adReward {
            Button(action: onUnlock) {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "play.rectangle.fill")
                        .font(.system(size: 10))
                    Text("광고 보고 받기")
                        .font(AppFont.micro)
                }
                .foregroundColor(.white)
                .padding(.horizontal, AppSpacing.md)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.green500)
                .clipShape(Capsule())
            }
        } else {
            Button(action: onPurchase) {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "cart.fill")
                        .font(.system(size: 10))
                    Text(theme.price)
                        .font(AppFont.micro)
                }
                .foregroundColor(.white)
                .padding(.horizontal, AppSpacing.md)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.orange500)
                .clipShape(Capsule())
            }
        }
    }
}

// MARK: - Mini Watch Preview

private struct MiniWatchPreview: View {
    let theme: ThemeData

    private var accentColor: Color {
        theme.id == "default" ? AppColors.orange500 : theme.colors.accent
    }

    private var inningBg: Color {
        theme.id == "default" ? Color.white.opacity(0.05) : theme.colors.primary.opacity(0.15)
    }

    var body: some View {
        VStack(spacing: 4) {
            // Score row
            HStack(spacing: 0) {
                // Away
                VStack(spacing: 1) {
                    Text("팀 1")
                        .font(.system(size: 7, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                    Text("5")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)

                // Inning
                VStack(spacing: 1) {
                    Text("7회말")
                        .font(.system(size: 7, weight: .bold))
                        .foregroundColor(accentColor)
                    Text("▼")
                        .font(.system(size: 6))
                        .foregroundColor(accentColor)
                }
                .frame(width: 32, height: 22)
                .background(inningBg)
                .cornerRadius(4)

                // Home
                VStack(spacing: 1) {
                    Text("팀 2")
                        .font(.system(size: 7, weight: .medium))
                        .foregroundColor(.white.opacity(0.7))
                    Text("4")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 8)

            // BSO row
            HStack(spacing: 6) {
                // Mini base diamond
                ZStack {
                    Diamond(size: 4, filled: true).offset(x: 0, y: -4)
                    Diamond(size: 4, filled: false).offset(x: -5, y: 0)
                    Diamond(size: 4, filled: true).offset(x: 5, y: 0)
                    Diamond(size: 4, filled: false).offset(x: 0, y: 4)
                }
                .frame(width: 18, height: 16)

                VStack(alignment: .leading, spacing: 2) {
                    CountDots(label: "B", count: 2, max: 3, color: AppColors.green500)
                    CountDots(label: "S", count: 1, max: 2, color: AppColors.orange500)
                    CountDots(label: "O", count: 1, max: 2, color: AppColors.red500)
                }
            }

            // Player info
            Text("P 김광현  B 이대호")
                .font(.system(size: 6))
                .foregroundColor(.white.opacity(0.5))
        }
        .padding(.vertical, 6)
    }
}

private struct Diamond: View {
    let size: CGFloat
    let filled: Bool

    var body: some View {
        Rectangle()
            .fill(filled ? AppColors.yellow500 : Color.white.opacity(0.2))
            .frame(width: size, height: size)
            .rotationEffect(.degrees(45))
    }
}

private struct CountDots: View {
    let label: String
    let count: Int
    let max: Int
    let color: Color

    var body: some View {
        HStack(spacing: 2) {
            Text(label)
                .font(.system(size: 5, weight: .bold))
                .foregroundColor(Color.white.opacity(0.5))
                .frame(width: 6)
            ForEach(0..<max, id: \.self) { i in
                Circle()
                    .fill(i < count ? color : Color.white.opacity(0.15))
                    .frame(width: 4, height: 4)
            }
        }
    }
}
