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
    var backLabel: String = "홈"
    let onSelectTarget: (VentingTarget) -> Void

    @State private var selectedTarget: VentingTarget?
    @State private var customName: String = ""

    private var isLive: Bool { context.inningLabel != nil }

    private var isCustomSelected: Bool {
        if case .custom = selectedTarget { return true }
        return false
    }

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
                        Text(isLive ? "지금까지의 아쉬운 순간" : "오늘의 아쉬운 순간 TOP\(min(context.candidates.count, 5))")
                            .font(AppFont.h5Bold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, AppSpacing.xxl)
                            .padding(.top, AppSpacing.lg)

                        if context.candidates.isEmpty {
                            Text("아직 집계된 아쉬운 순간이 없어요.\n감독을 고르거나 직접 입력으로 지목해보세요.")
                                .font(AppFont.caption)
                                .foregroundColor(AppColors.gray500)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, AppSpacing.xxl)
                        }

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

                            // 직접 입력 — 입력값은 표시 전용, 저장·전송하지 않는다.
                            CustomTargetRow(
                                name: $customName,
                                isSelected: isCustomSelected,
                                onNameChange: { name in
                                    let trimmed = name.trimmingCharacters(in: .whitespaces)
                                    if !trimmed.isEmpty {
                                        selectedTarget = .custom(trimmed)
                                    } else if isCustomSelected {
                                        selectedTarget = nil
                                    }
                                },
                                onTap: {
                                    let trimmed = customName.trimmingCharacters(in: .whitespaces)
                                    if !trimmed.isEmpty {
                                        selectedTarget = .custom(trimmed)
                                    }
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
                    Text(backLabel)
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

            Text(context.inningLabel ?? "최종")
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

// MARK: - CustomTargetRow (직접 입력)

/// 선수명 직접 입력 행. 입력 즉시 `VentingTarget.custom`으로 선택되며,
/// 입력값을 지우면 선택도 해제된다.
#if DEBUG
private struct CustomTargetRow: View {
    @Binding var name: String
    let isSelected: Bool
    let onNameChange: (String) -> Void
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: AppSpacing.md) {
            Image(systemName: "pencil")
                .font(AppFont.captionBold)
                .foregroundColor(isSelected ? AppColors.red400 : AppColors.gray500)
                .frame(width: 24, height: 24)

            VStack(alignment: .leading, spacing: AppSpacing.xxs) {
                Text("직접 입력")
                    .font(AppFont.captionBold)
                    .foregroundColor(isSelected ? .white : AppColors.gray300)
                TextField(
                    "",
                    text: $name,
                    prompt: Text("화풀이할 선수명을 입력하세요")
                        .font(AppFont.body)
                        .foregroundColor(AppColors.gray500)
                )
                .font(AppFont.body)
                .foregroundColor(isSelected ? AppColors.red300Input : .white)
                .tint(AppColors.red400)
                .autocorrectionDisabled()
                .onChange(of: name) { newValue in
                    onNameChange(newValue)
                }
            }

            Spacer()

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
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
        .animation(.easeInOut(duration: 0.15), value: isSelected)
    }
}

private extension AppColors {
    static let red300Input = Color(hex: 0xFCA5A5)
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
