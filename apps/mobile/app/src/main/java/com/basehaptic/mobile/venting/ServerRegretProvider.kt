package com.basehaptic.mobile.venting

import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 서버 regret-top5(6.1) 응답으로 분풀이 컨텍스트를 조립하는 빌더.
 *
 * 서버 항목(role_label·inning·reason)을 익명 후보로 매핑하고, 박스스코어 조인으로
 * 실명을 해소한다 — 실명은 [RegretCandidate.playerName]에만 담기며 선택 화면 TargetRow
 * 에서만 노출된다(룸·완파 화면은 [VentingTarget.roleLabel]/[eventDescription]만 읽음).
 *
 * [regret].items 가 비어 있으면 null 을 반환하고, 호출자는 [LiveRegretProvider] 로 폴백한다.
 */
object ServerRegretProvider {

    private val KST = ZoneId.of("Asia/Seoul")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun buildContext(
        gameId: String,
        state: BackendGamesRepository.LiveGameState?,
        boxscore: BackendGamesRepository.GameBoxscore?,
        myTeam: Team,
        regret: BackendGamesRepository.VentingRegretTop5
    ): VentingGameContext? {
        if (regret.items.isEmpty()) return null

        val myTeamIsHome = when (myTeam) {
            state?.homeTeamId -> true
            state?.awayTeamId -> false
            else -> null
        }
        val myScore = when (myTeamIsHome) {
            true -> state?.homeScore ?: 0
            false -> state?.awayScore ?: 0
            null -> 0
        }
        val opponentScore = when (myTeamIsHome) {
            true -> state?.awayScore ?: 0
            false -> state?.homeScore ?: 0
            null -> 0
        }
        val isFinished = state?.status == GameStatus.FINISHED
        val gameResult = when {
            state == null -> VentingGameResult.LOSS
            !isFinished -> VentingGameResult.IN_PROGRESS
            myScore < opponentScore -> VentingGameResult.LOSS
            myScore > opponentScore -> VentingGameResult.WIN
            else -> VentingGameResult.DRAW
        }

        val candidates = regret.items.take(5).mapIndexed { index, item ->
            val roleLabel = item.roleLabel?.takeIf { it.isNotBlank() } ?: "선수"
            val reason = item.reason.orEmpty()
            val eventDescription = when {
                !item.inning.isNullOrBlank() && reason.isNotBlank() -> "${item.inning} $reason"
                reason.isNotBlank() -> reason
                !item.inning.isNullOrBlank() -> item.inning
                else -> "아쉬운 순간"
            }
            RegretCandidate(
                id = "server_$index",
                roleLabel = roleLabel,
                eventDescription = eventDescription,
                kind = item.kind,
                teamSide = item.teamSide,
                battingOrder = item.battingOrder,
                appearanceOrder = item.appearanceOrder,
                playerName = resolvePlayerName(item, boxscore)
            )
        }

        return VentingGameContext(
            gameId = gameId,
            gameDate = todayKstString(),
            gameResult = gameResult,
            myTeamId = myTeam.name,
            myScore = myScore,
            opponentScore = opponentScore,
            candidates = candidates,
            managerEventDescription = regret.manager ?: "지금까지의 경기 운영 아쉬움",
            inningLabel = if (state == null || isFinished) null else state.inning
        )
    }

    /** 서버 항목(kind + team_side + 타순/등판순서)을 박스스코어 명단과 조인해 실명을 해소한다. */
    private fun resolvePlayerName(
        item: BackendGamesRepository.VentingRegretItem,
        boxscore: BackendGamesRepository.GameBoxscore?
    ): String? {
        if (boxscore == null) return null
        val isHome = when (item.teamSide?.lowercase()) {
            "home" -> true
            "away" -> false
            else -> return null
        }
        val kind = item.kind?.lowercase()
        return if (kind == "pitcher" || (kind != "batter" && item.appearanceOrder != null)) {
            val pitchers = if (isHome) boxscore.homePitchers else boxscore.awayPitchers
            val order = item.appearanceOrder ?: return null
            pitchers.firstOrNull { it.appearanceOrder == order }?.playerName
        } else {
            val batters = if (isHome) boxscore.homeBatters else boxscore.awayBatters
            val order = item.battingOrder ?: return null
            batters.firstOrNull { it.battingOrder == order }?.playerName
        }
    }

    private fun todayKstString(): String = LocalDate.now(KST).format(DATE_FORMAT)
}
