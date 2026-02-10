package com.basehaptic.mobile.data.model

data class Game(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeTeamId: Team,
    val awayTeamId: Team,
    val homeScore: Int,
    val awayScore: Int,
    val inning: String,
    val status: GameStatus,
    val time: String? = null,
    val isMyTeam: Boolean = false,
    val homePitcher: Pitcher? = null,
    val awayPitcher: Pitcher? = null
)

data class Pitcher(
    val name: String,
    val winStreak: Int,
    val record: PitcherRecord
)

data class PitcherRecord(
    val wins: Int,
    val draws: Int,
    val losses: Int
)

enum class GameStatus {
    LIVE,
    SCHEDULED,
    FINISHED
}

