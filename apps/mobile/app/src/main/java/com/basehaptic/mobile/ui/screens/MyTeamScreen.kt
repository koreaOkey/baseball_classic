package com.basehaptic.mobile.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.basehaptic.mobile.auth.SupabaseClientProvider
import com.basehaptic.mobile.data.model.Game
import com.basehaptic.mobile.data.model.GameStatus
import com.basehaptic.mobile.data.model.Team
import com.basehaptic.mobile.stadium.CheerSignalsLoader
import com.google.android.gms.location.LocationServices
import com.basehaptic.mobile.ui.theme.AppFont
import com.basehaptic.mobile.ui.theme.AppShapes
import com.basehaptic.mobile.ui.theme.AppSpacing
import com.basehaptic.mobile.ui.theme.Gray100
import com.basehaptic.mobile.ui.theme.Gray300
import com.basehaptic.mobile.ui.theme.Gray400
import com.basehaptic.mobile.ui.theme.Gray500
import com.basehaptic.mobile.ui.theme.Gray800
import com.basehaptic.mobile.ui.theme.Gray900
import com.basehaptic.mobile.ui.theme.Gray950
import com.basehaptic.mobile.ui.theme.LocalTeamTheme
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MyTeamScreen(
    selectedTeam: Team,
    todayGames: List<Game> = emptyList(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val hasTeam = selectedTeam != Team.NONE
    val checkinGame = todayGames.checkinGameFor(selectedTeam)
    val checkinVenue = checkinGame?.homeTeamId?.homeVenue() ?: CheckinVenue("오늘 경기장", "경기 일정 확인 중")
    val hasLocationPermission = context.hasLocationPermission()
    var isOutsideCheckinVenue by remember(selectedTeam, checkinVenue, hasLocationPermission) {
        mutableStateOf(false)
    }
    var localCheckinRecord by remember(selectedTeam, checkinVenue, today) {
        mutableStateOf(loadLocalCheckinRecord(context, selectedTeam, today))
    }
    val checkinState = when {
        !hasTeam -> CheerCheckinState.PermissionNeeded
        !hasLocationPermission -> CheerCheckinState.PermissionNeeded
        localCheckinRecord.checkedInToday -> CheerCheckinState.CheckedIn
        checkinGame == null -> CheerCheckinState.NoGameToday
        isOutsideCheckinVenue -> CheerCheckinState.OutsideVenue
        else -> CheerCheckinState.Ready
    }
    var activeInfoPopup by remember { mutableStateOf<MyTeamInfoPopup?>(null) }
    var venueMismatchMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTeam, checkinVenue, hasLocationPermission) {
        isOutsideCheckinVenue = if (hasTeam && hasLocationPermission && checkinVenue.hasCoordinate) {
            val currentLocation = runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            }.getOrNull()
            currentLocation?.isOutside(checkinVenue, CHECKIN_RADIUS_METERS) ?: false
        } else {
            false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray950)
            .padding(horizontal = AppSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
    ) {
        item {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            MyTeamHeader(
                selectedTeam = selectedTeam,
                activeInfoPopup = activeInfoPopup,
                onInfoClick = { activeInfoPopup = it },
            )
        }
        item {
            CheerCheckinCard(
                selectedTeamLabel = if (hasTeam) selectedTeam.teamName else "응원팀",
                stadiumName = checkinVenue.name,
                stadiumRegion = checkinVenue.region,
                state = checkinState,
                onPrimaryClick = {
                    when {
                        checkinState == CheerCheckinState.OutsideVenue -> {
                            venueMismatchMessage = "${selectedTeam.teamName} 경기 구장이 아닙니다."
                        }
                        hasTeam && !localCheckinRecord.checkedInToday -> {
                            localCheckinRecord = recordLocalCheckin(context, selectedTeam, today, checkinVenue.name)
                            coroutineScope.launch {
                                postCheckinIfAuthenticated(selectedTeam, checkinGame)
                            }
                        }
                    }
                },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.xl,
                color = Gray900,
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    SectionTitle(
                        icon = Icons.Default.EmojiEvents,
                        title = "팀 체크인 랭킹",
                        subtitle = "직관 인증으로 쌓이는 팬덤 순위",
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    TeamCheckinRankingScreen(
                        selectedTeam = selectedTeam,
                        localWeeklyBoost = if (localCheckinRecord.checkedInToday) 1 else 0,
                    )
                }
            }
        }
        item {
            MyCheckinSummaryCard(
                enabled = hasTeam,
                checkedInToday = localCheckinRecord.checkedInToday,
                weeklyCount = localCheckinRecord.weeklyCount,
                seasonCount = localCheckinRecord.seasonCount,
                lastVenue = localCheckinRecord.lastVenue,
            )
        }
        item {
            Spacer(modifier = Modifier.height(AppSpacing.bottomSafeSpacer))
        }
    }

    activeInfoPopup?.let { popup ->
        MyTeamInfoDialog(
            popup = popup,
            selectedTeam = selectedTeam,
            onDismiss = { activeInfoPopup = null },
        )
    }

    venueMismatchMessage?.let { message ->
        SimpleMyTeamDialog(
            title = "체크인할 수 없습니다",
            body = message,
            onDismiss = { venueMismatchMessage = null },
        )
    }
}

@Composable
private fun MyTeamHeader(
    selectedTeam: Team,
    activeInfoPopup: MyTeamInfoPopup?,
    onInfoClick: (MyTeamInfoPopup) -> Unit,
) {
    val teamTheme = LocalTeamTheme.current
    val teamLabel = if (selectedTeam == Team.NONE) "응원팀 미설정" else selectedTeam.teamName

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "내 팀", style = AppFont.h2, color = Color.White)
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = "체크인, 워치 응원, 랭킹을 한 곳에서 관리해요",
                    style = AppFont.body,
                    color = Gray400,
                )
            }
            Surface(
                shape = AppShapes.pill,
                color = teamTheme.primary.copy(alpha = 0.18f),
            ) {
                Text(
                    text = teamLabel,
                    style = AppFont.captionBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.lg))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.lg,
            color = Gray900,
        ) {
            Row(
                modifier = Modifier.padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    icon = Icons.Default.LocationOn,
                    text = "구장 체크인",
                    selected = activeInfoPopup == MyTeamInfoPopup.Checkin,
                    onClick = { onInfoClick(MyTeamInfoPopup.Checkin) },
                )
                StatusPill(
                    icon = Icons.Default.Watch,
                    text = "워치 응원",
                    selected = activeInfoPopup == MyTeamInfoPopup.WatchCheer,
                    onClick = { onInfoClick(MyTeamInfoPopup.WatchCheer) },
                )
                StatusPill(
                    icon = Icons.Default.EmojiEvents,
                    text = "랭킹",
                    selected = activeInfoPopup == MyTeamInfoPopup.Ranking,
                    onClick = { onInfoClick(MyTeamInfoPopup.Ranking) },
                )
            }
        }
    }
}

@Composable
private fun MyTeamInfoDialog(
    popup: MyTeamInfoPopup,
    selectedTeam: Team,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.xl,
            color = Gray950,
        ) {
            Column(modifier = Modifier.padding(AppSpacing.md)) {
                when (popup) {
                    MyTeamInfoPopup.Checkin -> CheckinInfoCard(selectedTeam = selectedTeam)
                    MyTeamInfoPopup.WatchCheer -> WatchCheerPreviewCard(selectedTeam = selectedTeam)
                    MyTeamInfoPopup.Ranking -> RankingInfoCard(selectedTeam = selectedTeam)
                }
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = "닫기", style = AppFont.captionBold, color = Gray300)
                }
            }
        }
    }
}

private enum class MyTeamInfoPopup {
    Checkin,
    WatchCheer,
    Ranking,
}

private data class CheckinVenue(
    val name: String,
    val region: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

private val CheckinVenue.hasCoordinate: Boolean
    get() = latitude != null && longitude != null

private const val CHECKIN_RADIUS_METERS = 500f

private data class LocalCheckinRecord(
    val checkedInToday: Boolean = false,
    val weeklyCount: Int = 0,
    val seasonCount: Int = 0,
    val lastVenue: String = "",
)

private const val LOCAL_CHECKIN_PREFS = "my_team_local_checkins"

private fun loadLocalCheckinRecord(
    context: Context,
    selectedTeam: Team,
    today: LocalDate,
): LocalCheckinRecord {
    if (selectedTeam == Team.NONE) return LocalCheckinRecord()
    val prefs = context.getSharedPreferences(LOCAL_CHECKIN_PREFS, Context.MODE_PRIVATE)
    val dates = prefs.getStringSet(localCheckinDatesKey(selectedTeam), emptySet()).orEmpty()
    val todayString = today.toString()
    val weekFields = WeekFields.of(Locale.KOREA)
    val weeklyCount = dates.count { dateString ->
        runCatching { LocalDate.parse(dateString) }.getOrNull()?.let { date ->
            date.get(weekFields.weekBasedYear()) == today.get(weekFields.weekBasedYear()) &&
                date.get(weekFields.weekOfWeekBasedYear()) == today.get(weekFields.weekOfWeekBasedYear())
        } ?: false
    }
    return LocalCheckinRecord(
        checkedInToday = dates.contains(todayString),
        weeklyCount = weeklyCount,
        seasonCount = dates.size,
        lastVenue = prefs.getString(localCheckinLastVenueKey(selectedTeam), "").orEmpty(),
    )
}

private fun recordLocalCheckin(
    context: Context,
    selectedTeam: Team,
    today: LocalDate,
    venueName: String,
): LocalCheckinRecord {
    if (selectedTeam == Team.NONE) return LocalCheckinRecord()
    val prefs = context.getSharedPreferences(LOCAL_CHECKIN_PREFS, Context.MODE_PRIVATE)
    val dates = prefs.getStringSet(localCheckinDatesKey(selectedTeam), emptySet()).orEmpty().toMutableSet()
    dates.add(today.toString())
    prefs.edit()
        .putStringSet(localCheckinDatesKey(selectedTeam), dates)
        .putString(localCheckinLastVenueKey(selectedTeam), venueName)
        .apply()
    return loadLocalCheckinRecord(context, selectedTeam, today)
}

private fun localCheckinDatesKey(team: Team): String = "dates_${team.name}"

private fun localCheckinLastVenueKey(team: Team): String = "last_venue_${team.name}"

private fun List<Game>.checkinGameFor(selectedTeam: Team): Game? {
    if (selectedTeam == Team.NONE) return null
    return firstOrNull { game ->
        game.status != GameStatus.CANCELED &&
            game.status != GameStatus.POSTPONED &&
            (game.homeTeamId == selectedTeam || game.awayTeamId == selectedTeam)
    }
}

private fun Team.homeVenue(): CheckinVenue {
    return when (this) {
        Team.DOOSAN,
        Team.LG -> CheckinVenue("잠실야구장", "서울", 37.5121, 127.0719)
        Team.KIWOOM -> CheckinVenue("고척스카이돔", "서울", 37.4982, 126.8670)
        Team.SSG -> CheckinVenue("인천SSG랜더스필드", "서울시 세종대로 67", 37.5628, 126.9752)
        Team.KT -> CheckinVenue("수원KT위즈파크", "수원", 37.2997, 127.0097)
        Team.HANWHA -> CheckinVenue("대전한화생명이글스파크", "대전", 36.3170, 127.4291)
        Team.SAMSUNG -> CheckinVenue("대구삼성라이온즈파크", "대구", 35.8411, 128.6817)
        Team.LOTTE -> CheckinVenue("사직야구장", "부산", 35.1939, 129.0617)
        Team.KIA -> CheckinVenue("광주기아챔피언스필드", "광주", 35.1681, 126.8889)
        Team.NC -> CheckinVenue("창원NC파크", "창원", 35.2225, 128.5822)
        Team.NONE -> CheckinVenue("오늘 경기장", "지역 확인 중")
    }
}

private fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

private fun Location.isOutside(venue: CheckinVenue, radiusMeters: Float): Boolean {
    val venueLatitude = venue.latitude ?: return false
    val venueLongitude = venue.longitude ?: return false
    val distance = FloatArray(1)
    Location.distanceBetween(latitude, longitude, venueLatitude, venueLongitude, distance)
    return distance.first() > radiusMeters
}

@Composable
private fun SimpleMyTeamDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.xl,
            color = Gray950,
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Text(text = title, style = AppFont.h5Bold, color = Color.White)
                Spacer(modifier = Modifier.height(AppSpacing.md))
                Text(text = body, style = AppFont.body, color = Gray300)
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = "확인", style = AppFont.captionBold, color = Gray300)
                }
            }
        }
    }
}

private fun Team.stadiumCode(): String? {
    return when (this) {
        Team.DOOSAN,
        Team.LG -> "JAMSIL"
        Team.KIWOOM -> "GOCHEOK"
        Team.SSG -> "INCHEON"
        Team.KT -> "SUWON"
        Team.HANWHA -> "DAEJEON"
        Team.SAMSUNG -> "DAEGU"
        Team.LOTTE -> "SAJIK"
        Team.KIA -> "GWANGJU"
        Team.NC -> "CHANGWON"
        Team.NONE -> null
    }
}

private suspend fun postCheckinIfAuthenticated(
    selectedTeam: Team,
    checkinGame: Game?,
) {
    val game = checkinGame ?: return
    val stadiumCode = game.homeTeamId.stadiumCode() ?: return
    val session = SupabaseClientProvider.client.auth.currentSessionOrNull() ?: return
    CheerSignalsLoader.postCheckin(
        accessToken = session.accessToken,
        team = selectedTeam,
        stadiumCode = stadiumCode,
        gameId = game.id,
        latitude = null,
        longitude = null,
        accuracyMeters = null,
        mockLocation = false,
    )
}

@Composable
private fun CheckinInfoCard(selectedTeam: Team) {
    val teamLabel = if (selectedTeam == Team.NONE) "응원팀" else selectedTeam.teamName

    InfoCard(
        icon = Icons.Default.LocationOn,
        title = "구장 체크인 안내",
        subtitle = "오늘 직관 인증은 경기장 근처에서 위치 확인 후 진행돼요",
        body = "$teamLabel 경기 당일 구장 반경 안에 있으면 체크인할 수 있고, 인증 결과는 팀 랭킹에 반영됩니다.",
    )
}

@Composable
private fun RankingInfoCard(selectedTeam: Team) {
    val teamLabel = if (selectedTeam == Team.NONE) "내 팀" else selectedTeam.teamName

    InfoCard(
        icon = Icons.Default.EmojiEvents,
        title = "랭킹 안내",
        subtitle = "팬들의 직관 인증을 모아 주간·시즌 순위를 보여줘요",
        body = "${teamLabel}의 위치를 강조해서 보여주고, iOS와 Android 체크인 기록을 합산해 집계합니다.",
    )
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    body: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.xl,
        color = Gray900,
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            SectionTitle(icon = icon, title = title, subtitle = subtitle)
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Text(text = body, style = AppFont.body, color = Gray300)
        }
    }
}

@Composable
private fun WatchCheerPreviewCard(selectedTeam: Team) {
    val teamTheme = LocalTeamTheme.current
    val teamLabel = if (selectedTeam == Team.NONE) "응원팀" else selectedTeam.teamName

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.xl,
        color = Gray900,
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 96.dp)
                    .clip(AppShapes.xl)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                teamTheme.primary.copy(alpha = 0.95f),
                                teamTheme.primaryDark.copy(alpha = 0.98f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(text = "같이", style = AppFont.microBold, color = Color.White)
                    Text(text = "응원!", style = AppFont.microBold, color = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(AppSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle(
                    icon = Icons.Default.Watch,
                    title = "워치 응원 안내",
                    subtitle = "체크인과 별개로 경기 시작 시 워치에서만 울려요",
                )
                Spacer(modifier = Modifier.height(AppSpacing.md))
                Text(
                    text = "$teamLabel 팬들, 지금 함께 응원해요!",
                    style = AppFont.bodyLgBold,
                    color = Gray100,
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = "예정 시각 18:30 · 강한 햅틱 3회",
                    style = AppFont.caption,
                    color = Gray400,
                )
            }
        }
    }
}

@Composable
private fun MyCheckinSummaryCard(
    enabled: Boolean,
    checkedInToday: Boolean,
    weeklyCount: Int,
    seasonCount: Int,
    lastVenue: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.xl,
        color = Gray900,
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Text(text = "내 직관 기록", style = AppFont.h5Bold, color = Color.White)
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                text = if (enabled) {
                    if (checkedInToday) {
                        val venueText = lastVenue.ifBlank { "오늘 경기장" }
                        "$venueText 체크인 완료. 앱을 다시 켜도 오늘은 완료 상태로 유지돼요"
                    } else {
                        "오늘 체크인하면 주간 랭킹과 내 직관 기록에 바로 반영돼요"
                    }
                } else {
                    "응원팀을 선택하면 체크인 기록을 모을 수 있어요"
                },
                style = AppFont.body,
                color = Gray400,
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SummaryMetric(label = "이번 주", value = "${weeklyCount}회")
                SummaryMetric(label = "시즌", value = "${seasonCount}회")
            }
        }
    }
}

@Composable
private fun RowScope.SummaryMetric(label: String, value: String) {
    Surface(
        shape = AppShapes.lg,
        color = Gray800,
        modifier = Modifier.weight(1f),
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Text(text = label, style = AppFont.tiny, color = Gray500)
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(text = value, style = AppFont.h5Bold, color = Color.White)
        }
    }
}

@Composable
private fun SectionTitle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalTeamTheme.current.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(AppSpacing.sm))
        Column {
            Text(text = title, style = AppFont.h5Bold, color = Color.White)
            Text(text = subtitle, style = AppFont.micro, color = Gray400)
        }
    }
}

@Composable
private fun StatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val teamTheme = LocalTeamTheme.current
    Surface(
        shape = AppShapes.pill,
        color = if (selected) teamTheme.primary.copy(alpha = 0.28f) else Gray800,
        modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Gray300,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(AppSpacing.xs))
            Text(text = text, style = AppFont.tinyBold, color = if (selected) Color.White else Gray300)
        }
    }
}
