package run.koto

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import run.koto.data.remote.api.NotificationApi
import run.koto.data.remote.api.RegisterDeviceRequest
import run.koto.security.AppLockManager
import run.koto.security.SecurityManager
import javax.inject.Inject

@HiltAndroidApp
class KotoApp : Application() {

    @Inject lateinit var appLockManager : AppLockManager
    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var notificationApi: NotificationApi

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Run security checks as early as possible — before any sensitive code path
        securityManager.check()
        appLockManager.init()
        registerPendingPushEndpoint()
    }

    /**
     * If a UnifiedPush endpoint was received while the app was not running,
     * send it to the backend now.
     */
    private fun registerPendingPushEndpoint() {
        val prefs = getSharedPreferences("push", MODE_PRIVATE)
        val needsRegister = prefs.getBoolean("needs_register", false)
        val endpoint = prefs.getString("endpoint", null)

        if (needsRegister && !endpoint.isNullOrEmpty()) {
            appScope.launch {
                runCatching {
                    notificationApi.registerDevice(RegisterDeviceRequest(token = endpoint))
                }.onSuccess {
                    prefs.edit().putBoolean("needs_register", false).apply()
                }
                // On failure the flag stays true and we retry on next app start
            }
        }
    }
}
