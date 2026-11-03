package com.safemode.llconnect

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safemode.llconnect.data.notifications.Notifications
import com.safemode.llconnect.data.settings.ConnectionConfig
import com.safemode.llconnect.data.settings.ThemePreference
import com.safemode.llconnect.ui.LLConnectRoot
import com.safemode.llconnect.ui.theme.LLConnectTheme

class MainActivity : ComponentActivity() {
    // Route hint from a tapped notification, consumed once by LLConnectRoot.
    private val navTarget = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh launch. A recreated activity (rotation, or restored after the process
        // was killed) gets the same intent back, and acting on it again would jump to the
        // notification's screen after the user had moved on. The same goes for reopening the app
        // from recents, which replays the launching intent.
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) {
            navTarget.value = intent?.getStringExtra(Notifications.EXTRA_NAV_TARGET)
        }
        intent?.removeExtra(Notifications.EXTRA_NAV_TARGET)
        enableEdgeToEdge()
        setContent {
            val config by Graph.settingsRepository.config
                .collectAsStateWithLifecycle(initialValue = ConnectionConfig())
            val preference = config.themePreference

            val darkTheme = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK, ThemePreference.MIDNIGHT -> true
            }

            // Re-apply edge-to-edge whenever the effective darkness changes, so the system-bar
            // icon colours track the chosen theme rather than only the device setting.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_SCRIM,
                        DARK_SCRIM,
                    ) { darkTheme },
                )
                onDispose {}
            }

            LLConnectTheme(themePreference = preference) {
                LLConnectRoot(
                    navTarget = navTarget.value,
                    onNavTargetHandled = { navTarget.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_NAV_TARGET)?.let { navTarget.value = it }
        intent.removeExtra(Notifications.EXTRA_NAV_TARGET)
    }

    private companion object {
        // Matches the scrims androidx uses for three-button navigation bars.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
