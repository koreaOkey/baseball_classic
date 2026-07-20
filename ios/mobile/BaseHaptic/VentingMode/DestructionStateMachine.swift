import Foundation

// MARK: - DestructionStateMachine

/// 분풀이 룸 파괴 상태머신.
///
/// 탭 1회 = 게이지 `DestructionConstants.tapIncrement` 증가.
/// 게이지 임계값(0.33 / 0.66 / 1.0)에서 단계가 전환되며,
/// 완파(gauge = 1.0) 시 경기당 첫 완파 여부를 UserDefaults에 기록한다.
///
/// - 게이지는 0…1 사이에서만 움직이며 1.0을 초과하지 않는다.
/// - reset() 후에도 UserDefaults 기록은 유지된다(Phase 2 광고 게이트 재사용).
/// - SwiftUI 바인딩이 필요하면 ObservableObject 래퍼(VentingRoomViewModel)를 사용한다.
final class DestructionStateMachine {

    // MARK: - State

    /// 현재 게이지 값 (0.0 ~ 1.0).
    /// 내부적으로 탭 횟수를 기준으로 계산하여 부동소수점 누적 오차를 방지한다.
    private(set) var gauge: Double = 0.0

    /// 현재 파괴 단계.
    private(set) var stage: DestructionStage = .idle

    // MARK: - UserDefaults

    private let userDefaults: UserDefaults
    private let gameID: String

    /// "venting_first_destruction_<gameID>" 키로 첫 완파 여부를 저장한다.
    private var firstDestructionKey: String {
        "venting_first_destruction_\(gameID)"
    }

    // MARK: - Init

    /// - Parameters:
    ///   - gameID: 경기 식별자 (UserDefaults 키 접두사에 사용).
    ///   - userDefaults: 주입 가능한 UserDefaults 인스턴스 (기본값: .standard).
    init(gameID: String, userDefaults: UserDefaults = .standard) {
        self.gameID = gameID
        self.userDefaults = userDefaults
    }

    // MARK: - Tap

    /// 탭 1회를 처리한다.
    ///
    /// - Returns: 단계가 전환된 경우 새 단계, 아닌 경우 `nil`.
    ///            완파 상태에서 탭하면 즉시 `nil` 반환.
    @discardableResult
    func tap() -> DestructionStage? {
        guard stage != .destroyed else { return nil }

        let oldStage = stage
        gauge = min(gauge + DestructionConstants.tapIncrement, DestructionConstants.complete)
        let newStage = DestructionStage(gauge: gauge)
        stage = newStage

        guard newStage != oldStage else { return nil }

        if newStage == .destroyed {
            recordFirstDestructionIfNeeded()
        }
        return newStage
    }

    // MARK: - Reset

    /// 게이지와 단계를 초기 상태(0, idle)로 되돌린다.
    /// UserDefaults 기록은 초기화하지 않는다.
    func reset() {
        gauge = 0.0
        stage = .idle
    }

    // MARK: - First Destruction Record

    /// 해당 경기에서 첫 완파가 이미 기록됐는지 반환한다.
    var hasRecordedFirstDestruction: Bool {
        userDefaults.bool(forKey: firstDestructionKey)
    }

    private func recordFirstDestructionIfNeeded() {
        guard !userDefaults.bool(forKey: firstDestructionKey) else { return }
        userDefaults.set(true, forKey: firstDestructionKey)
    }
}
