package com.basehaptic.watch.data

import androidx.compose.ui.graphics.Color

data class GameData(
    val gameId: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val inning: String,
    val isLive: Boolean,
    val ballCount: Int,
    val strikeCount: Int,
    val outCount: Int,
    val bases: BaseStatus,
    val pitcher: String,
    val batter: String,
    /// 활성 투수의 투구수. 백엔드에서 못 받았을 때는 null → UI 에서 "· N구" 부분을 숨긴다.
    val pitcherPitchCount: Int? = null,
    val scoreDiff: Int,
    val myTeamName: String,         // 나의 응원팀 이름 (테마 결정에 사용)
    val mascotUrl: String? = null
)

data class BaseStatus(
    val first: Boolean = false,
    val second: Boolean = false,
    val third: Boolean = false
)

// Mock data for preview
fun getMockGameData() = GameData(
    gameId = "1",
    homeTeam = "SSG",
    awayTeam = "KIA",
    homeScore = 5,
    awayScore = 4,
    inning = "9회말",
    isLive = true,
    ballCount = 3,
    strikeCount = 2,
    outCount = 2,
    bases = BaseStatus(first = true, third = true),
    pitcher = "KIM",
    batter = "LEE",
    pitcherPitchCount = 87,
    scoreDiff = 1,
    myTeamName = "SSG"
)
