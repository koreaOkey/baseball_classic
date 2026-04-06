package com.basehaptic.mobile.auth

import android.content.Context
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.providers.Kakao
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.basehaptic.mobile.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(
        val userId: String,
        val email: String?,
        val provider: String
    ) : AuthState()
}

object AuthManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val supabase = SupabaseClientProvider.client

    fun initialize() {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                _authState.value = when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        AuthState.LoggedIn(
                            userId = user?.id ?: "",
                            email = user?.email,
                            provider = user?.appMetadata?.get("provider")?.toString()?.removeSurrounding("\"") ?: "unknown"
                        )
                    }
                    is SessionStatus.NotAuthenticated -> AuthState.LoggedOut
                    is SessionStatus.Initializing -> AuthState.Loading
                    else -> AuthState.LoggedOut
                }
            }
        }
    }

    suspend fun signInWithKakao() {
        supabase.auth.signInWith(Kakao)
    }

    suspend fun signOut() {
        supabase.auth.signOut()
    }

    suspend fun deleteAccount(context: Context): Boolean {
        val session = supabase.auth.currentSessionOrNull() ?: return false
        val backendUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

        val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val url = URL("$backendUrl/account")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            try {
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        }

        if (result) {
            // 유저가 이미 삭제되었으므로 signOut 실패해도 무시
            try { supabase.auth.signOut() } catch (_: Exception) {}
            _authState.value = AuthState.LoggedOut
            context.getSharedPreferences("basehaptic_user_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()
        }

        return result
    }
}
