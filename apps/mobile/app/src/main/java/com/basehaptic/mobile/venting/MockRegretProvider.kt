package com.basehaptic.mobile.venting

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 1 목업 경기 컨텍스트 제공자 (iOS MockRegretProvider 포팅).
 * iOS mock_regret_candidates.json과 동일한 데이터를 반환한다.
 *
 * Phase 2에서 실제 백엔드 API 제공자로 교체 시 이 파일만 교체하면 되며,
 * 화면 코드는 무수정이다.
 */
object MockRegretProvider {

    /**
     * 목업이 특정 팀·특정 날짜에 묶이지 않도록, 현재 설정된 마이팀과 오늘(KST)을 주입한다.
     * 어떤 팀 팬이든·어느 날이든 실기기에서 분풀이 모드 오픈 조건이 충족된다.
     */
    fun fetchVentingContext(context: Context): VentingGameContext {
        val myTeam = context.applicationContext
            .getSharedPreferences("basehaptic_user_prefs", Context.MODE_PRIVATE)
            .getString("selected_team", null)

        return VentingGameContext(
            gameId = "20260720HHLG",
            gameDate = todayKstString(),
            gameResult = VentingGameResult.LOSS,
            myTeamId = myTeam ?: "HANWHA",
            myScore = 3,
            opponentScore = 7,
            candidates = listOf(
                RegretCandidate("c1", "선발 투수", "5이닝 6실점 조기 강판"),
                RegretCandidate("c2", "3번 타자", "4타수 무안타 잔루 5"),
                RegretCandidate("c3", "마무리 투수", "9회 3실점 블론세이브"),
                RegretCandidate("c4", "2번 타자", "8회 2사 만루 삼진"),
                RegretCandidate("c5", "유격수", "6회 결정적 실책")
            ),
            managerEventDescription = "만루 위기서 투수 교체 타이밍 아쉬움"
        )
    }

    private fun todayKstString(): String =
        LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
