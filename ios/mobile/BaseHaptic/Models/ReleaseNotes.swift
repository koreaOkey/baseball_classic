import Foundation

/// 대표 기능 소개 페이지 (슬라이드형 업데이트 안내).
/// visual 은 카드 상단 비주얼 영역의 렌더 방식:
/// - image: 스크린샷을 영역에 꽉 채워(fill) 표시
/// - lockScreen: 시계·날짜 잠금 화면 프레임 안에 노티 카드 이미지를 얹어 표시
struct WhatsNewFeaturePage {
    enum Visual {
        case image(String)
        case lockScreen(String)
    }

    let visual: Visual
    let title: String
    let body: String
}

struct ReleaseNote: Identifiable {
    var id: String { version }
    let version: String
    let subtitle: String
    let bullets: [String]
    /// 비어 있으면 기존 단일 불릿 모달, 있으면 슬라이드형(대표 기능 페이지들 + 마지막 불릿 페이지).
    var featurePages: [WhatsNewFeaturePage] = []
}

enum ReleaseNotes {
    // 새 버전 출시 시 entry 1개를 추가한다.
    // version 은 Info.plist `CFBundleShortVersionString` 과 정확히 일치해야 한다.
    // 일치하는 entry 가 없으면 모달이 노출되지 않는다.
    static let all: [ReleaseNote] = [
        ReleaseNote(
            version: "1.1.8",
            subtitle: "8월 업데이트",
            bullets: [
                "라이브 상세에 라인스코어·박스스코어 탭이 생겼어요",
                "타석 카드에 타순과 오늘 성적을 보여드려요",
                "워치로 관람하기가 더 빨라졌어요",
            ],
            featurePages: [
                WhatsNewFeaturePage(
                    visual: .image("WhatsNewVenting"),
                    title: "💢 빠따존이 생겼어요",
                    body: "속상한 경기엔 펭귄 인형 한 대! 폰을 흔들거나 두드려서 아쉬움을 날려보세요."
                ),
                WhatsNewFeaturePage(
                    visual: .lockScreen("WhatsNewLockScreen"),
                    title: "잠금 화면에서 실시간 스코어",
                    body: "이제 앱을 열지 않아도 경기 상황이 잠금 화면에서 실시간으로 업데이트돼요."
                ),
            ]
        ),
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
