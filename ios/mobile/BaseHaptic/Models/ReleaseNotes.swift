import Foundation

struct ReleaseNote: Identifiable {
    var id: String { version }
    let version: String
    let subtitle: String
    let bullets: [String]
}

enum ReleaseNotes {
    // 새 버전 출시 시 entry 1개를 추가한다.
    // version 은 Info.plist `CFBundleShortVersionString` 과 정확히 일치해야 한다.
    // 일치하는 entry 가 없으면 모달이 노출되지 않는다.
    static let all: [ReleaseNote] = [
        ReleaseNote(
            version: "1.1.3",
            subtitle: "더 좋아진 야구봄",
            bullets: [
                "워치 앱 연동 가이드 추가",
                "워치로 투구수 확인 기능 추가",
                "경기 시작 시 푸쉬 알림 추가",
            ]
        ),
    ]

    static func notes(for version: String) -> ReleaseNote? {
        all.first { $0.version == version }
    }
}
