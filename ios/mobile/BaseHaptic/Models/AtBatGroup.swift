import Foundation

/// 하나의 타석(at-bat) 동안 발생한 LiveEvent 묶음.
///
/// 라이브 상세 화면이 평면 EventCard 스트림 대신 네이버 스포츠 릴레이처럼
/// "한 타석 = 한 카드" 형태로 그릴 수 있게, 백엔드가 노출한
/// `atBatId` 와 `seqno` 를 기준으로 클라이언트에서 그룹화한다.
struct AtBatGroup: Identifiable {
    let id: String              // atBatId 또는 폴백 그룹 키
    let inning: String?         // 그룹의 대표 이닝 ("9회초" 등)
    let batter: String?
    let pitcher: String?
    let time: String            // 가장 최근 이벤트 시각
    let leadCursor: Int64       // 그룹 내 가장 큰 cursor (정렬·중복 회피용)
    let pitches: [LiveEvent]    // seqno asc 정렬된 그룹 내 전체 이벤트
    let outcome: LiveEvent?     // 타석 종료(HIT/OUT/HOMERUN/WALK/SCORE 등) 이벤트. 진행 중이면 nil

    /// 타석 종료를 의미하는 이벤트 타입. 이 중 마지막(가장 큰 seqno) 이 outcome 이 된다.
    /// BALL/STRIKE 처럼 타석 진행 중인 이벤트는 outcome 후보가 아니다.
    private static let outcomeTypes: Set<String> = [
        "HIT", "HOMERUN", "OUT", "WALK",
        "DOUBLE_PLAY", "TRIPLE_PLAY",
        "SCORE", "SAC_FLY_SCORE", "TAG_UP_ADVANCE",
        "STEAL", "PITCHER_CHANGE", "HALF_INNING_CHANGE"
    ]

    /// 평면 LiveEvent 리스트를 타석 단위 그룹으로 변환한다.
    ///
    /// 규칙:
    /// - `atBatId != nil` 이벤트는 atBatId 별로 묶고, 그룹 내부는 `seqno asc` 로 정렬.
    /// - `atBatId == nil` 이벤트는 각자 단일 그룹(현재 평면 UI 와 동등한 폴백).
    /// - 그룹 사이 정렬은 그룹 대표 cursor 의 desc — 최신 타석이 위.
    /// - 그룹의 `batter`/`pitcher`/`time` 은 가장 마지막 유효 이벤트 기준.
    /// - 그룹의 `inning` 은 지연 삽입된 결과 이벤트 오표시를 피하기 위해 그룹 안의 대표값 기준.
    static func group(_ events: [LiveEvent]) -> [AtBatGroup] {
        guard !events.isEmpty else { return [] }

        // atBatId 기준 묶기. 이벤트 저장 시점의 현재 이닝이 이미 다음 공격으로 넘어간
        // 경우에도 같은 타석의 마지막 이벤트가 이전 카드에 붙어야 한다.
        var buckets: [String: [LiveEvent]] = [:]
        var order: [String] = []  // 첫 등장 순서 보존 (안정 정렬용)
        for event in events {
            let key = event.atBatId ?? "__solo_\(event.cursor)"
            if buckets[key] == nil {
                buckets[key] = []
                order.append(key)
            }
            buckets[key]?.append(event)
        }

        let groups: [AtBatGroup] = order.compactMap { key in
            guard let bucket = buckets[key], !bucket.isEmpty else { return nil }
            let sorted = bucket.sorted { lhs, rhs in
                // seqno 가 둘 다 있으면 seqno asc, 아니면 cursor asc 폴백
                switch (lhs.seqno, rhs.seqno) {
                case let (l?, r?): return l < r
                default: return lhs.cursor < rhs.cursor
                }
            }
            let last = sorted.last!
            let outcome = sorted.last(where: { outcomeTypes.contains($0.type.uppercased()) })
            if isPlaceholderIntroGroup(sorted, outcome: outcome) {
                return nil
            }
            let leadCursor = sorted.map(\.cursor).max() ?? last.cursor
            // 선수명은 마지막 유효값을 쓰되, 이닝은 지연 저장된 결과 이벤트가 다음 이닝으로
            // 넘어갈 수 있어 그룹 안에서 가장 많이 나온 값을 대표로 쓴다.
            let resolvedBatter = sorted.reversed().compactMap { $0.batter }.first
            let resolvedPitcher = sorted.reversed().compactMap { $0.pitcher }.first
            let resolvedInning = representativeInning(sorted)
            return AtBatGroup(
                id: key,
                inning: resolvedInning,
                batter: resolvedBatter,
                pitcher: resolvedPitcher,
                time: last.time,
                leadCursor: leadCursor,
                pitches: sorted,
                outcome: outcome
            )
        }

        // 최신 타석이 위로
        return groups.sorted { $0.leadCursor > $1.leadCursor }
    }

    private static func representativeInning(_ events: [LiveEvent]) -> String? {
        var counts: [String: Int] = [:]
        var order: [String] = []
        for event in events {
            guard let inning = event.inning?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !inning.isEmpty else { continue }
            if counts[inning] == nil {
                order.append(inning)
                counts[inning] = 0
            }
            counts[inning, default: 0] += 1
        }
        var selected: String?
        var selectedCount = 0
        for inning in order {
            let count = counts[inning] ?? 0
            if count > selectedCount {
                selected = inning
                selectedCount = count
            }
        }
        return selected
    }

    private static func isPlaceholderIntroGroup(_ events: [LiveEvent], outcome: LiveEvent?) -> Bool {
        if outcome != nil { return false }
        let hasPitchDetail = events.contains { event in
            let pitchStuff = event.pitchStuff?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return event.pitchNum != nil ||
                event.pitchSpeed != nil ||
                !pitchStuff.isEmpty
        }
        if hasPitchDetail { return false }
        let hasOnlyOther = events.allSatisfy { $0.type.uppercased() == "OTHER" }
        let hasBatterRecord = events.contains { $0.batterRecord != nil }
        return hasOnlyOther && hasBatterRecord
    }
}
