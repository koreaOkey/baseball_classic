#if DEBUG
import SwiftUI

// MARK: - VentingRoomScreen

/// 분풀이 룸 화면.
///
/// - 펭귄 인형 스프라이트 + 데미지 게이지 + 하단 도구 트레이를 표시한다.
/// - 인형에 선수 이름·등번호·실제 외형 표기 없음.
/// - 하단 트레이에서 도구(맨손/뿅망치/야구방망이/슬리퍼/프라이팬)를 선택하고,
///   **인형을 직접 탭**하면 선택한 도구가 인형을 내려치는 연출 + 히트 이펙트가 재생된다.
///   (캐릭터가 스윙하는 모션 없음 — 도구 오브젝트만 등장)
/// - 탭 경햅틱·단계 전환 중햅틱·완파 성공 햅틱 — VentingRoomViewModel이 구동.
#if DEBUG
struct VentingRoomScreen: View {

    @ObservedObject var viewModel: VentingRoomViewModel
    let onBack: () -> Void
    let onDestroyed: () -> Void

    // 도구 선택 (기본: 뿅망치)
    @State private var selectedTool: VentingTool = .hammer

    // 타격 연출 상태 (아크 내려찍기: 와인드업 → 원호 이동 스윙 → 히트스톱 임팩트)
    @State private var strikeVisible = false
    @State private var strikeAngle: Double = StrikeMotion.windupAngle
    @State private var strikeOffset: CGSize = StrikeMotion.windupOffset
    @State private var showHitEffect = false
    @State private var dollSquash: CGFloat = 1.0
    @State private var dollPushDown: CGFloat = 0

    /// 아크 내려찍기 모션 상수
    private enum StrikeMotion {
        // 와인드업: 인형 우상단 높은 위치, 뒤로 젖힘
        static let windupAngle: Double = -75
        static let windupOffset = CGSize(width: 155, height: -140)
        // 임팩트: 인형 머리 위까지 이동, 앞으로 꽂힘
        static let impactAngle: Double = 20
        static let impactOffset = CGSize(width: 18, height: -52)
        // 타이밍
        static let swingDuration: Double = 0.09   // 가속 스윙
        static let hitStopDuration: Double = 0.07 // 접촉 순간 정지
        static let recoverDuration: Double = 0.12 // 복원·퇴장
    }

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

                    // 인형 (직접 탭 = 타격)
                    dollView
                        .offset(x: viewModel.tapShakeOffset)
                        .animation(.easeOut(duration: 0.05), value: viewModel.tapShakeOffset)

                    // 단계 표시
                    stageLabel
                }

                Spacer()

                // 게이지 + 도구 트레이
                VStack(spacing: AppSpacing.xl) {
                    gaugeBar
                    toolTray
                }
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.bottom, AppSpacing.xxxl)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            // 캡처용: 특정 도구 선택 (--venting-tool=frypan 등)
            if let arg = CommandLine.arguments.first(where: { $0.hasPrefix("--venting-tool=") }),
               let tool = VentingTool(rawValue: String(arg.dropFirst("--venting-tool=".count))) {
                selectedTool = tool
            }
            // 캡처용: 타격 임팩트 프레임 고정 (시뮬레이터 스크린샷 검증)
            if CommandLine.arguments.contains("--venting-strike-freeze") {
                strikeVisible = true
                strikeAngle = StrikeMotion.impactAngle
                strikeOffset = StrikeMotion.impactOffset
                showHitEffect = true
                dollSquash = 0.85
                dollPushDown = 8
            }
            // 캡처용: 와인드업 프레임 고정
            if CommandLine.arguments.contains("--venting-windup-freeze") {
                strikeVisible = true
                strikeAngle = StrikeMotion.windupAngle
                strikeOffset = StrikeMotion.windupOffset
            }
        }
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

    // MARK: - Doll

    private var dollView: some View {
        ZStack {
            // 익명 팀 유니폼 펭귄 인형: 단계별 스프라이트 (이름·등번호·실제 외형 없음)
            Image(viewModel.stage.dollImageName)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 220, height: 220)
                // 임팩트: 세로로 눌리고 가로로 살짝 퍼지는 만화식 스쿼시 (바닥 기준)
                .scaleEffect(x: 1.0 + (1.0 - dollSquash) * 0.6, y: dollSquash, anchor: .bottom)
                .offset(y: dollPushDown)
                .overlay(alignment: .center) {
                    // 단계별 데미지 퍼센트 표시
                    if viewModel.stage != .destroyed {
                        Text("\(Int(viewModel.gauge * 100))%")
                            .font(AppFont.h5Bold)
                            .foregroundColor(.white)
                            .shadow(color: .black.opacity(0.6), radius: 3)
                            .offset(y: 40)
                    }
                }
                .shadow(
                    color: stageShadowColor.opacity(0.5),
                    radius: viewModel.stage == .destroyed ? 30 : 12,
                    x: 0,
                    y: 0
                )
                .scaleEffect(viewModel.stage == .destroyed ? 0.85 : 1.0)
                .opacity(viewModel.stage == .destroyed ? 0.5 : 1.0)
                .animation(.easeInOut(duration: 0.3), value: viewModel.stage)

            // 히트 이펙트 (별·충격파, 타격 순간에만) — 도구 임팩트 접점에 표시
            if showHitEffect {
                Image(VentingTool.hitEffectImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 130, height: 130)
                    .offset(x: 30, y: -65)
                    .transition(.scale(scale: 0.5).combined(with: .opacity))
                    .allowsHitTesting(false)
            }
        }
        // 선택한 도구가 인형을 내려치는 연출 (도구 오브젝트만, 캐릭터 없음)
        .overlay {
            strikeToolView
        }
        .contentShape(Rectangle())
        .onTapGesture {
            strike()
        }
    }

    /// 타격 시 잠깐 나타나는 도구 오브젝트.
    /// 스프라이트별 기본 회전·반전으로 타격면이 인형을 향하게 한 뒤,
    /// 와인드업 위치에서 원호 궤적(회전+이동)으로 인형까지 내려찍는다.
    private var strikeToolView: some View {
        Image(selectedTool.imageName)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 120, height: 120)
            .scaleEffect(x: selectedTool.strikeFlipsHorizontally ? -1 : 1, y: 1)
            .rotationEffect(.degrees(selectedTool.strikeBaseRotation))
            .rotationEffect(.degrees(strikeAngle), anchor: .bottomLeading)
            .offset(strikeOffset)
            .opacity(strikeVisible ? 1 : 0)
            .allowsHitTesting(false)
    }

    // MARK: - Strike

    /// 인형 탭 1회 = 타격 1회 (아크 내려찍기).
    ///
    /// 게이지·햅틱은 viewModel.recordTap()이 처리하고, 여기서는 연출만 담당:
    /// 1. 와인드업 포즈로 등장 (우상단 높은 위치, 뒤로 젖힘)
    /// 2. 가속(easeIn)하며 회전+이동을 동시에 → 원호 궤적으로 인형 머리까지
    /// 3. 임팩트: 히트스톱(도구 정지) + 히트 이펙트 + 인형 세로 스쿼시·눌림
    /// 4. 복원: 인형 원상복구, 도구 퇴장
    private func strike() {
        guard !viewModel.isDestroyed else { return }
        viewModel.recordTap()

        // 1) 와인드업 포즈로 즉시 리셋 (연타 시에도 매번 처음부터 스윙)
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t) {
            strikeVisible = true
            strikeAngle = StrikeMotion.windupAngle
            strikeOffset = StrikeMotion.windupOffset
            dollSquash = 1.0
            dollPushDown = 0
        }
        showHitEffect = false

        // 2) 스윙: 가속하며 원호 궤적으로 임팩트 지점까지
        withAnimation(.easeIn(duration: StrikeMotion.swingDuration)) {
            strikeAngle = StrikeMotion.impactAngle
            strikeOffset = StrikeMotion.impactOffset
        }

        // 3) 임팩트: 도구는 히트스톱으로 정지, 인형은 눌리고 이펙트 발동
        DispatchQueue.main.asyncAfter(deadline: .now() + StrikeMotion.swingDuration) {
            withAnimation(.easeOut(duration: 0.05)) {
                showHitEffect = true
                dollSquash = 0.85
                dollPushDown = 8
            }
        }

        // 4) 히트스톱 종료 후 복원·퇴장
        let recoverAt = StrikeMotion.swingDuration + StrikeMotion.hitStopDuration
        DispatchQueue.main.asyncAfter(deadline: .now() + recoverAt) {
            withAnimation(.spring(response: 0.18, dampingFraction: 0.55)) {
                dollSquash = 1.0
                dollPushDown = 0
            }
            withAnimation(.easeOut(duration: StrikeMotion.recoverDuration)) {
                strikeVisible = false
            }
            showHitEffect = false
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

    // MARK: - Stage Label

    private var stageLabel: some View {
        Text(stageLabelText)
            .font(AppFont.h5Bold)
            .foregroundColor(stageLabelColor)
            .animation(.easeInOut(duration: 0.2), value: viewModel.stage)
    }

    private var stageLabelText: String {
        switch viewModel.stage {
        case .idle: return "도구를 골라 인형을 때리세요!"
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

    // MARK: - Tool Tray

    /// 하단 가로 도구 트레이 (A안).
    /// 선택된 도구는 레드 하이라이트, 비선택 도구는 그레이 톤.
    private var toolTray: some View {
        HStack(spacing: AppSpacing.sm) {
            ForEach(VentingTool.allCases) { tool in
                toolTrayItem(tool)
            }
        }
        .padding(AppSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: AppRadius.lg)
                .fill(AppColors.gray900)
        )
    }

    private func toolTrayItem(_ tool: VentingTool) -> some View {
        let isSelected = tool == selectedTool
        return Button {
            selectedTool = tool
        } label: {
            VStack(spacing: AppSpacing.xs) {
                Image(tool.imageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 42, height: 42)
                    .saturation(isSelected ? 1 : 0)
                    .opacity(isSelected ? 1 : 0.55)

                Text(tool.label)
                    .font(AppFont.captionBold)
                    .foregroundColor(isSelected ? .white : AppColors.gray500)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, AppSpacing.md)
            .background(
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .fill(isSelected ? AppColors.red500.opacity(0.22) : AppColors.gray800)
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppRadius.md)
                    .stroke(isSelected ? AppColors.red500 : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isDestroyed)
    }
}
#endif
#endif
