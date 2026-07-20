#if DEBUG
import SwiftUI

// MARK: - VentingDestroyedScreen

/// 완파 화면.
///
/// - "분풀이 완료" 상태와 재도전 버튼을 표시한다.
/// - 재도전 허용 여부는 VentingGateProviding 프로토콜로 판정.
/// - Phase 1: AlwaysAllowGate → 광고 없이 항상 허용.
#if DEBUG
struct VentingDestroyedScreen: View {

    let viewModel: VentingRoomViewModel
    let onRetry: () -> Void
    let onClose: () -> Void

    @State private var retryAllowed: Bool = true
    @State private var isCheckingRetry: Bool = false
    @State private var showConfetti: Bool = false

    var body: some View {
        ZStack {
            AppColors.gray950.ignoresSafeArea()

            VStack(spacing: AppSpacing.xxxl) {
                // 닫기 버튼 (우상단)
                HStack {
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark")
                            .font(AppFont.bodyLgBold)
                            .foregroundColor(AppColors.gray400)
                            .padding(AppSpacing.md)
                            .background(Circle().fill(AppColors.gray800))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.top, AppSpacing.lg)

                Spacer()

                // 완파 연출
                VStack(spacing: AppSpacing.xl) {
                    // 완파된 인형 (익명 펭귄 스프라이트)
                    Image(DestructionStage.destroyed.dollImageName)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 180, height: 180)
                        .scaleEffect(showConfetti ? 1.0 : 0.5)
                        .animation(.spring(response: 0.4, dampingFraction: 0.6), value: showConfetti)

                    VStack(spacing: AppSpacing.sm) {
                        Text("분풀이 완료!")
                            .font(AppFont.h3Bold)
                            .foregroundColor(AppColors.red400)

                        Text("시원하게 털어냈습니다 🎉")
                            .font(AppFont.bodyLgMedium)
                            .foregroundColor(AppColors.gray300)
                    }

                    // 대상 정보
                    VStack(spacing: AppSpacing.xs) {
                        Text(viewModel.selectedTarget.roleLabel)
                            .font(AppFont.captionBold)
                            .foregroundColor(AppColors.red400)
                            .padding(.horizontal, AppSpacing.md)
                            .padding(.vertical, AppSpacing.xs)
                            .background(AppColors.red500.opacity(0.2))
                            .clipShape(Capsule())

                        Text(viewModel.selectedTarget.eventDescription)
                            .font(AppFont.body)
                            .foregroundColor(AppColors.gray400)
                    }
                }

                // 첫 완파 배지
                if viewModel.viewModel_isFirstDestruction {
                    firstDestructionBadge
                }

                Spacer()

                // 재도전 버튼 영역
                VStack(spacing: AppSpacing.md) {
                    if isCheckingRetry {
                        ProgressView()
                            .tint(AppColors.red400)
                            .padding()
                    } else if retryAllowed {
                        Button(action: onRetry) {
                            HStack(spacing: AppSpacing.sm) {
                                Image(systemName: "arrow.clockwise")
                                    .font(AppFont.bodyLgBold)
                                Text("재도전하기")
                                    .font(AppFont.bodyLgMedium)
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AppSpacing.lg)
                            .background(AppColors.red500)
                            .cornerRadius(AppRadius.md)
                        }
                        .buttonStyle(.plain)
                    }

                    Button(action: onClose) {
                        Text("홈으로 돌아가기")
                            .font(AppFont.bodyMedium)
                            .foregroundColor(AppColors.gray400)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, AppSpacing.md)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.bottom, AppSpacing.xxxl)
            }
        }
        .navigationBarHidden(true)
        .task {
            showConfetti = true
            isCheckingRetry = true
            retryAllowed = await viewModel.canRetry()
            isCheckingRetry = false
        }
    }

    // MARK: - First Destruction Badge

    private var firstDestructionBadge: some View {
        HStack(spacing: AppSpacing.sm) {
            Image(systemName: "star.fill")
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.yellow500)
            Text("오늘 처음 완파 달성!")
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.yellow400)
        }
        .padding(.horizontal, AppSpacing.lg)
        .padding(.vertical, AppSpacing.sm)
        .background(AppColors.yellow500.opacity(0.15))
        .clipShape(Capsule())
        .overlay(
            Capsule()
                .stroke(AppColors.yellow500.opacity(0.4), lineWidth: 1)
        )
    }
}

// MARK: - ViewModel Extension

private extension VentingRoomViewModel {
    /// 현재 경기에서 첫 완파인지 여부 (UserDefaults에서 읽음)
    var viewModel_isFirstDestruction: Bool {
        // 완파가 됐을 때 이미 기록됐으므로, 기록된 게 있다 = 첫 완파
        // DestructionStateMachine.hasRecordedFirstDestruction을 직접 읽을 수 없으므로
        // UserDefaults에서 직접 확인
        UserDefaults.standard.bool(forKey: "venting_first_destruction_\(gameContext.gameId)")
    }
}
#endif
#endif
