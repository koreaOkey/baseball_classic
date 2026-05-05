package com.basehaptic.watch.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.basehaptic.watch.DataLayerListenerService
import com.basehaptic.watch.MainActivity
import com.basehaptic.watch.WatchFinishedGameCache
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Wear OS Tile that shows live game score.
 * Accessible by swiping left from the watch face.
 */
class GameTileService : TileService() {

    companion object {
        private const val RESOURCES_VERSION = "1"
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val gameData = readGameData()
        val layout = if (gameData != null) {
            buildGameLayout(gameData)
        } else {
            buildNoGameLayout()
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(5_000) // refresh every 5 seconds
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildGameLayout(game: TileGameData): LayoutElementBuilders.LayoutElement {
        val tapAction = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(tapAction)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF0A0A0B.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        // Inning
                        LayoutElementBuilders.Text.Builder()
                            .setText(game.inning)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(0xFFF97316.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(spacer(4f))
                    .addContent(
                        // Score row: AWAY score - score HOME
                        LayoutElementBuilders.Row.Builder()
                            .setWidth(expand())
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(
                                // Away team + score
                                LayoutElementBuilders.Column.Builder()
                                    .setWidth(expand())
                                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                    .addContent(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText(game.awayScore.toString())
                                            .setFontStyle(
                                                LayoutElementBuilders.FontStyle.Builder()
                                                    .setSize(sp(32f))
                                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                                    .setColor(argb(0xFFFFFFFF.toInt()))
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .addContent(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText(game.awayTeam)
                                            .setFontStyle(
                                                LayoutElementBuilders.FontStyle.Builder()
                                                    .setSize(sp(11f))
                                                    .setColor(argb(0xBBFFFFFF.toInt()))
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                // Dash separator
                                LayoutElementBuilders.Text.Builder()
                                    .setText("-")
                                    .setFontStyle(
                                        LayoutElementBuilders.FontStyle.Builder()
                                            .setSize(sp(20f))
                                            .setColor(argb(0x88FFFFFF.toInt()))
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(
                                // Home team + score
                                LayoutElementBuilders.Column.Builder()
                                    .setWidth(expand())
                                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                    .addContent(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText(game.homeScore.toString())
                                            .setFontStyle(
                                                LayoutElementBuilders.FontStyle.Builder()
                                                    .setSize(sp(32f))
                                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                                    .setColor(argb(0xFFFFFFFF.toInt()))
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .addContent(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText(game.homeTeam)
                                            .setFontStyle(
                                                LayoutElementBuilders.FontStyle.Builder()
                                                    .setSize(sp(11f))
                                                    .setColor(argb(0xBBFFFFFF.toInt()))
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .addContent(spacer(6f))
                    .addContent(
                        // BSO line
                        LayoutElementBuilders.Text.Builder()
                            .setText("B${game.ball}  S${game.strike}  O${game.out}")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(argb(0x99FFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildNoGameLayout(): LayoutElementBuilders.LayoutElement {
        val tapAction = ModifiersBuilders.Clickable.Builder()
            .setId("open_app")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(tapAction)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF0A0A0B.toInt()))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("BaseHaptic")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(16f))
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .setColor(argb(0xFFFFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(spacer(4f))
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText("진행 중인 경기 없음")
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(12f))
                                    .setColor(argb(0x99FFFFFF.toInt()))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder()
            .setHeight(dp(heightDp))
            .build()
    }

    private fun readGameData(): TileGameData? {
        val prefs = getSharedPreferences(
            DataLayerListenerService.GAME_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val gameId = prefs.getString(DataLayerListenerService.KEY_GAME_ID, "") ?: ""
        if (gameId.isBlank()) return null

        // 종료된 경기가 KST 자정을 넘긴 경우 캐시 삭제 후 경기 없음으로 표시
        val inning = prefs.getString(DataLayerListenerService.KEY_INNING, "") ?: ""
        val status = prefs.getString(DataLayerListenerService.KEY_STATUS, "") ?: ""
        val isFinished = status.equals("FINISHED", ignoreCase = true) ||
            inning.contains("경기 종료") || inning.uppercase().let { it.contains("FINAL") || it.contains("GAME OVER") }
        if (isFinished) {
            val updatedAt = prefs.getLong(DataLayerListenerService.KEY_GAME_UPDATED_AT, 0L)
            if (WatchFinishedGameCache.isExpired(updatedAt)) {
                WatchFinishedGameCache.clearGameData(prefs)
                return null
            }
        }

        return TileGameData(
            homeTeam = prefs.getString(DataLayerListenerService.KEY_HOME_TEAM, "") ?: "",
            awayTeam = prefs.getString(DataLayerListenerService.KEY_AWAY_TEAM, "") ?: "",
            homeScore = prefs.getInt(DataLayerListenerService.KEY_HOME_SCORE, 0),
            awayScore = prefs.getInt(DataLayerListenerService.KEY_AWAY_SCORE, 0),
            inning = prefs.getString(DataLayerListenerService.KEY_INNING, "") ?: "",
            ball = prefs.getInt(DataLayerListenerService.KEY_BALL, 0),
            strike = prefs.getInt(DataLayerListenerService.KEY_STRIKE, 0),
            out = prefs.getInt(DataLayerListenerService.KEY_OUT, 0)
        )
    }

    private data class TileGameData(
        val homeTeam: String,
        val awayTeam: String,
        val homeScore: Int,
        val awayScore: Int,
        val inning: String,
        val ball: Int,
        val strike: Int,
        val out: Int
    )
}
