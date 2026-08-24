import SwiftUI

// MARK: - VentingDestroyedScreen

/// 완파 화면.
///
/// - "분풀이 완료" 상태와 재도전 버튼을 표시한다.
/// - 재도전 허용 여부는 VentingGateProviding 프로토콜로 판정.
/// - 운영(RewardedAdGate): 재도전은 Rewarded 광고 1회 시청 후 허용.
struct VentingDestroyedScreen: View {

    let viewModel: VentingRoomViewModel
    var entrySource: String = "unknown"
    let onRetry: () -> Void
    let onClose: () -> Void

    // 기본은 광고 경로 — 게이트 판정 전 무료 버튼이 잠깐 보이는 플래시 방지
    @State private var retryAllowed: Bool = false
    @State private var isCheckingRetry: Bool = false
    @State private var isRequestingAd: Bool = false
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
                    // 완파된 인형 (익명 펭귄 스프라이트) — 찢김 연출이 어두운 배경과 어울려 글로우 없음
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
                        // 무료 재도전 (디버그/프리뷰 AlwaysAllowGate 경로 — 광고 지표 미보고)
                        retryButton(icon: "arrow.clockwise", title: "재도전하기") {
                            onRetry()
                        }
                    } else {
                        // 운영 경로: Rewarded 광고 1회 시청 후 재도전
                        retryButton(
                            icon: "play.rectangle.fill",
                            title: "광고 보고 재도전하기",
                            isBusy: isRequestingAd
                        ) {
                            requestAdRetry()
                        }
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
            // 6.1 지표: 재도전 프롬프트(완파 화면) 노출 = retry_prompt_shown
            VentingEventsReporter.report(
                eventType: "retry_prompt_shown",
                team: viewModel.gameContext.myTeamId,
                entrySource: entrySource,
                gameId: viewModel.gameContext.gameId
            )
            showConfetti = true
            isCheckingRetry = true
            retryAllowed = await viewModel.canRetry()
            isCheckingRetry = false
        }
    }

    // MARK: - Retry Button / Ad Gate

    private func retryButton(
        icon: String,
        title: String,
        isBusy: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: AppSpacing.sm) {
                if isBusy {
                    ProgressView()
                        .tint(.white)
                } else {
                    Image(systemName: icon)
                        .font(AppFont.bodyLgBold)
                }
                Text(title)
                    .font(AppFont.bodyLgMedium)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.lg)
            .background(AppColors.red500.opacity(isBusy ? 0.6 : 1))
            .cornerRadius(AppRadius.md)
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
    }

    /// Rewarded 광고를 요청하고 판정에 따라 재도전을 진행한다.
    private func requestAdRetry() {
        guard !isRequestingAd else { return }
        // 6.1 지표: 재도전 광고 진입 = retry_ad_start
        VentingEventsReporter.report(
            eventType: "retry_ad_start",
            team: viewModel.gameContext.myTeamId,
            entrySource: entrySource,
            gameId: viewModel.gameContext.gameId
        )
        isRequestingAd = true
        Task {
            let verdict = await viewModel.requestRetry()
            isRequestingAd = false
            switch verdict {
            case .adRewarded:
                // 6.1 지표: 광고 보상 획득 = retry_ad_complete
                VentingEventsReporter.report(
                    eventType: "retry_ad_complete",
                    team: viewModel.gameContext.myTeamId,
                    entrySource: entrySource,
                    gameId: viewModel.gameContext.gameId
                )
                onRetry()
            case .allowedFree:
                // 광고 로드 실패 폴백 — 사용자 귀책 아님, 광고 완료로 집계하지 않음
                onRetry()
            case .denied:
                break
            }
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
