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
    val weather: GameWeatherSummary? = null,
    val homePitcher: Pitcher? = null,
    val awayPitcher: Pitcher? = null
)

data class GameWeatherSummary(
    val stadiumCode: String,
    val stadiumName: String,
    val stadiumShortName: String,
    val forecastDate: String? = null,
    val forecastTime: String? = null,
    val forecastTimeLabel: String? = null,
    val condition: String,
    val temperatureC: Int? = null,
    val precipitationProbability: Int? = null,
    val precipitationType: String? = null,
    val windSpeedMps: Double? = null,
    val isIndoor: Boolean = false,
    val displayText: String
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
    FINISHED,
    CANCELED,
    POSTPONED
}
