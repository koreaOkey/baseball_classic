import CoreMotion
import Foundation

// MARK: - VentingShakeDetector

/// 분풀이 룸 흔들기 감지기 (Android VentingShakeDetector 포팅).
///
/// 중력 제거 가속도(userAcceleration) 크기가 임계값을 넘는 순간을 "흔들기 버스트"
/// 1회로 보고, 세기에 비례한 타격 수(1~3)를 콜백으로 전달한다 — 세게 흔들수록 데미지가 크다.
/// 룸 화면이 보이는 동안만 start/stop 으로 센서를 점유한다.
final class VentingShakeDetector {

    /// 흔들기 인정 최소 가속도 (g). Android 12 m/s² ≈ 1.22g 와 동일 수준.
    private static let shakeThresholdG = 1.2
    /// 버스트 간 최소 간격 — 한 번의 왕복 흔들기가 다중 인식되는 것 방지.
    private static let burstDebounceSeconds = 0.22

    private let motionManager = CMMotionManager()
    private let onShakeBurst: (Int) -> Void
    private var lastBurstTime: TimeInterval = 0

    init(onShakeBurst: @escaping (Int) -> Void) {
        self.onShakeBurst = onShakeBurst
    }

    func start() {
        guard motionManager.isDeviceMotionAvailable else { return }
        motionManager.deviceMotionUpdateInterval = 1.0 / 50.0
        motionManager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
            guard let self, let motion else { return }
            let a = motion.userAcceleration
            let magnitude = (a.x * a.x + a.y * a.y + a.z * a.z).squareRoot()
            guard magnitude >= Self.shakeThresholdG else { return }

            let now = Date().timeIntervalSince1970
            guard now - self.lastBurstTime >= Self.burstDebounceSeconds else { return }
            self.lastBurstTime = now

            // 세기 → 타격 수: 임계값 초과분 0.8g 당 +1, 최대 3타 (Android 동일 밸런스)
            let hits = min(3, 1 + Int((magnitude - Self.shakeThresholdG) / 0.8))
            self.onShakeBurst(hits)
        }
    }

    func stop() {
        motionManager.stopDeviceMotionUpdates()
    }
}
