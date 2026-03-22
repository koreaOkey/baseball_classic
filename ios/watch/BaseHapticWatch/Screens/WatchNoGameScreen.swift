import SwiftUI

struct WatchNoGameScreen: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("⚾")
                .font(.system(size: 32))

            Text("진행 중인 경기가 없습니다")
                .font(.system(size: 13))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)

            Text("모바일 앱에서 경기를 선택해주세요")
                .font(.system(size: 11))
                .foregroundColor(WatchColors.gray400)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(.horizontal, 16)
        .background(WatchColors.gray950)
    }
}

#Preview {
    WatchNoGameScreen()
}
