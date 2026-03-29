package com.basehaptic.mobile.auth

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
}
