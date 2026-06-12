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
            subtitle: "경기 확인과 워치 연결이 더 쉬워졌어요",
            bullets: [
                "잠금화면 토글: LIVE 경기를 휴대폰 잠금화면에서 볼 수 있어요",
                "Watch 토글: LIVE 경기를 스마트워치에서 볼 수 있어요",
                "전체 순위 보기: 아이콘을 누르면 전체 순위를 바로 확인할 수 있어요",
                "전체 일정 보기: 응원팀 시즌 일정을 달력으로 한눈에 확인할 수 있어요",
                "점수 보기: 경기 카드에서 최신 점수와 진행 상황을 더 쉽게 볼 수 있어요",
            ]
        ),
    ]

    static func notes(for version: String) -> ReleaseNote? {
        all.first { $0.version == version }
    }

    static var latest: ReleaseNote? {
        all.first
    }
}
