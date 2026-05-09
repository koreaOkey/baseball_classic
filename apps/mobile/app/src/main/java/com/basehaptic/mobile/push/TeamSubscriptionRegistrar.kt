package com.basehaptic.mobile.push

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 응원팀 단위 글로벌 푸시 구독을 백엔드와 동기화한다.
 *
 * - 등록 트리거: FCM 토큰 갱신, 응원팀 변경, 앱 시작.
 * - 등록 페이로드: token + my_team(예: "DOOSAN") + platform="android".
 * - 응원팀이 NONE/blank 면 unregister 한다.
 */
object TeamSubscriptionRegistrar {
    private const val TAG = "TeamSubscription"
    private const val PREFS_NAME = "team_subscription_prefs"
    private const val KEY_LAST_REGISTERED_TOKEN = "last_token"
    private const val KEY_LAST_REGISTERED_TEAM = "last_team"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()

    /**
     * 현재 저장된 token + my_team 을 백엔드에 동기화. 직전에 동일한 값으로
     * 등록한 적 있으면 호출 생략 (네트워크 비용 절감).
     */
    fun syncIfNeeded(context: Context) {
        val appCtx = context.applicationContext
        val token = appCtx.getSharedPreferences(BaseHapticMessagingService.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(BaseHapticMessagingService.KEY_FCM_TOKEN, null)
            .orEmpty()
        val team = readSelectedTeam(appCtx)

        if (token.isBlank()) {
            Log.i(TAG, "skip sync: no FCM token yet")
            return
        }

        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastToken = prefs.getString(KEY_LAST_REGISTERED_TOKEN, null)
        val lastTeam = prefs.getString(KEY_LAST_REGISTERED_TEAM, null)

        if (team.isBlank()) {
            Log.i(TAG, "skip sync: no team selected (clearing registration if any)")
            if (!lastToken.isNullOrBlank()) {
                unregisterAsync(appCtx, lastToken)
            }
            return
        }

        if (token == lastToken && team == lastTeam) {
            Log.i(TAG, "skip sync: already registered team=$team token=${token.take(8)}...")
            return
        }

        Log.i(TAG, "syncing team=$team token=${token.take(8)}...")
        registerAsync(appCtx, token, team)
    }

    private fun registerAsync(context: Context, token: String, team: String) {
        scope.launch {
            val body = JSONObject()
                .put("token", token)
                .put("my_team", team)
                .put("platform", "android")
                .put("is_sandbox", false)
                .toString()
                .toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/team-subscriptions")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_LAST_REGISTERED_TOKEN, token)
                            .putString(KEY_LAST_REGISTERED_TEAM, team)
                            .apply()
                        Log.i(TAG, "registered team=$team token=${token.take(8)}...")
                    } else {
                        Log.w(TAG, "register failed: code=${response.code}")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "register error", t)
            }
        }
    }

    private fun unregisterAsync(context: Context, token: String) {
        scope.launch {
            val request = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/team-subscriptions/$token")
                .delete()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .remove(KEY_LAST_REGISTERED_TOKEN)
                            .remove(KEY_LAST_REGISTERED_TEAM)
                            .apply()
                        Log.i(TAG, "unregistered token=${token.take(8)}...")
                    } else {
                        Log.w(TAG, "unregister failed: code=${response.code}")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "unregister error", t)
            }
        }
    }

    private fun readSelectedTeam(context: Context): String {
        return context.getSharedPreferences("basehaptic_user_prefs", Context.MODE_PRIVATE)
            .getString("selected_team", null)
            .orEmpty()
            .let { if (it == "NONE") "" else it }
    }
}
