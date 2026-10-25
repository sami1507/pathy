package com.example.pathy.walkietalkie

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.pathy.R
import com.example.pathy.WalkieTalkieActivity
import com.example.pathy.i18n.AppLocale

/**
 * Keeps a walkie-talkie call alive while the screen is off or another app is in front.
 *
 * Android stops an app's microphone and network use shortly after it leaves the screen unless a
 * foreground service with a notification is running. The service itself does no audio work: the
 * connections live in [TransportCoordinator]. (The earlier version recorded the microphone to a file in
 * the background, which nothing used and the person was never told about; that is gone.)
 */
class WalkieTalkieService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            true
        } catch (e: Exception) {
            // Missing microphone permission, or the system refused a background start: no call without it.
            Log.w(TAG, "Could not run the call in the foreground", e)
            false
        }
        if (!started || !TransportCoordinator.isActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A killed process also ends the call (its connections are gone), so do not restart empty.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, WalkieTalkieActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.walkie_service_title))
            .setContentText(getString(R.string.walkie_service_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.walkie_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WalkieTalkieService"
        private const val CHANNEL_ID = "walkie_channel"
        private const val NOTIFICATION_ID = 4102

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WalkieTalkieService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WalkieTalkieService::class.java))
        }
    }
}
