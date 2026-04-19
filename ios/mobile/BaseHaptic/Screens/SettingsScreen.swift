import SwiftUI
import AuthenticationServices

struct SettingsScreen: View {
    let selectedTeam: Team
    let onChangeTeam: (Team) -> Void
    let activeTheme: ThemeData?
    let onSelectTheme: (ThemeData?) -> Void
    let onOpenWatchTest: () -> Void
    var authState: AuthState = .loggedOut
    var onSignInWithKakao: () -> Void = {}
    var onSignInWithApple: (ASAuthorization) -> Void = { _ in }
    var onSignOut: () -> Void = {}
    var onDeleteAccount: () async -> Bool = { false }

    @Environment(\.teamTheme) private var teamTheme
    @State private var showTeamPicker = false
    @State private var hapticEnabled = true
    @State private var highFiveEnabled = true
    @AppStorage("ball_strike_haptic_enabled") private var ballStrikeHapticEnabled = true
    @State private var showDeleteConfirm = false
    @State private var isDeletingAccount = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: AppSpacing.sm) {
                Text("설정")
                    .font(AppFont.h2)
                    .foregroundColor(.white)
                    .padding(.bottom, AppSpacing.lg)

                SettingsItem(icon: "person.2.fill", title: "응원 팀", subtitle: selectedTeam.teamName) {
                    showTeamPicker.toggle()
                }

                if showTeamPicker {
                    VStack(spacing: 0) {
                        ForEach(Team.selectableTeams) { team in
                            Button {
                                onChangeTeam(team)
                                showTeamPicker = false
                            } label: {
                                HStack(spacing: AppSpacing.md) {
                                    TeamLogo(team: team, size: 56)
                                    Text(team.teamName)
                                        .font(team == selectedTeam ? AppFont.labelBold : AppFont.label)
                                        .foregroundColor(team == selectedTeam ? .white : AppColors.gray300)
                                    Spacer()
                                    if team == selectedTeam {
                                        Image(systemName: "checkmark")
                                            .foregroundColor(teamTheme.primary)
                                            .font(AppFont.h4)
                                    }
                                }
                                .padding(AppSpacing.md)
                                .background(team == selectedTeam ? teamTheme.primary.opacity(0.2) : Color.clear)
                                .cornerRadius(AppRadius.sm)
                            }
                        }
                    }
                    .padding(AppSpacing.md)
                    .background(AppColors.gray800)
                    .cornerRadius(AppRadius.md)
                }

                // 계정 섹션
                SettingsSection(title: "계정")

                switch authState {
                case .loggedIn(_, let email, let provider):
                    VStack(spacing: AppSpacing.sm) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(AppFont.h4)
                            Text("로그인됨")
                                .font(AppFont.bodyMedium)
                                .foregroundColor(.green)
                            Spacer()
                        }
                        Text(email ?? (provider == "apple" ? "애플로 로그인됨" : "카카오로 로그인됨"))
                            .font(AppFont.bodyLg)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Button(action: onSignOut) {
                            Text("로그아웃")
                                .font(AppFont.body)
                                .foregroundColor(AppColors.gray400)
                                .frame(maxWidth: .infinity)
                                .padding(AppSpacing.md)
                                .background(AppColors.gray800)
                                .cornerRadius(AppRadius.sm)
                        }

                        Button { showDeleteConfirm = true } label: {
                            if isDeletingAccount {
                                ProgressView()
                                    .tint(.red)
                                    .frame(maxWidth: .infinity)
                                    .padding(AppSpacing.md)
                            } else {
                                Text("계정 삭제")
                                    .font(AppFont.body)
                                    .foregroundColor(.red)
                                    .frame(maxWidth: .infinity)
                                    .padding(AppSpacing.md)
                                    .background(AppColors.gray800)
                                    .cornerRadius(AppRadius.sm)
                            }
                        }
                        .disabled(isDeletingAccount)
                    }
                    .padding(AppSpacing.lg)
                    .background(AppColors.gray900)
                    .cornerRadius(AppRadius.md)

                case .loggedOut:
                    VStack(spacing: AppSpacing.sm) {
                        // 카카오 로그인
                        Button(action: onSignInWithKakao) {
                            HStack(spacing: AppSpacing.sm) {
                                Image(systemName: "message.fill")
                                    .font(AppFont.h4)
                                    // Reason: 카카오 브랜드 지정 색
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
                    }

                case .loading:
                    EmptyView()
                }

                // 알림 섹션
                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "알림")

                SettingsItemWithToggle(
                    icon: "waveform",
                    title: "경기 라이브 알림",
                    subtitle: "실시간 경기 내용을 워치로 알림 받기",
                    isOn: $hapticEnabled
                )

                SettingsItemWithToggle(
                    icon: "baseball",
                    title: "스트라이크 · 볼 알림",
                    subtitle: "볼, 스트라이크 이벤트를 워치에서 진동으로 받기",
                    isOn: $ballStrikeHapticEnabled
                )

                // 개발자 섹션 - 배포 시 숨김
                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "개발자")
                SettingsItem(icon: "applewatch.radiowaves.left.and.right", title: "워치 테스트", subtitle: "시뮬레이션 이벤트로 워치 동기화 테스트") {
                    onOpenWatchTest()
                }

                // 정보 섹션
                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "정보")

                SettingsItem(icon: "info.circle.fill", title: "버전", subtitle: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-") {}

                Spacer().frame(height: AppSpacing.bottomSafeSpacer)
            }
            .padding(AppSpacing.xxl)
        }
        .background(AppColors.gray950)
        .alert("계정 삭제", isPresented: $showDeleteConfirm) {
            Button("취소", role: .cancel) {}
            Button("삭제", role: .destructive) {
                isDeletingAccount = true
                Task {
                    _ = await onDeleteAccount()
                    isDeletingAccount = false
                }
            }
        } message: {
            Text("계정을 삭제하면 모든 데이터가 영구적으로 삭제되며 복구할 수 없습니다. 정말 삭제하시겠습니까?")
        }
    }
}

// MARK: - Subviews
private struct SettingsSection: View {
    let title: String
    var body: some View {
        Text(title)
            .font(AppFont.bodyMedium)
            .foregroundColor(AppColors.gray400)
            .padding(.top, AppSpacing.sm)
            .padding(.bottom, AppSpacing.sm)
    }
}

private struct SettingsItem: View {
    let icon: String
    let title: String
    let subtitle: String
    let onTap: () -> Void

    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: AppSpacing.lg) {
                Image(systemName: icon)
                    .foregroundColor(teamTheme.primary)
                    .font(AppFont.h3)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: AppSpacing.xxs) {
                    Text(title)
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(.white)
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(AppFont.body)
                            .foregroundColor(AppColors.gray400)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundColor(AppColors.gray500)
                    .font(AppFont.h4)
            }
            .padding(AppSpacing.lg)
            .background(AppColors.gray900)
            .cornerRadius(AppRadius.md)
        }
        .buttonStyle(.plain)
    }
}

private struct SettingsItemWithToggle: View {
    let icon: String
    let title: String
    let subtitle: String
    @Binding var isOn: Bool

    @Environment(\.teamTheme) private var teamTheme

    var body: some View {
        HStack(spacing: AppSpacing.lg) {
            Image(systemName: icon)
                .foregroundColor(teamTheme.primary)
                .font(AppFont.h3)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: AppSpacing.xxs) {
                Text(title)
                    .font(AppFont.bodyLgMedium)
                    .foregroundColor(.white)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray400)
                }
            }

            Spacer()

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(teamTheme.primary)
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }
}
