import SwiftUI

struct ThemeStoreScreen: View {
    let selectedTeam: Team
    let activeTheme: ThemeData?
    let purchasedThemes: [ThemeData]
    let onApplyTheme: (ThemeData?) -> Void
    let onPurchaseTheme: (ThemeData) -> Void

    @Environment(\.teamTheme) private var teamTheme

    private var storeItems: [ThemeStoreItem] {
        Self.mockItems(for: selectedTeam)
    }

    private var purchasedIds: Set<String> {
        Set(purchasedThemes.map { $0.id })
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header

                VStack(spacing: AppSpacing.sm) {
                    ForEach(storeItems) { item in
                        ThemeStoreItemCard(
                            item: item,
                            isPurchased: purchasedIds.contains(item.theme.id),
                            isApplied: activeTheme?.id == item.theme.id,
                            onPurchase: { onPurchaseTheme(item.theme) },
                            onApply: { onApplyTheme(item.theme) }
                        )
                    }
                }
                .padding(.horizontal, AppSpacing.xxl)

                Text("목업 데이터는 실제 결제/다운로드와 연결되어 있지 않습니다.")
                    .font(AppFont.micro)
                    .foregroundColor(AppColors.gray400)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, AppSpacing.xxl)
                    .padding(.vertical, AppSpacing.xl)

                Spacer().frame(height: AppSpacing.bottomSafeSpacer)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppColors.gray950)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("테마 상점")
                .font(AppFont.h3Bold)
                .foregroundColor(.white)
            Text("목업 데이터로 구성된 테마 프리뷰입니다.")
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
                .padding(.top, 6)
            Text("현재 적용: \(activeTheme?.name ?? "기본 팀 테마")")
                .font(AppFont.caption)
                .foregroundColor(teamTheme.primary)
                .padding(.top, AppSpacing.sm)
            Button(action: { onApplyTheme(nil) }) {
                Text("기본 팀 테마 적용")
                    .font(AppFont.labelMedium)
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, AppSpacing.sm)
                    .overlay(
                        RoundedRectangle(cornerRadius: AppRadius.sm)
                            .stroke(activeTheme == nil ? AppColors.gray600 : teamTheme.primary, lineWidth: 1)
                    )
                    .foregroundColor(activeTheme == nil ? AppColors.gray600 : teamTheme.primary)
            }
            .disabled(activeTheme == nil)
            .padding(.top, AppSpacing.md)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.vertical, AppSpacing.xl)
    }
}

private struct ThemeStoreItemCard: View {
    let item: ThemeStoreItem
    let isPurchased: Bool
    let isApplied: Bool
    let onPurchase: () -> Void
    let onApply: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            HStack {
                HStack(spacing: AppSpacing.md) {
                    TeamLogo(team: item.theme.teamId, size: 60)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.theme.name)
                            .font(AppFont.bodyLgBold)
                            .foregroundColor(.white)
                        Text(item.subtitle)
                            .font(AppFont.micro)
                            .foregroundColor(AppColors.gray400)
                    }
                }
                Spacer()
                Text("\(item.price)P")
                    .font(AppFont.labelBold)
                    .foregroundColor(isPurchased ? AppColors.gray300 : AppColors.yellow500)
            }

            ZStack {
                LinearGradient(
                    colors: [item.theme.colors.primary, item.theme.colors.secondary],
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .frame(height: 64)
                .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))

                HStack {
                    Spacer()
                    Text("Accent")
                        .font(AppFont.bodyLgBold)
                        .foregroundColor(item.theme.colors.accent)
                        .padding(.trailing, AppSpacing.lg)
                }
            }

            HStack {
                Spacer()
                actionButton
            }
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))
    }

    @ViewBuilder
    private var actionButton: some View {
        if isApplied {
            HStack(spacing: AppSpacing.xs) {
                Image(systemName: "checkmark")
                    .font(AppFont.bodyBold)
                Text("적용 중")
                    .font(AppFont.labelMedium)
            }
            .foregroundColor(AppColors.gray300)
            .padding(.horizontal, AppSpacing.lg)
            .padding(.vertical, AppSpacing.sm)
            .background(AppColors.gray800)
            .clipShape(Capsule())
        } else if isPurchased {
            Button(action: onApply) {
                Text("적용하기")
                    .font(AppFont.labelMedium)
                    .foregroundColor(.white)
                    .padding(.horizontal, AppSpacing.lg)
                    .padding(.vertical, AppSpacing.sm)
                    .background(AppColors.blue500)
                    .clipShape(RoundedRectangle(cornerRadius: AppRadius.sm))
            }
        } else {
            Button(action: onPurchase) {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "cart.fill")
                        .font(AppFont.body)
                    Text("구매하기")
                        .font(AppFont.labelMedium)
                }
                .foregroundColor(.white)
                .padding(.horizontal, AppSpacing.lg)
                .padding(.vertical, AppSpacing.sm)
                .background(AppColors.gray700)
                .clipShape(RoundedRectangle(cornerRadius: AppRadius.sm))
            }
        }
    }
}

private struct ThemeStoreItem: Identifiable {
    let theme: ThemeData
    let price: Int
    let subtitle: String

    var id: String { theme.id }
}

// Reason: 목업 테마 스토어 데이터. 각 테마가 고유 색 조합을 가지므로 토큰 팔레트 밖 색상 사용 허용.
extension ThemeStoreScreen {
    fileprivate static func mockItems(for selectedTeam: Team) -> [ThemeStoreItem] {
        [
            ThemeStoreItem(
                theme: ThemeData(
                    id: "theme_home_crowd",
                    teamId: selectedTeam,
                    name: "\(selectedTeam.teamName) 홈 크라우드",
                    colors: ThemeColors(
                        primary: selectedTeam.color,
                        secondary: AppColors.gray800,
                        accent: AppColors.yellow500
                    ),
                    animation: "crowd_wave"
                ),
                price: 1200,
                subtitle: "응원단 중앙 하이라이트 + 응원가 무드"
            ),
            ThemeStoreItem(
                theme: ThemeData(
                    id: "theme_retro_sunset",
                    teamId: .kia,
                    name: "레트로 선셋",
                    colors: ThemeColors(
                        primary: AppColors.orange500,
                        secondary: AppColors.red500,
                        accent: AppColors.yellow500
                    ),
                    animation: "retro_scanline"
                ),
                price: 900,
                subtitle: "복고 감성 컬러 + 아날로그 스캔 효과"
            ),
            ThemeStoreItem(
                theme: ThemeData(
                    id: "theme_ice_blue",
                    teamId: .samsung,
                    name: "아이스 블루",
                    colors: ThemeColors(
                        primary: AppColors.blue500,
                        secondary: Color(hex: 0x0B1F3A),
                        accent: Color(hex: 0xA5D8FF)
                    ),
                    animation: "ice_spark"
                ),
                price: 1000,
                subtitle: "시원한 톤의 라인 + 타격 이펙트 강조"
            ),
            ThemeStoreItem(
                theme: ThemeData(
                    id: "theme_dark_monochrome",
                    teamId: .lotte,
                    name: "모노크롬 다크",
                    colors: ThemeColors(
                        primary: Color(hex: 0xE5E7EB),
                        secondary: Color(hex: 0x111827),
                        accent: Color(hex: 0x93C5FD)
                    ),
                    animation: "mono_pulse"
                ),
                price: 700,
                subtitle: "미니멀 대비 + 점수 집중형 UI"
            )
        ]
    }
}
