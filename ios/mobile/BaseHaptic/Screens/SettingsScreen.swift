import SwiftUI
import AuthenticationServices

struct SettingsScreen: View {
    let selectedTeam: Team
    let onChangeTeam: (Team) -> Void
    let purchasedThemes: [ThemeData]
    let activeTheme: ThemeData?
    let onSelectTheme: (ThemeData?) -> Void
    let onOpenWatchTest: () -> Void
    var authState: AuthState = .loggedOut
    var onSignInWithKakao: () -> Void = {}
    var onSignInWithApple: () -> Void = {}
    var onSignOut: () -> Void = {}

    @Environment(\.teamTheme) private var teamTheme
    @State private var showTeamPicker = false
    @State private var hapticEnabled = true
    @State private var highFiveEnabled = true
    @AppStorage("ball_strike_haptic_enabled") private var ballStrikeHapticEnabled = true

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                Text("설정")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.bottom, 16)

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
                                HStack(spacing: 12) {
                                    TeamLogo(team: team, size: 32)
                                    Text(team.teamName)
                                        .font(.system(size: 15, weight: team == selectedTeam ? .bold : .regular))
                                        .foregroundColor(team == selectedTeam ? .white : AppColors.gray300)
                                    Spacer()
                                    if team == selectedTeam {
                                        Image(systemName: "checkmark")
                                            .foregroundColor(teamTheme.primary)
                                            .font(.system(size: 20))
                                    }
                                }
                                .padding(12)
                                .background(team == selectedTeam ? teamTheme.primary.opacity(0.2) : Color.clear)
                                .cornerRadius(8)
                            }
                        }
                    }
                    .padding(12)
                    .background(AppColors.gray800)
                    .cornerRadius(12)
                }

                // 계정 섹션
                SettingsSection(title: "계정")

                switch authState {
                case .loggedIn(_, let email, _):
                    VStack(spacing: 8) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.system(size: 20))
                            Text("로그인됨")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.green)
                            Spacer()
                        }
                        Text(email ?? "카카오 계정")
                            .font(.system(size: 16))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Button(action: onSignOut) {
                            Text("로그아웃")
                                .font(.system(size: 14))
                                .foregroundColor(AppColors.gray400)
                                .frame(maxWidth: .infinity)
                                .padding(12)
                                .background(AppColors.gray800)
                                .cornerRadius(8)
                        }
                    }
                    .padding(16)
                    .background(AppColors.gray900)
                    .cornerRadius(12)

                case .loggedOut:
                    VStack(spacing: 8) {
                        // 카카오 로그인
                        Button(action: onSignInWithKakao) {
                            HStack(spacing: 8) {
                                Text("\u{1F4AC}")
                                    .font(.system(size: 20))
                                Text("카카오로 로그인")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Color(red: 0.1, green: 0.1, blue: 0.1))
                            }
                            .frame(maxWidth: .infinity)
                            .padding(16)
                            .background(Color(red: 254/255, green: 229/255, blue: 0))
                            .cornerRadius(12)
                        }

                        // Apple 로그인
                        SignInWithAppleButton(.signIn) { _ in
                        } onCompletion: { _ in
                            onSignInWithApple()
                        }
                        .signInWithAppleButtonStyle(.white)
                        .frame(height: 50)
                        .cornerRadius(12)
                    }

                case .loading:
                    EmptyView()
                }

                // 알림 섹션
                Spacer().frame(height: 16)
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

                // 원격 하이파이브 - 추후 공개
                // SettingsItemWithToggle(
                //     icon: "antenna.radiowaves.left.and.right",
                //     title: "원격 하이파이브",
                //     subtitle: "친구와 득점 순간을 함께 공유",
                //     isOn: $highFiveEnabled
                // )

                // 개발자 섹션
                Spacer().frame(height: 16)
                SettingsSection(title: "개발자")

                SettingsItem(icon: "applewatch", title: "워치 테스트", subtitle: "워치 데이터 전송 테스트") {
                    onOpenWatchTest()
                }

                // 정보 섹션
                Spacer().frame(height: 16)
                SettingsSection(title: "정보")

                SettingsItem(icon: "info.circle.fill", title: "버전", subtitle: "1.0.0") {}

                Spacer().frame(height: 80)
            }
            .padding(24)
        }
        .background(AppColors.gray950)
    }
}

// MARK: - Subviews
private struct SettingsSection: View {
    let title: String
    var body: some View {
        Text(title)
            .font(.system(size: 14, weight: .medium))
            .foregroundColor(AppColors.gray400)
            .padding(.top, 8)
            .padding(.bottom, 8)
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
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .foregroundColor(teamTheme.primary)
                    .font(.system(size: 24))
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(.white)
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.system(size: 14))
                            .foregroundColor(AppColors.gray400)
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .foregroundColor(AppColors.gray500)
                    .font(.system(size: 20))
            }
            .padding(16)
            .background(AppColors.gray900)
            .cornerRadius(12)
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
        HStack(spacing: 16) {
            Image(systemName: icon)
                .foregroundColor(teamTheme.primary)
                .font(.system(size: 24))
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 14))
                        .foregroundColor(AppColors.gray400)
                }
            }

            Spacer()

            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(teamTheme.primary)
        }
        .padding(16)
        .background(AppColors.gray900)
        .cornerRadius(12)
    }
}
