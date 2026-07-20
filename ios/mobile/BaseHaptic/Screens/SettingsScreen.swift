import SwiftUI
import AuthenticationServices

struct SettingsScreen: View {
    let selectedTeam: Team
    let teamDisplayNameStyle: TeamDisplayNameStyle
    let onChangeTeam: (Team) -> Void
    let onChangeTeamDisplayNameStyle: (TeamDisplayNameStyle) -> Void
    let activeTheme: ThemeData?
    let onSelectTheme: (ThemeData?) -> Void
    let onOpenWatchTest: () -> Void
    var authState: AuthState = .loggedOut
    var onSignInWithKakao: () -> Void = {}
    var onSignInWithApple: (ASAuthorization) -> Void = { _ in }
    var onSignOut: () -> Void = {}
    var onDeleteAccount: () async -> Bool = { false }

    @Environment(\.teamTheme) private var teamTheme
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject private var connectivity = PhoneConnectivityManager.shared
    @State private var showTeamPicker = false
    @State private var showTeamDisplayNameDialog = false
    @State private var pendingTeamDisplayNameStyle: TeamDisplayNameStyle = .team
    @State private var highFiveEnabled = true
    @AppStorage("event_video_enabled") private var eventVideoEnabled = true
    @State private var showDeleteConfirm = false
    @State private var manuallyOpenedReleaseNote: ReleaseNote?
    @State private var isDeletingAccount = false
    #if DEBUG
    @State private var ventingModeEnabled = VentingFeatureFlag.isEnabled
    #endif

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: AppSpacing.sm) {
                Text("설정")
                    .font(AppFont.h2)
                    .foregroundColor(.white)
                    .padding(.bottom, AppSpacing.lg)

                WatchInstallCard(
                    status: connectivity.watchCompanionStatus,
                    onInstall: { connectivity.refreshCompanionStatus() },
                    onRecheck: { connectivity.refreshCompanionStatus() },
                    onOpenWatchApp: { _ = WatchInstallLauncher.openCompanionWatchApp() },
                    onWatchTest: onOpenWatchTest
                )

                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "팀 설정")

                SettingsItem(
                    icon: "person.2.fill",
                    title: "응원 팀",
                    subtitle: selectedTeam.displayName(style: teamDisplayNameStyle)
                ) {
                    showTeamPicker.toggle()
                }

                SettingsItem(
                    icon: "textformat.size",
                    title: "팀 이름 표시",
                    subtitle: teamDisplayNameStyle == .team
                        ? "팀명으로 보기 · \(selectedTeam.displayName(style: .team))"
                        : "마스코트명으로 보기 · \(selectedTeam.displayName(style: .mascot))"
                ) {
                    if selectedTeam != .none {
                        pendingTeamDisplayNameStyle = teamDisplayNameStyle
                        showTeamDisplayNameDialog = true
                    }
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
                                    Text(team.displayName(style: teamDisplayNameStyle))
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

                // 알림 이벤트 섹션
                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "알림 이벤트")
                Text("경기 카드에서 watch나 잠금화면 보기를 켠 경기에만 적용됩니다.")
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray400)
                    .padding(.horizontal, AppSpacing.xs)
                    .padding(.bottom, AppSpacing.xs)

                EventFilterMatrix()

                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "워치 영상")

                SettingsItemWithToggle(
                    icon: "play.rectangle.fill",
                    title: "이벤트 영상 알림",
                    subtitle: "워치에서 캐릭터 영상 재생",
                    isOn: $eventVideoEnabled
                )
                .onChange(of: eventVideoEnabled) { _, newValue in
                    WatchThemeSyncManager.syncEventVideoEnabledToWatch(enabled: newValue)
                }

                #if DEBUG
                // DEBUG 섹션
                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "DEBUG")

                SettingsItemWithToggle(
                    icon: "squirrel.fill",
                    title: "분풀이 모드",
                    subtitle: "DEBUG 전용 피처",
                    isOn: $ventingModeEnabled
                )
                .onChange(of: ventingModeEnabled) { _, newValue in
                    VentingFeatureFlag.setEnabled(newValue)
                }
                #endif

                // 정보 섹션
                Spacer().frame(height: AppSpacing.lg)
                SettingsSection(title: "정보")

                SettingsItem(icon: "info.circle.fill", title: "버전", subtitle: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-") {
                    let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
                    #if DEBUG
                    manuallyOpenedReleaseNote = ReleaseNotes.notes(for: currentVersion) ?? ReleaseNotes.latest
                    #else
                    manuallyOpenedReleaseNote = ReleaseNotes.notes(for: currentVersion)
                    #endif
                }

                Spacer().frame(height: AppSpacing.bottomSafeSpacer)
            }
            .padding(AppSpacing.xxl)
        }
        .background(AppColors.gray950)
        .onAppear { connectivity.refreshCompanionStatus() }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active { connectivity.refreshCompanionStatus() }
        }
        .overlay {
            if let note = manuallyOpenedReleaseNote {
                WhatsNewSheet(
                    note: note,
                    onConfirm: { manuallyOpenedReleaseNote = nil }
                )
                .transition(.opacity)
                .zIndex(1)
            }
            if showTeamDisplayNameDialog && selectedTeam != .none {
                TeamDisplayNameStyleDialog(
                    team: selectedTeam,
                    selectedStyle: $pendingTeamDisplayNameStyle,
                    onConfirm: {
                        onChangeTeamDisplayNameStyle(pendingTeamDisplayNameStyle)
                        showTeamDisplayNameDialog = false
                    },
                    onDismiss: {
                        showTeamDisplayNameDialog = false
                    }
                )
                .transition(.opacity)
                .zIndex(2)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: manuallyOpenedReleaseNote?.id)
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

private struct EventFilterMatrix: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: AppSpacing.md) {
                Text("이벤트")
                    .font(AppFont.caption)
                    .foregroundColor(AppColors.gray500)
                Spacer()
                Text("Watch")
                    .font(AppFont.caption)
                    .foregroundColor(AppColors.gray400)
                    .frame(width: 58)
                Text("잠금")
                    .font(AppFont.caption)
                    .foregroundColor(AppColors.gray400)
                    .frame(width: 58)
            }
            .padding(.horizontal, AppSpacing.lg)
            .padding(.top, AppSpacing.md)
            .padding(.bottom, AppSpacing.sm)

            ForEach(Array(EventFilterOption.all.enumerated()), id: \.element.id) { index, option in
                EventFilterMatrixRow(option: option)

                if index < EventFilterOption.all.count - 1 {
                    Divider()
                        .background(AppColors.gray800)
                        .padding(.leading, AppSpacing.lg)
                }
            }
        }
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.md)
    }
}

private struct EventFilterMatrixRow: View {
    let option: EventFilterOption
    @AppStorage private var watchEnabled: Bool
    @AppStorage private var lockScreenEnabled: Bool

    @Environment(\.teamTheme) private var teamTheme

    init(option: EventFilterOption) {
        self.option = option
        _watchEnabled = AppStorage(
            wrappedValue: option.defaultEnabled(for: .watch),
            option.storageKey(for: .watch)
        )
        _lockScreenEnabled = AppStorage(
            wrappedValue: option.defaultEnabled(for: .lockScreen),
            option.storageKey(for: .lockScreen)
        )
    }

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: option.icon)
                .font(AppFont.bodyLg)
                .foregroundColor(teamTheme.primary)
                .frame(width: 22)

            Text(option.title)
                .font(AppFont.bodyLgMedium)
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.82)

            Spacer(minLength: AppSpacing.sm)

            EventChannelToggleChip(
                accessibilityTitle: "\(option.title) Watch 알림",
                isOn: watchEnabled,
                activeColor: teamTheme.primary
            ) {
                watchEnabled.toggle()
                WatchThemeSyncManager.syncEventFiltersToWatch(
                    filters: EventFilterOption.currentValues(channel: .watch)
                )
            }

            EventChannelToggleChip(
                accessibilityTitle: "\(option.title) 잠금화면 알림",
                isOn: lockScreenEnabled,
                activeColor: teamTheme.primary
            ) {
                lockScreenEnabled.toggle()
            }
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, AppSpacing.md)
    }
}

private struct EventChannelToggleChip: View {
    let accessibilityTitle: String
    let isOn: Bool
    let activeColor: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(isOn ? "ON" : "OFF")
                .font(AppFont.caption)
                .foregroundColor(isOn ? AppColors.gray950 : AppColors.gray300)
                .frame(width: 58, height: 32)
                .background(isOn ? activeColor : AppColors.gray800)
                .overlay(
                    RoundedRectangle(cornerRadius: AppRadius.sm)
                        .stroke(isOn ? activeColor : AppColors.gray700, lineWidth: 1)
                )
                .cornerRadius(AppRadius.sm)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityTitle)
        .accessibilityValue(isOn ? "켬" : "끔")
        .accessibilityAddTraits(isOn ? [.isButton, .isSelected] : .isButton)
    }
}

extension EventFilterOption {
    static func currentValues(channel: EventNotificationChannel) -> [String: Bool] {
        var values: [String: Bool] = [:]
        let defaults = UserDefaults.standard
        for option in EventFilterOption.all {
            let key = option.storageKey(for: channel)
            values[key] = defaults.object(forKey: key) as? Bool ?? option.defaultEnabled(for: channel)
        }
        return values
    }
}
