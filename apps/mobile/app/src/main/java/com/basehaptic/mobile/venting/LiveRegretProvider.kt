package com.basehaptic.mobile.venting

import com.basehaptic.mobile.data.BackendGamesRepository
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 라이브 경기 화면에서 분풀이 진입 시 사용하는 컨텍스트 빌더.
 *
 * 경기 상세 화면이 이미 들고 있는 중계 이벤트·박스스코어에서 "지금까지 이슈가 있던
 * 마이팀 선수" 후보를 클라이언트 규칙으로 추린다 — 백엔드는 수정하지 않는다.
 *
 * 후보 규칙(인터뷰 동결 TOP5 규칙의 라이브 축소판):
 * - 마이팀 공격 중: 병살·삼진 타자
 * - 마이팀 수비 중: 실점(득점·희생플라이·홈런 허용) 시점의 투수
 * - 실명은 [RegretCandidate.playerName]에 담아 선택 화면 TargetRow의 "역할(실명)"
 *   표기에만 사용한다 — 룸·완파 화면은 익명(역할 레이블) 유지.
 */
object LiveRegretProvider {

    private val KST = ZoneId.of("Asia/Seoul")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 마이팀이 참가한 경기가 아니면 null. */
    fun buildContext(
        state: BackendGamesRepository.LiveGameState,
        events: List<BackendGamesRepository.LiveEvent>,
        boxscore: BackendGamesRepository.GameBoxscore?,
        myTeam: Team
    ): VentingGameContext? {
        val myTeamIsHome = when (myTeam) {
            state.homeTeamId -> true
            state.awayTeamId -> false
            else -> return null
        }
        val myScore = if (myTeamIsHome) state.homeScore else state.awayScore
        val opponentScore = if (myTeamIsHome) state.awayScore else state.homeScore

        val isFinished = state.status == GameStatus.FINISHED
        val gameResult = when {
            !isFinished -> VentingGameResult.IN_PROGRESS
            myScore < opponentScore -> VentingGameResult.LOSS
            myScore > opponentScore -> VentingGameResult.WIN
            else -> VentingGameResult.DRAW
        }

        return VentingGameContext(
            gameId = state.gameId,
            gameDate = todayKstString(),
            gameResult = gameResult,
            myTeamId = myTeam.name,
            myScore = myScore,
            opponentScore = opponentScore,
            candidates = buildCandidates(events, boxscore, myTeamIsHome),
            managerEventDescription = "지금까지의 경기 운영 아쉬움",
            inningLabel = if (isFinished) null else state.inning
        )
    }

    private fun buildCandidates(
        events: List<BackendGamesRepository.LiveEvent>,
        boxscore: BackendGamesRepository.GameBoxscore?,
        myTeamIsHome: Boolean
    ): List<RegretCandidate> {
        val myBatters = if (myTeamIsHome) boxscore?.homeBatters else boxscore?.awayBatters
        val myPitchers = if (myTeamIsHome) boxscore?.homePitchers else boxscore?.awayPitchers

        // events 는 최신순(cursor 내림차순) — 최근 이슈가 먼저 후보에 오른다.
        val candidates = mutableListOf<RegretCandidate>()
        for (event in events) {
            val inning = event.inning ?: continue
            val homeBatting = when {
                inning.contains("말") -> true
                inning.contains("초") -> false
                else -> continue
            }
            val myTeamBatting = homeBatting == myTeamIsHome
            val type = event.type.uppercase()

            val candidate = if (myTeamBatting) {
                val isBatterRegret = type == "DOUBLE_PLAY" || type == "TRIPLE_PLAY" ||
                    type == "STRIKEOUT" || (type == "OUT" && event.description.contains("삼진"))
                if (!isBatterRegret) continue
                RegretCandidate(
                    id = "live_${event.cursor}",
                    roleLabel = batterRoleLabel(event.batter, myBatters),
                    eventDescription = "$inning ${stripNamePrefix(event.description)}",
                    playerName = normalizedPlayerName(event.batter)
                )
            } else {
                val isPitcherRegret = type == "SCORE" || type == "SAC_FLY_SCORE" || type == "HOMERUN"
                if (!isPitcherRegret) continue
                RegretCandidate(
                    id = "live_${event.cursor}",
                    roleLabel = pitcherRoleLabel(event.pitcher, myPitchers),
                    eventDescription = "$inning ${stripNamePrefix(event.description)} 허용",
                    playerName = normalizedPlayerName(event.pitcher)
                )
            }
            candidates.add(candidate)
        }

        // 같은 선수(실명 없으면 같은 역할)는 가장 최근 사건 1건만 남긴다.
        return candidates.distinctBy { it.playerName ?: it.roleLabel }.take(5)
    }

    /** 공백 정리 후 비어 있으면 null — 선택 화면 "역할(실명)" 표기용. */
    private fun normalizedPlayerName(name: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() }

    /** 박스스코어 타순으로 "N번 타자" 레이블 생성. 매칭 실패 시 "타자". */
    private fun batterRoleLabel(
        batterName: String?,
        batters: List<BackendGamesRepository.BoxscoreBatter>?
    ): String {
        if (batterName.isNullOrBlank() || batters == null) return "타자"
        val order = batters.firstOrNull { it.playerName == batterName }?.battingOrder
        return if (order != null && order in 1..9) "${order}번 타자" else "타자"
    }

    /** 박스스코어 등판 정보로 "선발/구원 투수" 레이블 생성. 매칭 실패 시 "투수". */
    private fun pitcherRoleLabel(
        pitcherName: String?,
        pitchers: List<BackendGamesRepository.BoxscorePitcher>?
    ): String {
        if (pitcherName.isNullOrBlank() || pitchers == null) return "투수"
        val pitcher = pitchers.firstOrNull { it.playerName == pitcherName } ?: return "투수"
        return if (pitcher.isStarter) "선발 투수" else "구원 투수"
    }

    /** 중계 문구의 "선수명 : 내용" 앞부분을 제거해 사건 문구를 정리한다 (실명은 제목의 "역할(실명)" 표기가 담당). */
    private fun stripNamePrefix(description: String): String {
        val separatorIndex = description.indexOf(" : ")
        return if (separatorIndex > 0) {
            description.substring(separatorIndex + 3).trim()
        } else {
            description
        }
    }

    private fun todayKstString(): String = LocalDate.now(KST).format(DATE_FORMAT)
}
