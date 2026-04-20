package com.basehaptic.mobile.data

import com.basehaptic.mobile.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

object ThemeRepository {
    private val client = SupabaseClientProvider.client

    // MARK: - Fetch (로그인 시 복원)

    suspend fun fetchUnlockedThemeIds(): Set<String> {
        val userId = currentUserId()

        val rows = client.from("user_theme_purchases")
            .select { filter { eq("user_id", userId); eq("status", "PAID") } }
            .decodeList<PurchaseRow>()

        return rows.map { it.theme_id }.toMutableSet().apply { add("default") }
    }

    suspend fun fetchUserSettings(): UserSettingsResult {
        val userId = currentUserId()

        val rows = client.from("user_settings")
            .select { filter { eq("user_id", userId) } }
            .decodeList<SettingsRow>()

        val row = rows.firstOrNull()
        return UserSettingsResult(
            activeThemeId = row?.active_theme_id,
            selectedTeam = row?.selected_team
        )
    }

    // MARK: - Save (잠금해제 / 적용)

    suspend fun saveUnlock(themeId: String) {
        val userId = currentUserId()

        val row = InsertPurchaseRow(
            user_id = userId,
            theme_id = themeId,
            order_id = "ad_reward_${userId}_${themeId}_${System.currentTimeMillis() / 1000}",
            status = "PAID"
        )

        client.from("user_theme_purchases").insert(row)
    }

    suspend fun saveActiveTheme(themeId: String?) {
        val userId = currentUserId()

        val row = UpsertActiveThemeRow(
            user_id = userId,
            active_theme_id = themeId
        )

        client.from("user_settings").upsert(row)
    }

    suspend fun saveSelectedTeam(team: String) {
        val userId = currentUserId()

        val row = UpsertSelectedTeamRow(
            user_id = userId,
            selected_team = team
        )

        client.from("user_settings").upsert(row)
    }

    // MARK: - Helper

    private suspend fun currentUserId(): String {
        val session = client.auth.currentSessionOrNull()
            ?: throw IllegalStateException("Not authenticated")
        return session.user?.id ?: throw IllegalStateException("No user ID")
    }

    // MARK: - DTOs

    @Serializable
    private data class PurchaseRow(val theme_id: String)

    @Serializable
    private data class SettingsRow(
        val active_theme_id: String? = null,
        val selected_team: String? = null
    )

    @Serializable
    private data class InsertPurchaseRow(
        val user_id: String,
        val theme_id: String,
        val order_id: String,
        val status: String
    )

    @Serializable
    private data class UpsertActiveThemeRow(
        val user_id: String,
        val active_theme_id: String?
    )

    @Serializable
    private data class UpsertSelectedTeamRow(
        val user_id: String,
        val selected_team: String
    )

    data class UserSettingsResult(
        val activeThemeId: String?,
        val selectedTeam: String?
    )
}
