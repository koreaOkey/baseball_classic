import SwiftUI

// MARK: - VentingHomeCard

/// 분풀이 모드 홈카드: "오늘의 아쉬운 순간"
///
/// - 오픈 조건(피처 플래그 + 마이팀 패배 당일)이 충족된 경우에만 렌더링된다.
/// - 컨텍스트는 실제 경기 데이터(서버 regret-top5, 폴백: 로컬 규칙)로 조립된다.
struct VentingHomeCard: View {

    let context: VentingGameContext
    let onEnterVenting: (VentingGameContext) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            // 헤더
            HStack(spacing: AppSpacing.sm) {
                Text("💢")
                    .font(.system(size: 20))
                VStack(alignment: .leading, spacing: AppSpacing.xxs) {
                    Text("오늘의 아쉬운 순간")
                        .font(AppFont.captionBold)
                        .foregroundColor(AppColors.red400)
                    Text("빠따존 한번 가볼까요?")
                        .font(AppFont.micro)
                        .foregroundColor(AppColors.gray400)
                }
                Spacer()
                // 패배 스코어 배지
                Text("\(context.myScore) : \(context.opponentScore)")
                    .font(AppFont.h5Bold)
                    .foregroundColor(AppColors.red400)
                    .padding(.horizontal, AppSpacing.md)
                    .padding(.vertical, AppSpacing.xs)
                    .background(AppColors.red500.opacity(0.15))
                    .clipShape(Capsule())
            }

            // 아쉬운 순간 미리보기 (최대 2개)
            VStack(alignment: .leading, spacing: AppSpacing.xs) {
                ForEach(context.candidates.prefix(2)) { candidate in
                    HStack(spacing: AppSpacing.sm) {
                        Circle()
                            .fill(AppColors.red500.opacity(0.4))
                            .frame(width: 6, height: 6)
                        Text("\(candidate.roleLabel) · \(candidate.eventDescription)")
                            .font(AppFont.micro)
                            .foregroundColor(AppColors.gray300)
                            .lineLimit(1)
                    }
                }
                if context.candidates.count > 2 {
                    Text("외 \(context.candidates.count - 2)건 더...")
                        .font(AppFont.micro)
                        .foregroundColor(AppColors.gray500)
                }
            }

            // 진입 버튼
            Button {
                onEnterVenting(context)
            } label: {
                HStack(spacing: AppSpacing.sm) {
                    Text("빠따존 가기")
                        .font(AppFont.bodyMedium)
                        .foregroundColor(.white)
                    Image(systemName: "chevron.right")
                        .font(AppFont.captionBold)
                        .foregroundColor(.white.opacity(0.7))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, AppSpacing.md)
                .background(AppColors.red500)
                .cornerRadius(AppRadius.md)
            }
            .buttonStyle(.plain)
        }
        .padding(AppSpacing.lg)
        .background(
            LinearGradient(
                colors: [AppColors.red500.opacity(0.18), AppColors.red500.opacity(0.08)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(AppRadius.lg)
        .overlay(
            RoundedRectangle(cornerRadius: AppRadius.lg)
                .stroke(AppColors.red500.opacity(0.35), lineWidth: 1)
        )
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.vertical, 6)
    }
}
