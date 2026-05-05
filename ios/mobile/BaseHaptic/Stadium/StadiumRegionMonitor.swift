import Foundation
import CoreLocation

final class StadiumRegionMonitor: NSObject, CLLocationManagerDelegate {
    static let shared = StadiumRegionMonitor()

    private let locationManager = CLLocationManager()
    private var didStart = false

    /// 진입 콜백. 활성화 시 외부에서 주입(로컬 알림 발송 등).
    var onEnterStadium: ((Stadium) -> Void)?

    private override init() {
        super.init()
    }

    func start() {
        guard !didStart else { return }
        didStart = true
        locationManager.delegate = self
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestAlwaysAuthorization()
        for stadium in StadiumDirectory.all {
            let region = CLCircularRegion(
                center: stadium.coordinate,
                radius: stadium.radiusMeters,
                identifier: stadium.code
            )
            region.notifyOnEntry = true
            region.notifyOnExit = false
            locationManager.startMonitoring(for: region)
        }
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard let stadium = StadiumDirectory.byCode(region.identifier) else { return }
        onEnterStadium?(stadium)
    }
}
