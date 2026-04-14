import SwiftUI

struct WatchNoGameScreen: View {
    var body: some View {
        VStack(spacing: WatchAppSpacing.sm) {
            // Reason: 워치 고정 심볼 크기 (프로파일과 무관한 플레이스홀더)
            Text("⚾")
                .font(.system(size: 32))

            // Reason: 워치 본문 고정 사이즈 — spec의 "11/12/13pt 작은 고정 사이즈" 규정 준수
            Text("진행 중인 경기가 없습니다")
                .font(.system(size: 13))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)

            // Reason: 워치 캡션 고정 사이즈
            Text("모바일 앱에서 경기를 선택해주세요")
                .font(.system(size: 11))
                .foregroundColor(WatchColors.gray400)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, WatchAppSpacing.lg)
        .background(WatchColors.gray950)
    }
}

#Preview {
    WatchNoGameScreen()
}
