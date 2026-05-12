package run.koto.ui.screens.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

/**
 * On API 30+ (Android 11): allow both biometric AND device credential (PIN/pattern/password).
 * On API 26-29:            DEVICE_CREDENTIAL as an allowed authenticator is unsupported;
 *                          use biometric only with a cancel button (user can retry).
 */
private val ALLOWED_AUTH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    BIOMETRIC_STRONG or DEVICE_CREDENTIAL
} else {
    BIOMETRIC_STRONG or BIOMETRIC_WEAK
}

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context  = LocalContext.current
    val activity = context as FragmentActivity

    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun launchBiometric() {
        errorMsg = null

        // If no suitable auth is enrolled / available, just unlock automatically.
        val canAuth = BiometricManager.from(context).canAuthenticate(ALLOWED_AUTH)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnlocked()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // ERROR_USER_CANCELED / ERROR_NEGATIVE_BUTTON: user dismissed,
                    // show the retry button but don't display an error message.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        errorMsg = errString.toString()
                    }
                }

                override fun onAuthenticationFailed() {
                    // Individual attempt failed (e.g. wrong fingerprint) — system
                    // already shows feedback; no need to duplicate it here.
                }
            },
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Koto")
            .setSubtitle("Authenticate to continue")
            .setAllowedAuthenticators(ALLOWED_AUTH)

        // API 26-29: DEVICE_CREDENTIAL not supported as authenticator type,
        // so we must provide a negative button (acts as "cancel / try again").
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setNegativeButtonText("Отмена")
        }

        prompt.authenticate(builder.build())
    }

    // Trigger prompt automatically as soon as the lock screen appears.
    LaunchedEffect(Unit) { launchBiometric() }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "Koto заблокирован",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (errorMsg != null) {
                Text(
                    text  = errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(onClick = ::launchBiometric) {
                Text("Разблокировать")
            }
        }
    }
}
