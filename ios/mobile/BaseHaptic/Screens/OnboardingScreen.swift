import SwiftUI

struct OnboardingScreen: View {
    let onComplete: (Team) -> Void

    @State private var selectedTeam: Team = .none
    @State private var step = 1

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [AppColors.gray950, Color(hex: 0x0F172A), AppColors.gray950],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Spacer()

                if step == 1 {
                    teamSelectionStep
                } else {
                    featureStep
                }

                Spacer()
            }
            .padding(.horizontal, 24)
        }
    }

    // MARK: - Step 1: Team Selection
    private var teamSelectionStep: some View {
        VStack(spacing: 0) {
            Text("⚾")
                .font(.system(size: 64))
                .padding(.bottom, 16)

            Text("BaseHaptic")
                .font(.system(size: 36, weight: .bold))
                .foregroundColor(.white)
                .padding(.bottom, 8)

            Text("응원 팀을 선택하고 워치 실시간 햅틱을 시작해보세요.")
                .font(.system(size: 14))
                .foregroundColor(AppColors.gray400)
                .padding(.bottom, 32)

            VStack(spacing: 0) {
                Text("응원하는 팀을 선택하세요")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.bottom, 16)

                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(Team.selectableTeams) { team in
                            TeamSelectionItem(
                                team: team,
                                isSelected: selectedTeam == team,
                                onTap: { selectedTeam = team }
                            )
                        }
                    }
                }
                .frame(maxHeight: 400)

                Button(action: { step = 2 }) {
                    Text("계속하기")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(selectedTeam != .none ? AppColors.blue600 : AppColors.gray800)
                        .cornerRadius(12)
                }
                .disabled(selectedTeam == .none)
                .padding(.top, 24)
            }
            .padding(24)
            .background(AppColors.gray900.opacity(0.5))
            .cornerRadius(16)
        }
    }

    // MARK: - Step 2: Features
    private var featureStep: some View {
        VStack(spacing: 0) {
            Text("알림 설정")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
                .padding(.bottom, 24)

            FeatureCard(emoji: "⌚", title: "워치 햅틱 피드백",
                        description: "득점, 홈런 등 주요 이벤트 발생 시 스마트워치로 진동 알림을 보냅니다.")

            FeatureCard(emoji: "🗓", title: "경기 일정 자동 동기화",
                        description: "응원 팀 경기 일정을 캘린더에 자동으로 등록합니다.")
                .padding(.top, 16)

            FeatureCard(emoji: "🤝", title: "원격 하이파이브",
                        description: "친구와 득점 순간의 감정을 함께 공유합니다. (블루투스 사용)")
                .padding(.top, 16)

            Button(action: { onComplete(selectedTeam) }) {
                Text("시작하기")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(AppColors.blue600)
                    .cornerRadius(12)
            }
            .padding(.top, 24)
        }
        .padding(24)
        .background(AppColors.gray900.opacity(0.5))
        .cornerRadius(16)
    }
}

// MARK: - Subviews
private struct TeamSelectionItem: View {
    let team: Team
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                TeamLogo(team: team, size: 40)
                    .padding(.trailing, 4)

                Text(team.teamName)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)

                Spacer()

                if isSelected {
                    Image(systemName: "chevron.right")
                        .foregroundColor(.white)
                }
            }
            .padding(16)
            .background(isSelected ? team.color : AppColors.gray800.opacity(0.5))
            .cornerRadius(12)
        }
    }
}

private struct FeatureCard: View {
    let emoji: String
    let title: String
    let description: String

    var body: some View {
        HStack(alignment: .top) {
            Text(emoji)
                .font(.system(size: 24))
                .padding(.trailing, 12)

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)

                Text(description)
                    .font(.system(size: 14))
                    .foregroundColor(AppColors.gray400)
                    .lineSpacing(4)
            }
        }
        .padding(16)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(AppColors.gray800.opacity(0.8), lineWidth: 1)
        )
    }
}
