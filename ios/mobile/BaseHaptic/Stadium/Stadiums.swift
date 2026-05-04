import Foundation
import CoreLocation

// TODO(stadium-cheer): 활성화 시 시즌 시작 전 좌표·반경 검증. 원격 stadiums.json 갱신은 Phase 1.5.
struct Stadium: Identifiable, Codable, Hashable {
    let code: String
    let name: String
    let homeTeam: String
    let latitude: Double
    let longitude: Double
    let radiusMeters: Double
    let indoor: Bool

    var id: String { code }
    var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

enum StadiumDirectory {
    /// KBO 9개 정규 구장. 좌표는 외야 중심 부근 근사값.
    static let all: [Stadium] = [
        Stadium(code: "JAMSIL", name: "잠실야구장", homeTeam: "DOOSAN", latitude: 37.5121, longitude: 127.0719, radiusMeters: 350, indoor: false),
        Stadium(code: "GOCHEOK", name: "고척스카이돔", homeTeam: "KIWOOM", latitude: 37.4982, longitude: 126.8670, radiusMeters: 350, indoor: true),
        Stadium(code: "INCHEON", name: "인천SSG랜더스필드", homeTeam: "SSG", latitude: 37.4370, longitude: 126.6932, radiusMeters: 350, indoor: false),
        Stadium(code: "SUWON", name: "수원KT위즈파크", homeTeam: "KT", latitude: 37.2997, longitude: 127.0097, radiusMeters: 350, indoor: false),
        Stadium(code: "DAEJEON", name: "대전한화생명이글스파크", homeTeam: "HANWHA", latitude: 36.3170, longitude: 127.4291, radiusMeters: 350, indoor: false),
        Stadium(code: "DAEGU", name: "대구삼성라이온즈파크", homeTeam: "SAMSUNG", latitude: 35.8411, longitude: 128.6817, radiusMeters: 350, indoor: false),
        Stadium(code: "SAJIK", name: "사직야구장", homeTeam: "LOTTE", latitude: 35.1939, longitude: 129.0617, radiusMeters: 350, indoor: false),
        Stadium(code: "GWANGJU", name: "광주기아챔피언스필드", homeTeam: "KIA", latitude: 35.1681, longitude: 126.8889, radiusMeters: 350, indoor: false),
        Stadium(code: "CHANGWON", name: "창원NC파크", homeTeam: "NC", latitude: 35.2225, longitude: 128.5822, radiusMeters: 350, indoor: false),
    ]

    static func byCode(_ code: String) -> Stadium? {
        all.first { $0.code == code }
    }
}
