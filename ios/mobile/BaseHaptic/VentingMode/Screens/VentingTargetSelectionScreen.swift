#if DEBUG
import SwiftUI

// MARK: - VentingTargetSelectionScreen

/// 아쉬운 순간 TOP5 선택 화면.
///
/// - 선수 후보(최대 5명) + 감독 고정 6번째 항목을 표시한다.
/// - 후보 5명 미만이어도 있는 후보만 부분 표시하며 진입을 차단하지 않는다.
/// - "기록 기반 자동 선정이며 공식 평가가 아닙니다" 면책 문구 상시 노출.
/// - 선수 이름·등번호·실제 외형 표기 없음.
#if DEBUG
struct VentingTargetSelectionScreen: View {

    let context: VentingGameContext
    let gate: any VentingGateProviding
    let onBack: () -> Void
    let onSelectTarget: (VentingTarget) -> Void

    @State private var selectedTarget: VentingTarget?

    private var managerOption: VentingManagerOption {
        VentingManagerOption(eventDescription: context.managerEventDescription)
    }

    var body: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()

            VStack(spacing: 0) {
                // 네비게이션 헤더
                headerBar

                ScrollView {
                    VStack(spacing: AppSpacing.md) {
                        // 경기 스코어 요약
                        scoreHeader
                            .padding(.top, AppSpacing.lg)

                        // 안내 문구
                        Text("오늘의 아쉬운 순간 TOP\(min(context.candidates.count, 5))")
                            .font(AppFont.h5Bold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, AppSpacing.xxl)
                            .padding(.top, AppSpacing.lg)

                        // 선수 후보 목록
                        VStack(spacing: AppSpacing.sm) {
                            ForEach(Array(context.candidates.prefix(5).enumerated()), id: \.element.id) { index, candidate in
                                let target = VentingTarget.player(candidate)
                                TargetRow(
                                    rank: index + 1,
                                    roleLabel: candidate.roleLabel,
                                    eventDescription: candidate.eventDescription,
                                    isSelected: selectedTarget == target,
                                    onTap: {
                                        selectedTarget = target
                                    }
                                )
                            }

                            // 감독 고정 6번째
                            TargetRow(
                                rank: nil,
                                roleLabel: managerOption.label,
                                eventDescription: managerOption.eventDescription,
                                isSelected: selectedTarget == .manager(managerOption),
                                onTap: {
                                    selectedTarget = .manager(managerOption)
                                }
                            )
                        }
                        .padding(.horizontal, AppSpacing.xxl)

                        // 면책 문구 (상시 노출, 절대 제거 금지)
                        disclaimerText

                        Spacer().frame(height: AppSpacing.xxxl)
                    }
                }

                // 분풀이 시작 버튼
                VStack(spacing: 0) {
                    Divider().background(AppColors.gray800)
                    Button {
                        if let target = selectedTarget {
                            onSelectTarget(target)
                        }
                    } label: {
                        Text(selectedTarget == nil ? "대상을 선택하세요" : "분풀이 시작하기")
                            .font(AppFont.bodyLgMedium)
                            .foregroundColor(selectedTarget == nil ? AppColors.gray400 : .white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AppSpacing.lg)
                            .background(selectedTarget == nil ? AppColors.gray800 : AppColors.red500)
                            .cornerRadius(AppRadius.md)
                    }
                    .disabled(selectedTarget == nil)
                    .padding(.horizontal, AppSpacing.xxl)
                    .padding(.vertical, AppSpacing.lg)
                    .background(AppColors.gray950)
                }
            }
        }
        .navigationBarHidden(true)
    }

    // MARK: - Header Bar

    private var headerBar: some View {
        HStack {
            Button(action: onBack) {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "chevron.left")
                        .font(AppFont.bodyLgBold)
                    Text("홈")
                        .font(AppFont.bodyMedium)
                }
                .foregroundColor(.white)
            }
            .buttonStyle(.plain)

            Spacer()

            Text("분풀이 모드")
                .font(AppFont.h5Bold)
                .foregroundColor(.white)

            Spacer()

            // 균형을 위한 공간
            Color.clear
                .frame(width: 60, height: 1)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.vertical, AppSpacing.lg)
        .background(AppColors.gray950)
    }

    // MARK: - Score Header

    private var scoreHeader: some View {
        HStack(spacing: AppSpacing.lg) {
            VStack(spacing: AppSpacing.xs) {
                Text(teamDisplayName(context.myTeamId))
                    .font(AppFont.captionBold)
                    .foregroundColor(AppColors.gray400)
                Text("\(context.myScore)")
                    .font(AppFont.h2)
                    .foregroundColor(AppColors.red400)
            }
            .frame(maxWidth: .infinity)

            Text("최종")
                .font(AppFont.micro)
                .foregroundColor(AppColors.gray500)

            VStack(spacing: AppSpacing.xs) {
                Text("상대팀")
                    .font(AppFont.captionBold)
                    .foregroundColor(AppColors.gray400)
                Text("\(context.opponentScore)")
                    .font(AppFont.h2)
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(AppSpacing.lg)
        .background(AppColors.gray900)
        .cornerRadius(AppRadius.lg)
        .padding(.horizontal, AppSpacing.xxl)
    }

    // MARK: - Disclaimer

    private var disclaimerText: some View {
        HStack(spacing: AppSpacing.xs) {
            Image(systemName: "info.circle")
                .font(AppFont.micro)
                .foregroundColor(AppColors.gray500)
            Text("기록 기반 자동 선정이며 공식 평가가 아닙니다")
                .font(AppFont.micro)
                .foregroundColor(AppColors.gray500)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.top, AppSpacing.md)
    }

    // MARK: - Helper

    private func teamDisplayName(_ teamId: String) -> String {
        switch teamId.uppercased() {
        // KBO team codes (Phase 2 backend format)
        case "HH": return "한화"
        case "LG": return "LG"
        case "OB": return "두산"
        case "WO": return "키움"
        case "SS": return "삼성"
        case "LT": return "롯데"
        case "SK": return "SSG"
        case "KT": return "KT"
        case "HT": return "KIA"
        case "NC": return "NC"
        // Team rawValue format (Phase 1 mock format)
        case "HANWHA": return "한화"
        case "DOOSAN": return "두산"
        case "KIWOOM": return "키움"
        case "SAMSUNG": return "삼성"
        case "LOTTE": return "롯데"
        case "SSG": return "SSG"
        case "KIA": return "KIA"
        default: return teamId
        }
    }
}

// MARK: - TargetRow

private struct TargetRow: View {
    let rank: Int?
    let roleLabel: String
    let eventDescription: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: AppSpacing.md) {
                // 순위 뱃지
                if let rank {
                    Text("\(rank)")
                        .font(AppFont.captionBold)
                        .foregroundColor(isSelected ? AppColors.red400 : AppColors.gray500)
                        .frame(width: 24, height: 24)
                        .background(
                            Circle()
                                .fill(isSelected ? AppColors.red500.opacity(0.2) : AppColors.gray800)
                        )
                } else {
                    Image(systemName: "person.bust")
                        .font(AppFont.captionBold)
                        .foregroundColor(isSelected ? AppColors.red400 : AppColors.gray500)
                        .frame(width: 24, height: 24)
                }

                // 역할 + 사건 문구
                VStack(alignment: .leading, spacing: AppSpacing.xxs) {
                    Text(roleLabel)
                        .font(AppFont.captionBold)
                        .foregroundColor(isSelected ? .white : AppColors.gray300)
                    Text(eventDescription)
                        .font(AppFont.body)
                        .foregroundColor(isSelected ? AppColors.red300 : AppColors.gray400)
                        .lineLimit(2)
                }

                Spacer()

                // 선택 인디케이터
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(AppFont.bodyLg)
                    .foregroundColor(isSelected ? AppColors.red500 : AppColors.gray700)
            }
            .padding(AppSpacing.lg)
            .background(isSelected ? AppColors.red500.opacity(0.12) : AppColors.gray900)
            .cornerRadius(AppRadius.md)
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .stroke(isSelected ? AppColors.red500.opacity(0.5) : AppColors.gray800, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.15), value: isSelected)
    }
}
#endif

// MARK: - AppColors extension (red300)
// red300은 기존 Colors.swift에 없으므로 VentingMode 전용으로 추가
#if DEBUG
private extension AppColors {
    static let red300 = Color(hex: 0xFCA5A5)
}
#endif
#endif
