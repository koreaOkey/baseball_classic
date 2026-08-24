package com.basehaptic.mobile.venting

import android.content.Context
import android.util.Log
import com.basehaptic.mobile.BuildConfig
import com.basehaptic.mobile.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
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
 * 분풀이 6.1 지표 리포터 — POST /venting/events (LiveViewSessionRegistrar 패턴).
 *
 * 완전 베스트에포트: UI 를 절대 블로킹하지 않으며(자체 IO 스코프 fire-and-forget),
 * 실패는 조용히 삼킨다. 서버가 비활성이면 {ok:false} 를 돌려주지만 클라이언트는 무시한다.
 *
 * event_type: room_enter · watch_room_enter · destroy_complete · retry_prompt_shown · retry_ad_start · retry_ad_complete.
 */
object VentingEventReporter {
    private const val TAG = "VentingEventReporter"

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * [context] 는 호출부 시그니처 일관성용(향후 user_id 등 확장 대비) — 현재 전송에는 사용하지 않는다.
     * fire-and-forget: 반환 즉시 종료하며 결과를 기다리지 않는다.
     */
    fun report(
        @Suppress("UNUSED_PARAMETER") context: Context,
        eventType: String,
        team: String,
        entrySource: String,
        gameId: String?
    ) {
        scope.launch {
            try {
                val payload = JSONObject()
                    .put("event_type", eventType)
                    .put("team", team)
                    .put("entry_source", entrySource)
                    .put("platform", "android")
                if (!gameId.isNullOrBlank()) payload.put("game_id", gameId)
                val body = payload.toString().toRequestBody(jsonMedia)
                val builder = Request.Builder()
                    .url("${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/venting/events")
                    .post(body)
                // 로그인 상태면 토큰 첨부 → 서버가 user_id 를 추출해 순 사용자 집계 가능.
                // 비로그인/실패 시 익명 이벤트로 전송(비차단).
                runCatching { SupabaseClientProvider.client.auth.currentSessionOrNull()?.accessToken }
                    .getOrNull()
                    ?.let { builder.addHeader("Authorization", "Bearer $it") }
                val request = builder.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "report failed eventType=$eventType code=${response.code}")
                    }
                }
            } catch (t: Throwable) {
                // best-effort: 절대 UI 를 블로킹하지 않고, 실패는 조용히 삼킨다.
                Log.w(TAG, "report error eventType=$eventType", t)
            }
        }
    }
}
