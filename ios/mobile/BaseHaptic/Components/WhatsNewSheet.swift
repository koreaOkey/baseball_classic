import SwiftUI

struct WhatsNewSheet: View {
    let note: ReleaseNote
    let onConfirm: () -> Void

    @Environment(\.teamTheme) private var teamTheme
    @State private var pageIndex = 0

    /// 마지막 인덱스 = 불릿 페이지. featurePages 가 비어 있으면 슬라이드 없이 기존 단일 레이아웃.
    private var pageCount: Int { note.featurePages.count + 1 }
    private var isSlideMode: Bool { !note.featurePages.isEmpty }

    var body: some View {
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture(perform: onConfirm)

            if isSlideMode {
                slideCard
            } else {
                classicCard
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - 슬라이드형 (대표 기능 페이지 + 마지막 불릿 페이지)

    private var slideCard: some View {
        VStack(spacing: 0) {
            slideHeader
                .padding(.horizontal, AppSpacing.xxl)
                .padding(.top, AppSpacing.xxl)
                .padding(.bottom, AppSpacing.md)

            TabView(selection: $pageIndex) {
                ForEach(Array(note.featurePages.enumerated()), id: \.offset) { index, page in
                    featurePage(page).tag(index)
                }
                bulletsPage.tag(note.featurePages.count)
            }
            .tabViewStyle(.page(indexDisplayMode: .never))

            pageDots
                .padding(.vertical, AppSpacing.md)

            slideButton
        }
        .frame(height: min(600, UIScreen.main.bounds.height * 0.72))
        .background(AppColors.gray950)
        .cornerRadius(AppRadius.lg)
        .padding(.horizontal, AppSpacing.xxl)
    }

    private var slideHeader: some View {
        HStack(spacing: AppSpacing.sm) {
            Text("NEW v\(note.version)")
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.gray950)
                .padding(.horizontal, AppSpacing.sm)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.yellow400)
                .cornerRadius(AppRadius.sm)

            Text("업데이트 안내")
                .font(AppFont.h4Bold)
                .foregroundColor(.white)

            Spacer()
        }
    }

    private func featurePage(_ page: WhatsNewFeaturePage) -> some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            featureVisual(page.visual)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: AppRadius.lg))

            VStack(alignment: .leading, spacing: AppSpacing.sm) {
                Text(page.title)
                    .font(AppFont.h4Bold)
                    .foregroundColor(.white)
                Text(page.body)
                    .font(AppFont.body)
                    .foregroundColor(AppColors.gray400)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // 페이지 간 텍스트 양 차이로 비주얼 높이가 출렁이지 않게 고정
            .frame(height: 88, alignment: .topLeading)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.top, AppSpacing.xs)
    }

    @ViewBuilder
    private func featureVisual(_ visual: WhatsNewFeaturePage.Visual) -> some View {
        switch visual {
        case .image(let name):
            GeometryReader { geo in
                Image(name)
                    .resizable()
                    .scaledToFill()
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()
            }
        case .lockScreen(let name):
            lockScreenFrame(imageName: name)
        }
    }

    /// 시계·날짜를 그린 잠금 화면 프레임 안에 노티 카드 스크린샷을 얹는다.
    private func lockScreenFrame(imageName: String) -> some View {
        ZStack(alignment: .top) {
            LinearGradient(
                colors: [Color(red: 0.17, green: 0.15, blue: 0.20), Color(red: 0.06, green: 0.06, blue: 0.09)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            VStack(spacing: AppSpacing.xs) {
                Text(Self.lockScreenDateText)
                    .font(AppFont.captionSemibold)
                    .foregroundColor(.white.opacity(0.75))
                Text("9:41")
                    .font(.system(size: 48, weight: .thin))
                    .foregroundColor(.white.opacity(0.92))
                    .padding(.bottom, AppSpacing.md)

                Image(imageName)
                    .resizable()
                    .scaledToFit()
                    .clipShape(RoundedRectangle(cornerRadius: AppRadius.md))
                    .shadow(color: .black.opacity(0.5), radius: 12, y: 6)
                    .padding(.horizontal, AppSpacing.lg)
            }
            .padding(.top, AppSpacing.xl)
        }
    }

    private static var lockScreenDateText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "M월 d일 EEEE"
        return formatter.string(from: Date())
    }

    private var bulletsPage: some View {
        VStack(alignment: .leading, spacing: AppSpacing.lg) {
            Text("이런 것도 좋아졌어요")
                .font(AppFont.h4Bold)
                .foregroundColor(.white)

            bulletList

            Spacer(minLength: 0)

            Text("그 외 자잘한 버그 수정과 안정성 개선이 포함되어 있어요.")
                .font(AppFont.caption)
                .foregroundColor(AppColors.gray400)
        }
        .padding(.horizontal, AppSpacing.xxl)
        .padding(.top, AppSpacing.xs)
    }

    private var pageDots: some View {
        HStack(spacing: AppSpacing.sm) {
            ForEach(0..<pageCount, id: \.self) { index in
                Capsule()
                    .fill(index == pageIndex ? teamTheme.controlAccent : AppColors.gray800)
                    .frame(width: index == pageIndex ? 20 : 7, height: 7)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: pageIndex)
    }

    private var slideButton: some View {
        Button {
            if pageIndex >= pageCount - 1 {
                onConfirm()
            } else {
                withAnimation { pageIndex += 1 }
            }
        } label: {
            Text(pageIndex >= pageCount - 1 ? "확인" : "다음")
                .font(AppFont.bodyLgBold)
                .foregroundColor(teamTheme.controlAccent)
                .frame(maxWidth: .infinity)
                .frame(height: AppSpacing.buttonHeight)
        }
        .background(AppColors.gray900)
        .overlay(
            Rectangle()
                .fill(AppColors.gray800)
                .frame(height: 1),
            alignment: .top
        )
    }

    // MARK: - 기존 단일 불릿 레이아웃 (featurePages 없는 버전)

    private var classicCard: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: AppSpacing.xl) {
                header
                bulletList
            }
            .padding(.horizontal, AppSpacing.xxl)
            .padding(.top, AppSpacing.xxl)
            .padding(.bottom, AppSpacing.xl)

            confirmButton
        }
        .background(AppColors.gray950)
        .cornerRadius(AppRadius.lg)
        .padding(.horizontal, AppSpacing.xxl)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            Text("NEW v\(note.version)")
                .font(AppFont.captionBold)
                .foregroundColor(AppColors.gray950)
                .padding(.horizontal, AppSpacing.sm)
                .padding(.vertical, AppSpacing.xs)
                .background(AppColors.yellow400)
                .cornerRadius(AppRadius.sm)

            Text("업데이트 안내")
                .font(AppFont.h2)
                .foregroundColor(.white)

            Text(note.subtitle)
                .font(AppFont.body)
                .foregroundColor(AppColors.gray400)
        }
    }

    private var bulletList: some View {
        VStack(alignment: .leading, spacing: AppSpacing.md) {
            ForEach(note.bullets, id: \.self) { bullet in
                HStack(alignment: .top, spacing: AppSpacing.sm) {
                    ZStack {
                        Circle()
                            .fill(teamTheme.controlAccent)
                            .frame(width: 20, height: 20)
                        Image(systemName: "checkmark")
                            .font(AppFont.tinyBold)
                            .foregroundColor(.white)
                    }
                    Text(bullet)
                        .font(AppFont.bodyLgMedium)
                        .foregroundColor(AppColors.gray100)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var confirmButton: some View {
        Button(action: onConfirm) {
            Text("확인")
                .font(AppFont.bodyLgBold)
                .foregroundColor(teamTheme.controlAccent)
                .frame(maxWidth: .infinity)
                .frame(height: AppSpacing.buttonHeight)
        }
        .background(AppColors.gray900)
        .overlay(
            Rectangle()
                .fill(AppColors.gray800)
                .frame(height: 1),
            alignment: .top
        )
    }
}
