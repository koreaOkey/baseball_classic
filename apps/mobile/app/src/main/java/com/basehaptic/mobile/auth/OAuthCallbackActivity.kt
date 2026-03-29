package com.basehaptic.mobile.auth

import android.os.Bundle
import androidx.activity.ComponentActivity

class OAuthCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
