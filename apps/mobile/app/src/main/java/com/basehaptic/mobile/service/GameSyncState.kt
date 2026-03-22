package com.basehaptic.mobile.service

import com.basehaptic.mobile.data.model.Game
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state between GameSyncForegroundService and UI (MainActivity).
 * The service writes; the UI collects.
 */
object GameSyncState {

    private val _todayGames = MutableStateFlow<List<Game>>(emptyList())
    val todayGames: StateFlow<List<Game>> = _todayGames.asStateFlow()

    private val _gameWentLive = MutableStateFlow<GameWentLiveEvent?>(null)
    val gameWentLive: StateFlow<GameWentLiveEvent?> = _gameWentLive.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    fun updateTodayGames(games: List<Game>) {
        _todayGames.value = games
    }

    fun emitGameWentLive(event: GameWentLiveEvent) {
        _gameWentLive.value = event
    }

    fun consumeGameWentLive() {
        _gameWentLive.value = null
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    data class GameWentLiveEvent(
        val gameId: String,
        val homeTeam: String,
        val awayTeam: String,
        val myTeam: String
    )
}
