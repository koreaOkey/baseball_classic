package com.basehaptic.mobile.auth

import android.os.Bundle
import androidx.activity.ComponentActivity

class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Supabase SDK handles the OAuth callback token exchange
        // automatically via the deep link. Just finish and return to MainActivity.
        finish()
    }
}
