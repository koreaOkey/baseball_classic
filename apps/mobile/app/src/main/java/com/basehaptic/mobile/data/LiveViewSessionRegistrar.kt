package com.basehaptic.mobile.data

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.push.BaseHapticMessagingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

object LiveViewSessionRegistrar {
    private const val TAG = "LiveViewSession"
    private const val PREFS_NAME = "live_view_session_prefs"
    private const val KEY_INSTALL_ID = "install_id"

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()

    fun installId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_INSTALL_ID, null).orEmpty()
        if (existing.isNotBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, created).apply()
        return created
    }

    suspend fun setActive(
        context: Context,
        gameId: String,
        surface: String,
        active: Boolean,
        myTeam: String,
    ) = withContext(Dispatchers.IO) {
        if (gameId.isBlank()) return@withContext
        val appCtx = context.applicationContext
        val token = appCtx.getSharedPreferences(BaseHapticMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(BaseHapticMessagingService.KEY_FCM_TOKEN, null)
            .orEmpty()
        val userKey = installId(appCtx)
        val tokenKey = when (surface) {
            "wearos" -> "wearos:$userKey"
            else -> token.ifBlank { "android:$userKey" }
        }
        val body = JSONObject()
            .put("game_id", gameId)
            .put("user_key", userKey)
            .put("surface", surface)
            .put("token_key", tokenKey)
            .put("my_team", myTeam)
            .put("active", active)
            .toString()
            .toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/live-view-sessions")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "update failed surface=$surface active=$active code=${response.code}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "update error surface=$surface active=$active", t)
        }
    }
}
