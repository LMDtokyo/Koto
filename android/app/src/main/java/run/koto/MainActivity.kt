package run.koto

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import run.koto.data.prefs.AccountPrefs
import run.koto.data.prefs.SettingsPrefs
import run.koto.network.ConnectivityManager
import run.koto.security.AppLockManager
import run.koto.ui.navigation.KotoNavGraph
import run.koto.ui.navigation.Screen
import run.koto.ui.screens.lock.LockScreen
import run.koto.ui.theme.KotoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var accountPrefs       : AccountPrefs
    @Inject lateinit var settingsPrefs      : SettingsPrefs
    @Inject lateinit var appLockManager     : AppLockManager
    @Inject lateinit var connectivityManager: ConnectivityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()                       // must be before super.onCreate() for correct window setup

        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)

        val isRegistered = runBlocking { accountPrefs.getAccountId() != null }

        // ── FLAG_SECURE: apply synchronously before setContent ───────────────────
        // Must be set before the first frame is drawn, so screenshots are blocked
        // from the very first render — not after async LaunchedEffect fires.
        val initialHide = runBlocking { settingsPrefs.load().hideFromRecents }
        if (initialHide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        keepSplash = false

        val startDestination = if (isRegistered) Screen.Conversations.route
                               else Screen.Onboarding.route

        setContent {
            val isDarkMode by settingsPrefs.darkModeFlow
                .collectAsState(initial = true)

            KotoTheme(darkTheme = isDarkMode) {
                // SideEffect runs synchronously after every recomposition, so the
                // flag is always in sync with the setting with no async gap.
                val hideFromRecents by settingsPrefs.settingsFlow()
                    .map { it.hideFromRecents }
                    .collectAsState(initial = initialHide)

                SideEffect {
                    if (hideFromRecents) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }

                // ── Screen lock ──────────────────────────────────────────────────
                val isLocked by appLockManager.isLocked.collectAsState()

                if (isLocked) {
                    LockScreen(onUnlocked = { appLockManager.unlock() })
                } else {
                    KotoNavGraph(
                        connectivityManager = connectivityManager,
                        accountPrefs        = accountPrefs,
                        startDestination    = startDestination,
                    )
                }
            }
        }
    }
}
