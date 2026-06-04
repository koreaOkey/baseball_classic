package com.basehaptic.mobile.data.model

import com.basehaptic.mobile.data.BackendGamesRepository.LiveEvent

/**
 * 하나의 타석(at-bat) 동안 발생한 LiveEvent 묶음.
 *
 * 라이브 상세 화면이 평면 EventCard 스트림 대신 네이버 스포츠 릴레이처럼
 * "한 타석 = 한 카드" 형태로 그릴 수 있게, 백엔드가 노출한 [LiveEvent.atBatId] /
 * [LiveEvent.seqno] 를 기준으로 클라이언트에서 그룹화한다.
 *
 * iOS `AtBatGroup` 과 동일 그룹화 규칙을 유지한다.
 */
data class AtBatGroup(
    /** atBatId 또는 폴백 그룹 키. */
    val id: String,
    val inning: String?,
    val batter: String?,
    val pitcher: String?,
    /** 가장 최근 이벤트 시각. */
    val time: String,
    /** 그룹 내 가장 큰 cursor (정렬·중복 회피용). */
    val leadCursor: Long,
    /** seqno asc 정렬된 그룹 내 전체 이벤트. */
    val pitches: List<LiveEvent>,
    /** 타석 종료(HIT/OUT/HOMERUN/WALK/SCORE 등) 이벤트. 진행 중이면 null. */
    val outcome: LiveEvent?,
) {
    companion object {
        /**
         * 타석 종료를 의미하는 이벤트 타입. 이 중 마지막(가장 큰 seqno) 이 outcome 이 된다.
         * BALL/STRIKE 처럼 타석 진행 중인 이벤트는 outcome 후보가 아니다.
         */
        private val outcomeTypes: Set<String> = setOf(
            "HIT", "HOMERUN", "OUT", "WALK",
            "DOUBLE_PLAY", "TRIPLE_PLAY",
            "SCORE", "SAC_FLY_SCORE", "TAG_UP_ADVANCE",
            "STEAL", "PITCHER_CHANGE", "HALF_INNING_CHANGE",
        )

        /**
         * 평면 LiveEvent 리스트를 타석 단위 그룹으로 변환한다.
         *
         * 규칙:
         * - `atBatId != null` 이벤트는 atBatId 별로 묶고, 그룹 내부는 `seqno asc` 로 정렬.
         * - `atBatId == null` 이벤트는 각자 단일 그룹(현재 평면 UI 와 동등한 폴백).
         * - 그룹 사이 정렬은 그룹 대표 cursor 의 desc — 최신 타석이 위.
         * - 그룹의 batter/pitcher/inning/time 은 가장 마지막 이벤트 기준.
         */
        fun group(events: List<LiveEvent>): List<AtBatGroup> {
            if (events.isEmpty()) return emptyList()

            // atBatId 기준 묶기. null 인 이벤트는 cursor 를 폴백 키로 써서 각자 단일 그룹.
            val buckets = LinkedHashMap<String, MutableList<LiveEvent>>()
            for (event in events) {
                val key = event.atBatId ?: "__solo_${event.cursor}"
                buckets.getOrPut(key) { mutableListOf() }.add(event)
            }

            val groups = buckets.map { (key, bucket) ->
                val sorted = bucket.sortedWith(
                    compareBy(
                        // seqno 가 둘 다 있으면 seqno asc, 아니면 cursor asc 폴백
                        { it.seqno ?: Int.MAX_VALUE },
                        { it.cursor },
                    )
                )
                val last = sorted.last()
                val outcome = sorted.lastOrNull { outcomeTypes.contains(it.type.uppercase()) }
                val leadCursor = sorted.maxOf { it.cursor }
                // 마지막 이벤트가 batter/pitcher/inning 의 가장 정확한 스냅샷
                val resolvedBatter = sorted.reversed().firstNotNullOfOrNull { it.batter }
                val resolvedPitcher = sorted.reversed().firstNotNullOfOrNull { it.pitcher }
                val resolvedInning = sorted.reversed().firstNotNullOfOrNull { it.inning }
                AtBatGroup(
                    id = key,
                    inning = resolvedInning,
                    batter = resolvedBatter,
                    pitcher = resolvedPitcher,
                    time = last.time,
                    leadCursor = leadCursor,
                    pitches = sorted,
                    outcome = outcome,
                )
            }

            // 최신 타석이 위로
            return groups.sortedByDescending { it.leadCursor }
        }
    }
}
