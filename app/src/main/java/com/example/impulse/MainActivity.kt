package com.example.impulse

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.example.impulse.data.ThemeMode
import com.example.impulse.ui.LifeLoopRoot
import com.example.impulse.ui.theme.ImpulseTheme

class MainActivity : ComponentActivity() {
    /** Pending deep link (from shortcuts or notifications), consumed by the nav host. */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LifeLoopApp).container
        deepLink.value = intent?.data?.toString()
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val dark = when (settings?.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            ImpulseTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor == true) {
                LifeLoopRoot(
                    settings = settings,
                    deepLink = deepLink.value,
                    onDeepLinkHandled = { deepLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { deepLink.value = it.toString() }
    }

    override fun onResume() {
        super.onResume()
        // Coming back to the app is a good moment to roll occurrences forward and re-check alarms.
        (application as LifeLoopApp).container.reminders.requestReconcile()
    }
}
