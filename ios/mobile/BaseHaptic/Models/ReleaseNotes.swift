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
            version: "1.1.7",
            subtitle: "6/17일 배포",
            bullets: [
                "경기장 날씨 확인 기능 추가",
                "오류 개선",
            ]
        ),
        ReleaseNote(
            version: "1.1.6",
            subtitle: "6/17일 배포",
            bullets: [
                "날씨와 전체 순위 데이터를 안정적으로 불러오도록 서버 연결을 조정했어요",
            ]
        ),
        ReleaseNote(
            version: "1.1.5",
            subtitle: "6/16일 배포",
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
