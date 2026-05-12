package run.koto.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import run.koto.MainActivity
import run.koto.R

class PushReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "koto_messages"
        const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
        const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
        const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MESSAGE -> handleMessage(context, intent)
            ACTION_NEW_ENDPOINT -> handleNewEndpoint(context, intent)
            ACTION_UNREGISTERED -> handleUnregistered(context)
        }
    }

    private fun handleMessage(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: return
        val json = runCatching { JSONObject(message) }.getOrNull() ?: return
        val convId = json.optString("conv_id", "")

        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("conv_id", convId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, convId.hashCode(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_koto_splash)
            .setContentTitle("Koto")
            .setContentText("Новое сообщение")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        // Check POST_NOTIFICATIONS permission on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        NotificationManagerCompat.from(context).notify(convId.hashCode(), notification)
    }

    private fun handleNewEndpoint(context: Context, intent: Intent) {
        val endpoint = intent.getStringExtra("endpoint") ?: return
        // Store endpoint — will be sent to server on next app open
        context.getSharedPreferences("push", Context.MODE_PRIVATE)
            .edit()
            .putString("endpoint", endpoint)
            .putBoolean("needs_register", true)
            .apply()
    }

    private fun handleUnregistered(context: Context) {
        context.getSharedPreferences("push", Context.MODE_PRIVATE)
            .edit()
            .remove("endpoint")
            .putBoolean("needs_register", false)
            .apply()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Уведомления о новых сообщениях"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
