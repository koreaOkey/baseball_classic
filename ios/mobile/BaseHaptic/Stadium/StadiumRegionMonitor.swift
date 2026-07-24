import Foundation
import CoreLocation

final class StadiumRegionMonitor: NSObject, CLLocationManagerDelegate {
    static let shared = StadiumRegionMonitor()

    private let locationManager = CLLocationManager()
    private var didStart = false

    /// 진입 콜백. 활성화 시 외부에서 주입(로컬 알림 발송 등).
    var onEnterStadium: ((Stadium) -> Void)?
    var onLocationUpdate: ((CLLocation) -> Void)?

    private override init() {
        super.init()
    }

    func start() {
        guard !didStart else { return }
        didStart = true
        locationManager.delegate = self
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.requestAlwaysAuthorization()
        locationManager.startUpdatingLocation()
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

    /// 기능 비활성 시 잔존 지오펜스 정리. 이전 버전에서 등록한 region 은
    /// CoreLocation 에 앱 재실행 후에도 보존되므로 명시적으로 제거해야 한다.
    func stopAndClear() {
        for region in locationManager.monitoredRegions {
            locationManager.stopMonitoring(for: region)
        }
        locationManager.stopUpdatingLocation()
        didStart = false
    }

    func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard let stadium = StadiumDirectory.byCode(region.identifier) else { return }
        onEnterStadium?(stadium)
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        onLocationUpdate?(location)
    }
}
