import Foundation
import CoreLocation

// TODO(stadium-cheer): 활성화 시점에 다음 작업 일괄 진행:
//   1. Info.plist에 NSLocationAlwaysAndWhenInUseUsageDescription 추가
//   2. AppDelegate 또는 적절한 lifecycle hook에서 StadiumRegionMonitor.shared.start() 호출
//   3. didEnterRegion 콜백에서 UNUserNotificationCenter로 로컬 알림 발송
// 다크 머지 단계에서는 클래스 정의만 두고 startMonitoring/locationManager 등록은 하지 않음.
final class StadiumRegionMonitor: NSObject, CLLocationManagerDelegate {
    static let shared = StadiumRegionMonitor()

    private let locationManager = CLLocationManager()
    private var didStart = false

    /// 진입 콜백. 활성화 시 외부에서 주입(로컬 알림 발송 등).
    var onEnterStadium: ((Stadium) -> Void)?

    private override init() {
        super.init()
    }

    /// TODO(stadium-cheer): 활성화 시 호출. 다크 상태에서는 호출되지 않음.
    func start() {
        guard !didStart else { return }
        didStart = true
        locationManager.delegate = self
        // locationManager.requestAlwaysAuthorization()
        // for stadium in StadiumDirectory.all {
        //     let region = CLCircularRegion(
        //         center: stadium.coordinate,
        //         radius: stadium.radiusMeters,
        //         identifier: stadium.code
        //     )
        //     region.notifyOnEntry = true
        //     region.notifyOnExit = false
        //     locationManager.startMonitoring(for: region)
        // }
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard let stadium = StadiumDirectory.byCode(region.identifier) else { return }
        onEnterStadium?(stadium)
    }
}
