import WatchKit

/// 워치 화면 크기별 UI 프로파일
/// - small:  ≤176pt (41mm 이하)
/// - medium: ≤198pt (45mm)
/// - large:  >198pt (49mm Ultra)
struct WatchUiProfile {

    // MARK: - Typography
    let scoreValueSize: CGFloat
    let teamNameSize: CGFloat
    let inningSize: CGFloat
    let inningHalfSize: CGFloat
    let playerInfoSize: CGFloat
    let countLabelSize: CGFloat
    let countLabelWidth: CGFloat

    // MARK: - Layout
    let topPadding: CGFloat
    let horizontalPadding: CGFloat
    let bsoScoreSpacing: CGFloat
    let bsoPlayerSpacing: CGFloat

    // MARK: - Widgets
    let baseDiamondFrame: CGFloat
    let baseCellSize: CGFloat
    let countDotSize: CGFloat

    // MARK: - Factory

    static var current: WatchUiProfile {
        let width = WKInterfaceDevice.current().screenBounds.width
        if width <= 176 { return .small }
        if width <= 198 { return .medium }
        return .large
    }

    static let small = WatchUiProfile(
        scoreValueSize: 24,
        teamNameSize: 8,
        inningSize: 9,
        inningHalfSize: 7,
        playerInfoSize: 9,
        countLabelSize: 8,
        countLabelWidth: 11,
        topPadding: 2,
        horizontalPadding: 6,
        bsoScoreSpacing: 6,
        bsoPlayerSpacing: 4,
        baseDiamondFrame: 26,
        baseCellSize: 9,
        countDotSize: 6
    )

    static let medium = WatchUiProfile(
        scoreValueSize: 28,
        teamNameSize: 9,
        inningSize: 10,
        inningHalfSize: 8,
        playerInfoSize: 10,
        countLabelSize: 9,
        countLabelWidth: 12,
        topPadding: 4,
        horizontalPadding: 8,
        bsoScoreSpacing: 8,
        bsoPlayerSpacing: 6,
        baseDiamondFrame: 30,
        baseCellSize: 10,
        countDotSize: 7
    )

    static let large = WatchUiProfile(
        scoreValueSize: 32,
        teamNameSize: 10,
        inningSize: 11,
        inningHalfSize: 9,
        playerInfoSize: 11,
        countLabelSize: 10,
        countLabelWidth: 13,
        topPadding: 6,
        horizontalPadding: 10,
        bsoScoreSpacing: 10,
        bsoPlayerSpacing: 8,
        baseDiamondFrame: 34,
        baseCellSize: 12,
        countDotSize: 8
    )
}
