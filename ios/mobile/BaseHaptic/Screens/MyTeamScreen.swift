import SwiftUI

// 1차 콘텐츠는 응원 누적 체크인 랭킹. 향후 팀별 뉴스 등 확장 가능 컨테이너.
struct MyTeamScreen: View {
    let selectedTeam: Team

    var body: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: AppSpacing.md) {
                    Text("내 팀")
                        .font(AppFont.h2)
                        .foregroundColor(.white)
                        .padding(.top, AppSpacing.lg)
                        .padding(.horizontal, AppSpacing.lg)
                    TeamCheckinRankingView(selectedTeam: selectedTeam)
                        .padding(.horizontal, AppSpacing.lg)
                }
            }
        }
    }
}
