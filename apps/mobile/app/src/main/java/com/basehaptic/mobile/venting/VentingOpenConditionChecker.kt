package com.basehaptic.mobile.venting

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 분풀이 모드 오픈 조건 판정 (iOS VentingOpenConditionChecker 포팅).
 * 실제 코드로 구현되며, 입력(경기 결과·날짜·마이팀 설정)만 목업 데이터로 주입한다.
 *
 * 오픈 조건:
 * - 마이팀이 설정된 사용자
 * - 마이팀이 패배한 경기
 * - 경기 날짜가 오늘(KST 자정 전)
 * - 무승부·취소·연기 경기는 미오픈
 */
object VentingOpenConditionChecker {

    private val KST = ZoneId.of("Asia/Seoul")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun isOpen(context: VentingGameContext, myTeamId: String): Boolean {
        if (myTeamId.isEmpty()) return false
        if (context.myTeamId != myTeamId) return false
        if (context.gameResult != VentingGameResult.LOSS) return false
        return isTodayKst(context.gameDate)
    }

    private fun isTodayKst(dateString: String): Boolean {
        return try {
            LocalDate.parse(dateString, DATE_FORMAT) == LocalDate.now(KST)
        } catch (e: Exception) {
            false
        }
    }
}
