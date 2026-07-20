#if DEBUG
import SwiftUI

// MARK: - VentingRoomScreen

/// 분풀이 룸 화면.
///
/// - 펭귄 인형(도형 기반 플레이스홀더) + 데미지 게이지를 표시한다.
/// - 인형에 선수 이름·등번호·실제 외형 표기 없음.
/// - 탭 연타로 게이지 증가, 3단계 연출(idle/cracked/burst/destroyed).
/// - 탭 경햅틱·단계 전환 중햅틱·완파 성공 햅틱 — VentingRoomViewModel이 구동.
#if DEBUG
struct VentingRoomScreen: View {

    @ObservedObject var viewModel: VentingRoomViewModel
    let onBack: () -> Void
    let onDestroyed: () -> Void

    @State private var showDestroyedTransition = false

    var body: some View {
        ZStack {
            // 배경: 파괴 단계에 따라 색상 변화
            stageBackground
                .ignoresSafeArea()
                .animation(.easeInOut(duration: 0.4), value: viewModel.stage)

            VStack(spacing: 0) {
                // 헤더
                roomHeader

                Spacer()

                // 펭귄 인형 영역
                VStack(spacing: AppSpacing.xxl) {
                    // 대상 표시 (역할+사건 문구, 이름/등번호 없음)
                    targetLabel

                    // 인형
                    dollView
                        .offset(x: viewModel.tapShakeOffset)
                        .animation(.easeOut(duration: 0.05), value: viewModel.tapShakeOffset)

                    // 단계 표시
                    stageLabel
                }

                Spacer()

                // 게이지 + 탭 버튼
                VStack(spacing: AppSpacing.xl) {
                    gaugeBar
                    tapButton
                }
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.bottom, AppSpacing.xxxl)
            }
        }
        .navigationBarHidden(true)
        .onChange(of: viewModel.isDestroyed) { _, destroyed in
            if destroyed {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                    onDestroyed()
                }
            }
        }
    }

    // MARK: - Background

    private var stageBackground: some View {
        Group {
            switch viewModel.stage {
            case .idle:
                AppColors.gray950
            case .cracked:
                LinearGradient(
                    colors: [Color(hex: 0x1A0808), AppColors.gray950],
                    startPoint: .top,
                    endPoint: .bottom
                )
            case .burst:
                LinearGradient(
                    colors: [Color(hex: 0x2D0A0A), AppColors.gray950],
                    startPoint: .top,
                    endPoint: .bottom
                )
            case .destroyed:
                LinearGradient(
                    colors: [Color(hex: 0x3D0B0B), AppColors.gray950],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        }
    }

    // MARK: - Header

    private var roomHeader: some View {
        HStack {
            Button(action: onBack) {
                HStack(spacing: AppSpacing.xs) {
                    Image(systemName: "chevron.left")
                        .font(AppFont.bodyLgBold)
                    Text("선택")
                        .font(AppFont.bodyMedium)
                }
                .foregroundColor(.white)
            }
            .buttonStyle(.plain)

            Spacer()

            Text("분풀이 룸")
                .font(AppFont.h5Bold)
                .foregroundColor(.white)

            Spacer()

            Color.clear.frame(width: 60, height: 1)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.vertical, AppSpacing.lg)
    }

    // MARK: - Target Label

    private var targetLabel: some View {
        VStack(spacing: AppSpacing.xs) {
            Text(viewModel.selectedTarget.roleLabel)
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.red400)
                .padding(.horizontal, AppSpacing.md)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.red500.opacity(0.2))
                .clipShape(Capsule())

            Text(viewModel.selectedTarget.eventDescription)
                .font(AppFont.bodyMedium)
                .foregroundColor(AppColors.gray300)
                .multilineTextAlignment(.center)
                .padding(.horizontal, AppSpacing.xxl)
        }
    }

    // MARK: - Doll (도형 기반 플레이스홀더 - 실제 외형 없음)

    private var dollView: some View {
        ZStack {
            // 파괴 상태별 연출 색상
            let dollColor = stageDollColor

            // 인형 외형: 도형으로만 구성, 이름·등번호 없음
            VStack(spacing: -4) {
                // 머리
                ZStack {
                    Circle()
                        .fill(dollColor)
                        .frame(width: 80, height: 80)

                    // 단계별 이미지 표시
                    Text(stageEmoji)
                        .font(.system(size: 40))

                    // 균열 오버레이
                    if viewModel.stage == .cracked {
                        crackOverlay
                            .frame(width: 80, height: 80)
                    } else if viewModel.stage == .burst {
                        burstOverlay
                            .frame(width: 80, height: 80)
                    }
                }

                // 몸통
                RoundedRectangle(cornerRadius: AppRadius.sm)
                    .fill(dollColor)
                    .frame(width: 70, height: 90)
                    .overlay(
                        // 단계별 숫자 게이지 표시
                        Text("\(Int(viewModel.gauge * 100))%")
                            .font(AppFont.captionBold)
                            .foregroundColor(.white.opacity(0.8))
                    )

                // 다리
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(dollColor)
                        .frame(width: 28, height: 40)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(dollColor)
                        .frame(width: 28, height: 40)
                }
            }
            .shadow(
                color: stageShadowColor.opacity(0.5),
                radius: viewModel.stage == .destroyed ? 30 : 10,
                x: 0,
                y: 0
            )
            .scaleEffect(viewModel.stage == .destroyed ? 0.8 : 1.0)
            .opacity(viewModel.stage == .destroyed ? 0.4 : 1.0)
            .animation(.easeInOut(duration: 0.3), value: viewModel.stage)
        }
    }

    private var stageDollColor: Color {
        switch viewModel.stage {
        case .idle: return Color(hex: 0x4A90E2) // 파란 펭귄
        case .cracked: return Color(hex: 0xD4763A) // 균열 - 갈색
        case .burst: return Color(hex: 0xC0392B) // 터짐 - 빨간
        case .destroyed: return Color(hex: 0x7F1D1D) // 완파 - 어두운 빨간
        }
    }

    private var stageShadowColor: Color {
        switch viewModel.stage {
        case .idle: return Color(hex: 0x4A90E2)
        case .cracked: return Color(hex: 0xD4763A)
        case .burst: return AppColors.red500
        case .destroyed: return AppColors.red400
        }
    }

    private var stageEmoji: String {
        switch viewModel.stage {
        case .idle: return "🐧"
        case .cracked: return "😤"
        case .burst: return "😡"
        case .destroyed: return "💥"
        }
    }

    // MARK: - Crack Overlays (도형 기반)

    private var crackOverlay: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height
            var path = Path()
            path.move(to: CGPoint(x: w * 0.5, y: 0))
            path.addLine(to: CGPoint(x: w * 0.45, y: h * 0.4))
            path.addLine(to: CGPoint(x: w * 0.55, y: h * 0.5))
            path.addLine(to: CGPoint(x: w * 0.4, y: h))
            context.stroke(path, with: .color(.white.opacity(0.5)), lineWidth: 2)
        }
        .clipShape(Circle())
    }

    private var burstOverlay: some View {
        Canvas { context, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            for angle in stride(from: 0.0, to: 360.0, by: 45.0) {
                let rad = angle * .pi / 180
                let endX = center.x + cos(rad) * size.width * 0.45
                let endY = center.y + sin(rad) * size.height * 0.45
                var path = Path()
                path.move(to: center)
                path.addLine(to: CGPoint(x: endX, y: endY))
                context.stroke(path, with: .color(AppColors.red400.opacity(0.7)), lineWidth: 2)
            }
        }
        .clipShape(Circle())
    }

    // MARK: - Stage Label

    private var stageLabel: some View {
        Text(stageLabelText)
            .font(AppFont.h5Bold)
            .foregroundColor(stageLabelColor)
            .animation(.easeInOut(duration: 0.2), value: viewModel.stage)
    }

    private var stageLabelText: String {
        switch viewModel.stage {
        case .idle: return "인형을 탭해서 분풀이하세요!"
        case .cracked: return "💢 균열이 생겼습니다!"
        case .burst: return "🔥 터지기 직전입니다!"
        case .destroyed: return "💥 완파!"
        }
    }

    private var stageLabelColor: Color {
        switch viewModel.stage {
        case .idle: return AppColors.gray400
        case .cracked: return AppColors.yellow400
        case .burst: return AppColors.orange500
        case .destroyed: return AppColors.red400
        }
    }

    // MARK: - Gauge Bar

    private var gaugeBar: some View {
        VStack(spacing: AppSpacing.sm) {
            HStack {
                Text("데미지")
                    .font(AppFont.captionBold)
                    .foregroundColor(AppColors.gray400)
                Spacer()
                Text("\(Int(viewModel.gauge * 100))%")
                    .font(AppFont.captionBold)
                    .foregroundColor(gaugeColor)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    // 배경
                    RoundedRectangle(cornerRadius: AppRadius.sm)
                        .fill(AppColors.gray800)
                        .frame(height: 10)

                    // 게이지
                    RoundedRectangle(cornerRadius: AppRadius.sm)
                        .fill(
                            LinearGradient(
                                colors: [gaugeColor.opacity(0.8), gaugeColor],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geo.size.width * viewModel.gauge, height: 10)
                        .animation(.easeOut(duration: 0.1), value: viewModel.gauge)
                }
            }
            .frame(height: 10)

            // 임계값 마커
            GeometryReader { geo in
                HStack(spacing: 0) {
                    Spacer().frame(width: geo.size.width * DestructionConstants.threshold1)
                    Rectangle()
                        .fill(AppColors.gray600)
                        .frame(width: 2, height: 16)
                    Spacer().frame(width: geo.size.width * (DestructionConstants.threshold2 - DestructionConstants.threshold1) - 2)
                    Rectangle()
                        .fill(AppColors.gray600)
                        .frame(width: 2, height: 16)
                    Spacer()
                }
                .offset(y: -3)
            }
            .frame(height: 16)
        }
    }

    private var gaugeColor: Color {
        switch viewModel.stage {
        case .idle: return AppColors.blue400
        case .cracked: return AppColors.yellow400
        case .burst: return AppColors.orange500
        case .destroyed: return AppColors.red500
        }
    }

    // MARK: - Tap Button

    private var tapButton: some View {
        Button {
            viewModel.recordTap()
        } label: {
            VStack(spacing: AppSpacing.xs) {
                Text("💢")
                    .font(.system(size: 32))
                Text("탭!")
                    .font(AppFont.bodyLgBold)
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.xl)
            .background(
                Group {
                    if viewModel.isDestroyed {
                        AppColors.gray800
                    } else {
                        LinearGradient(
                            colors: [AppColors.red500, AppColors.red500.opacity(0.8)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    }
                }
            )
            .cornerRadius(AppRadius.lg)
        }
        .disabled(viewModel.isDestroyed)
        .buttonStyle(.plain)
    }
}
#endif
#endif
