package com.basehaptic.watch.venting

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 워치 자체 "아쉬운 순간" 축적기.
 *
 * 워치는 중계 기록(타순·박스스코어·실명)이 없으므로, 라이브 중 수신하는 이벤트
 * 타입 + 그 시점 이닝만으로 이닝 기반 역할 레이블("7회 병살 타자")을 만들어 쌓는다.
 * 실명 미표기 원칙(폰 자동 후보와 동일)을 자연스럽게 만족한다.
 *
 * - 경기 중 선택 화면: 최신순 (그 시점까지 이슈가 있었던 사람)
 * - 패배 확정 후: 심각도(실점 3 > 병살 2 > 아웃 1) 가중 정렬 (패배 지분 근사,
 *   Phase 2 백엔드 regret-top5 API로 교체 예정 자리)
 * - 새 경기 감지 시 자동 초기화.
 */
object WatchRegretTracker {

    private const val PREFS_NAME = "watch_venting_prefs"
    private const val KEY_GAME_ID = "regret_game_id"
    private const val KEY_ENTRIES = "regret_entries"
    private const val MAX_STORED = 12
    private const val MAX_SHOWN = 5

    data class Entry(
        val label: String,
        val description: String,
        val severity: Int,
        val atMs: Long
    )

    /**
     * 이벤트 1건 기록. [myTeamBatting]이 null이면 공수 판정 불가(중립/데이터 부족)로,
     * 타자·투수 이슈 모두 기록해 테스트 경기에서도 후보가 쌓이게 한다.
     */
    fun record(
        context: Context,
        gameId: String,
        eventType: String,
        inning: String,
        myTeamBatting: Boolean?
    ) {
        if (gameId.isBlank()) return
        val inningLabel = inningNumberLabel(inning) ?: return

        val batting = myTeamBatting != false
        val fielding = myTeamBatting != true
        val entry = when (eventType.uppercase()) {
            "DOUBLE_PLAY" -> if (batting) Entry("$inningLabel 병살 타자", "$inning 병살타", 2, now()) else null
            "TRIPLE_PLAY" -> if (batting) Entry("$inningLabel 삼중살 타자", "$inning 삼중살", 2, now()) else null
            "OUT" -> if (batting) Entry("$inningLabel 아웃 타자", "$inning 아웃", 1, now()) else null
            "SCORE", "SAC_FLY_SCORE" -> if (fielding) Entry("$inningLabel 실점 투수", "$inning 실점 허용", 3, now()) else null
            "HOMERUN" -> if (fielding) Entry("$inningLabel 피홈런 투수", "$inning 홈런 허용", 3, now()) else null
            else -> null
        } ?: return

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val entries = if (prefs.getString(KEY_GAME_ID, "") == gameId) {
            readEntries(prefs.getString(KEY_ENTRIES, null))
        } else {
            mutableListOf() // 새 경기 → 초기화
        }
        entries.add(entry)
        while (entries.size > MAX_STORED) entries.removeAt(0)

        prefs.edit()
            .putString(KEY_GAME_ID, gameId)
            .putString(KEY_ENTRIES, writeEntries(entries))
            .apply()
    }

    /**
     * 선택 화면용 후보 목록 (같은 레이블은 최근 1건, 최대 5건).
     * @param rankBySeverity true = 패배 확정 후 지분 정렬, false = 경기 중 최신순
     */
    fun candidates(context: Context, gameId: String, rankBySeverity: Boolean): List<Entry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_GAME_ID, "") != gameId) return emptyList()
        val entries = readEntries(prefs.getString(KEY_ENTRIES, null))

        val ordered = if (rankBySeverity) {
            entries.sortedWith(compareByDescending<Entry> { it.severity }.thenByDescending { it.atMs })
        } else {
            entries.sortedByDescending { it.atMs }
        }
        return ordered.distinctBy { it.label }.take(MAX_SHOWN)
    }

    private fun inningNumberLabel(inning: String): String? {
        val match = Regex("(\\d+)회").find(inning) ?: return null
        return "${match.groupValues[1]}회"
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun readEntries(raw: String?): MutableList<Entry> {
        if (raw.isNullOrBlank()) return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    label = o.optString("label"),
                    description = o.optString("desc"),
                    severity = o.optInt("sev", 1),
                    atMs = o.optLong("at", 0L)
                )
            }
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun writeEntries(entries: List<Entry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("label", e.label)
                put("desc", e.description)
                put("sev", e.severity)
                put("at", e.atMs)
            })
        }
        return arr.toString()
    }
}
