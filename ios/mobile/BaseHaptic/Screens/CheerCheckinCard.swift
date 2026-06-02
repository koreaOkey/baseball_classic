import SwiftUI

enum CheerCheckinState {
    case ready
    case checkedIn
    case outsideVenue
    case permissionNeeded
    case noGameToday
    case dark
}

struct CheerCheckinCard: View {
    let stadiumName: String
    let stadiumRegion: String
    let teamLabel: String
    let state: CheerCheckinState
    let onConfirm: () -> Void
    let onDismiss: () -> Void

    init(
        stadiumName: String,
        stadiumRegion: String = "서울",
        teamLabel: String,
        state: CheerCheckinState = .ready,
        onConfirm: @escaping () -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.stadiumName = stadiumName
        self.stadiumRegion = stadiumRegion
        self.teamLabel = teamLabel
        self.state = state
        self.onConfirm = onConfirm
        self.onDismiss = onDismiss
    }

    private var status: CheckinStatus {
        switch state {
        case .ready:
            return CheckinStatus(
                icon: "mappin.and.ellipse",
                label: "\(stadiumName) 근처",
                title: "오늘 직관 인증",
                subtitle: "\(teamLabel) 경기 시각에 위치가 함께 울려요",
                button: "체크인하기",
                footer: "위치 확인 완료",
                isEnabled: true,
                showsDismiss: true
            )
        case .checkedIn:
            return CheckinStatus(
                icon: "checkmark.seal.fill",
                label: "체크인 완료",
                title: "오늘 응원 인증 완료",
                subtitle: "팀 체크인 랭킹에 반영될 예정이에요",
                button: "완료됨",
                footer: "위치 확인 완료",
                isEnabled: false,
                showsDismiss: false
            )
        case .outsideVenue:
            return CheckinStatus(
                icon: "mappin.and.ellipse",
                label: "구장 밖",
                title: "오늘 직관 인증",
                subtitle: "\(teamLabel) 경기 구장 반경 500m 안에서 체크인할 수 있어요",
                button: "체크인하기",
                footer: "현재 경기장 장소가 아닙니다",
                isEnabled: true,
                showsDismiss: true
            )
        case .permissionNeeded:
            return CheckinStatus(
                icon: "lock.fill",
                label: "권한 필요",
                title: "위치 권한을 켜면 자동으로 확인해요",
                subtitle: "운영 활성화 전까지는 숨김 상태로 보존됩니다",
                button: "권한 안내",
                footer: "위치 확인 대기",
                isEnabled: false,
                showsDismiss: false
            )
        case .noGameToday:
            return CheckinStatus(
                icon: "baseball.fill",
                label: "오늘 경기 없음",
                title: "오늘은 체크인할 경기가 없어요",
                subtitle: "랭킹은 계속 볼 수 있고 다음 경기 때 다시 알려드릴게요",
                button: "대기 중",
                footer: "경기 일정 대기",
                isEnabled: false,
                showsDismiss: false
            )
        case .dark:
            return CheckinStatus(
                icon: "mappin.and.ellipse",
                label: "숨김 상태",
                title: "경기장 체크인 준비 중",
                subtitle: "내 팀 탭에서 체크인, 워치 응원, 랭킹을 한 번에 제공합니다",
                button: "준비 중",
                footer: "위치 확인 준비 중",
                isEnabled: false,
                showsDismiss: false
            )
        }
    }

    var body: some View {
        let status = status

        VStack(spacing: AppSpacing.lg) {
            ZStack(alignment: .topTrailing) {
                Image("stadium_checkin_hero")
                    .resizable()
                    .scaledToFill()
                    .frame(maxWidth: .infinity)
                    .frame(height: 164)

                VStack(spacing: 2) {
                    Text(stadiumName)
                        .font(AppFont.captionBold)
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(stadiumRegion)
                        .font(AppFont.tiny)
                        .foregroundColor(AppColors.gray300)
                        .lineLimit(1)
                }
                .padding(.horizontal, AppSpacing.lg)
                .padding(.vertical, AppSpacing.sm)
                .background(Color(red: 2/255, green: 6/255, blue: 23/255).opacity(0.72))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(.top, AppSpacing.md)
                .padding(.trailing, AppSpacing.md)
            }
                .frame(maxWidth: .infinity)
                .frame(height: 164)
                .clipShape(RoundedRectangle(cornerRadius: 16))

            VStack(spacing: AppSpacing.sm) {
                Text(status.title)
                    .font(AppFont.h3Bold)
                    .foregroundColor(.white)
                    .lineLimit(1)
                Text(status.subtitle)
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray300)
                    .multilineTextAlignment(.center)
            }

            Button(action: onConfirm) {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(AppFont.captionBold)
                    Text(status.button)
                        .font(AppFont.captionBold)
                }
                .foregroundColor(status.isEnabled ? .white : AppColors.gray400)
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.md)
                .background(
                    Group {
                        if status.isEnabled {
                            LinearGradient(
                                colors: [
                                    Color(red: 239/255, green: 68/255, blue: 68/255),
                                    Color(red: 219/255, green: 39/255, blue: 119/255)
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        } else {
                            LinearGradient(
                                colors: [Color.white.opacity(0.12), Color.white.opacity(0.08)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        }
                    }
                )
                .clipShape(Capsule())
            }
            .disabled(!status.isEnabled)
            .buttonStyle(.plain)

            HStack(spacing: AppSpacing.xs) {
                Image(systemName: status.icon)
                    .font(AppFont.tiny)
                Text(status.footer)
                    .font(AppFont.tiny)
            }
            .foregroundColor(footerColor)
        }
        .padding(AppSpacing.lg)
        .background(
            LinearGradient(
                colors: [Color(red: 7/255, green: 21/255, blue: 46/255), AppColors.gray800],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private var teamAccent: Color {
        Team.selectableTeams.first { $0.teamName == teamLabel }?.color ?? AppColors.blue500
    }

    private var footerColor: Color {
        if status.footer == "위치 확인 완료" {
            return Color(red: 34/255, green: 197/255, blue: 94/255)
        }
        if status.footer == "현재 경기장 장소가 아닙니다" {
            return Color(red: 249/255, green: 115/255, blue: 22/255)
        }
        return teamAccent
    }
}

private struct CheckinStatus {
    let icon: String
    let label: String
    let title: String
    let subtitle: String
    let button: String
    let footer: String
    let isEnabled: Bool
    let showsDismiss: Bool
}
