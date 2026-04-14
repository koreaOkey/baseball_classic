import SwiftUI
import AuthenticationServices

struct OnboardingScreen: View {
    let onComplete: (Team) -> Void
    var authState: AuthState = .loggedOut
    var onSignInWithKakao: () -> Void = {}
    var onSignInWithApple: (ASAuthorization) -> Void = { _ in }

    @State private var selectedTeam: Team = .none
    @State private var step = 1
    @State private var didAutoComplete = false

    var body: some View {
        ZStack {
            // Reason: 온보딩 전용 3단 그라디언트. 중간색은 디자인 요구사항이라 토큰화하지 않음.
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
                } else if step == 2 {
                    featureStep
                } else {
                    loginStep
                }

                Spacer()
            }
            .padding(.horizontal, AppSpacing.xxl)
        }
        .onChange(of: authState) { _, newState in
            if case .loggedIn = newState, step == 3, !didAutoComplete {
                didAutoComplete = true
                onComplete(selectedTeam)
            }
        }
    }

    // MARK: - Step 1: Team Selection
    private var teamSelectionStep: some View {
        VStack(spacing: 0) {
            Image(systemName: "baseball.fill")
                .font(AppFont.display)
                .foregroundColor(.white)
                .padding(.bottom, AppSpacing.lg)

            Text("야구봄")
                .font(AppFont.h1)
                .foregroundColor(.white)
                .padding(.bottom, AppSpacing.sm)

            Text("응원 팀을 선택하고 워치로 실시간 중계를 확인하세요.")
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
                .padding(.bottom, AppSpacing.xxxl)

            VStack(spacing: 0) {
                Text("응원하는 팀을 선택하세요")
                    .font(AppFont.h4Bold)
                    .foregroundColor(.white)
                    .padding(.bottom, AppSpacing.lg)

                ScrollView {
                    VStack(spacing: AppSpacing.sm) {
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
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        // Reason: 표준 버튼 높이(14pt = lg/2 - xs)
                        .padding(.vertical, 14)
                        .background(selectedTeam != .none ? AppColors.blue600 : AppColors.gray800)
                        .cornerRadius(AppRadius.md)
                }
                .disabled(selectedTeam == .none)
                .padding(.top, AppSpacing.xxl)
            }
            .padding(AppSpacing.xxl)
            .background(AppColors.gray900.opacity(0.5))
            .cornerRadius(AppRadius.lg)
        }
    }

    // MARK: - Step 2: Features
    private var featureStep: some View {
        VStack(spacing: 0) {
            Text("기능 설명")
                .font(AppFont.h3Bold)
                .foregroundColor(.white)
                .padding(.bottom, AppSpacing.xxl)

            FeatureCard(systemImage: "applewatch.radiowaves.left.and.right", title: "워치로 라이브 경기 보기",
                        description: "득점, 홈런 등 주요 이벤트 발생 시 스마트워치로 진동 알림을 보냅니다.")

            Button(action: { step = 3 }) {
                Text("계속하기")
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    // Reason: 표준 버튼 높이
                    .padding(.vertical, 14)
                    .background(AppColors.blue600)
                    .cornerRadius(AppRadius.md)
            }
            .padding(.top, AppSpacing.xxl)
        }
        .padding(AppSpacing.xxl)
        .background(AppColors.gray900.opacity(0.5))
        .cornerRadius(AppRadius.lg)
    }

    // MARK: - Step 3: Login
    private var loginStep: some View {
        VStack(spacing: 0) {
            Text("로그인")
                .font(AppFont.h3Bold)
                .foregroundColor(.white)
                .padding(.bottom, AppSpacing.sm)

            Text("로그인하면 데이터를 안전하게 저장하고\n다른 기기에서도 이용할 수 있어요.")
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
                .multilineTextAlignment(.center)
                .padding(.bottom, AppSpacing.xxl)

            switch authState {
            case .loggedIn:
                // Reason: "로그인 완료" 피드백 전용 Material green 톤
                Text("로그인 완료!")
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(Color(red: 76/255, green: 175/255, blue: 80/255))
                    .padding(.bottom, AppSpacing.lg)

                Button(action: { onComplete(selectedTeam) }) {
                    Text("시작하기")
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(AppColors.blue600)
                        .cornerRadius(AppRadius.md)
                }

            default:
                // 카카오 로그인
                Button(action: onSignInWithKakao) {
                    HStack(spacing: AppSpacing.sm) {
                        Image(systemName: "message.fill")
                            .font(AppFont.h4)
                            // Reason: 카카오 브랜드 지정 색 (거의 블랙)
                            .foregroundColor(Color(red: 0.1, green: 0.1, blue: 0.1))
                        Text("카카오로 로그인")
                            .font(AppFont.bodyLgBold)
                            .foregroundColor(Color(red: 0.1, green: 0.1, blue: 0.1))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(AppSpacing.lg)
                    // Reason: 카카오 브랜드 지정 색 (#FEE500)
                    .background(Color(red: 254/255, green: 229/255, blue: 0))
                    .cornerRadius(AppRadius.md)
                }

                // Apple 로그인
                SignInWithAppleButton(.signIn) { request in
                    request.requestedScopes = [.email]
                } onCompletion: { result in
                    if case .success(let authorization) = result {
                        onSignInWithApple(authorization)
                    }
                }
                .signInWithAppleButtonStyle(.white)
                .frame(height: 50)
                .cornerRadius(AppRadius.md)
                .padding(.top, AppSpacing.sm)

                // 건너뛰기
                Button(action: { onComplete(selectedTeam) }) {
                    Text("건너뛰기")
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, AppSpacing.md)
                }
                .padding(.top, AppSpacing.sm)
            }
        }
        .padding(AppSpacing.xxl)
        .background(AppColors.gray900.opacity(0.5))
        .cornerRadius(AppRadius.lg)
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
                TeamLogo(team: team, size: 72)
                    .padding(.trailing, AppSpacing.xs)

                Text(team.teamName)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)

                Spacer()

                if isSelected {
                    Image(systemName: "chevron.right")
                        .foregroundColor(.white)
                }
            }
            .padding(AppSpacing.lg)
            .background(isSelected ? team.color : AppColors.gray800.opacity(0.5))
            .cornerRadius(AppRadius.md)
        }
    }
}

private struct FeatureCard: View {
    let systemImage: String
    let title: String
    let description: String

    var body: some View {
        HStack(alignment: .top) {
            Image(systemName: systemImage)
                .font(AppFont.h3)
                .foregroundColor(.white)
                .frame(width: 32)
                .padding(.trailing, AppSpacing.md)

            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                Text(title)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)

                Text(description)
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray400)
                    .lineSpacing(4)
            }
        }
        .padding(AppSpacing.lg)
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.md)
                .stroke(AppColors.gray800.opacity(0.8), lineWidth: 1)
        )
    }
}
