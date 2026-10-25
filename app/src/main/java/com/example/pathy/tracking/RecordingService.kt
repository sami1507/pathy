package com.example.pathy.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.pathy.R
import com.example.pathy.TrackingActivity
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.track.GpsFix
import com.example.pathy.core.track.RecorderState
import com.example.pathy.core.track.SportType
import com.example.pathy.core.track.TrackRecorderCore
import com.example.pathy.data.DatabaseProvider
import com.example.pathy.i18n.AppLocale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Records the route in a foreground service, so recording continues with the screen off, the app in
 * the background or the phone in a pocket. The old recorder lived inside the screen: locking the
 * phone throttled or stopped it, and a killed process lost the whole outing.
 *
 * Every accepted point is written to the database as it arrives. If the system kills the service it
 * restarts (START_STICKY) and rebuilds the recorder from the database.
 *
 * Everything that changes state goes through one queue, in order: commands from the screen and GPS
 * fixes never run at the same time.
 */
class RecordingService : Service() {

    private sealed interface Event {
        data class Start(val sport: SportType) : Event
        object Restore : Event
        object Pause : Event
        object Resume : Event
        object Stop : Event
        data class Fix(val fix: GpsFix) : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val events = Channel<Event>(Channel.UNLIMITED)

    private lateinit var repository: RecordingRepository
    private lateinit var fused: FusedLocationProviderClient
    private var core: TrackRecorderCore? = null
    private var recordingId: String? = null
    private var seq = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var locationRunning = false
    private var requestedFast = true
    private var lastNotificationMs = 0L
    private var lastNotifiedState: RecordingState? = null
    private var lastSavedState: RecorderState? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) events.trySend(Event.Fix(location.toFix()))
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        repository = RecordingRepository(DatabaseProvider.getDatabase(this).recordingDao())
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        isRunning = true
        scope.launch { for (event in events) handle(event) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A foreground service must call startForeground soon after it is started.
        if (!startForegroundSafely(currentNotification())) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> events.trySend(Event.Start(sportOf(intent)))
            ACTION_PAUSE -> events.trySend(Event.Pause)
            ACTION_RESUME -> events.trySend(Event.Resume)
            ACTION_STOP -> events.trySend(Event.Stop)
            // ACTION_RESTORE, or the system restarting the service after killing it (null intent).
            else -> events.trySend(Event.Restore)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        runCatching { fused.removeLocationUpdates(locationCallback) }
        releaseWakeLock()
        events.close()
        scope.cancel()
        super.onDestroy()
    }

    // ---- events -----------------------------------------------------------------------------

    private suspend fun handle(event: Event) {
        try {
            when (event) {
                is Event.Start -> start(event.sport)
                Event.Restore -> restore()
                Event.Pause -> pause()
                Event.Resume -> resume()
                Event.Stop -> stop()
                is Event.Fix -> onFix(event.fix)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // One bad event must not end the recording. The point is lost, the outing is not.
            Log.e(TAG, "Recording event failed: $event", e)
        }
    }

    private suspend fun start(sport: SportType) {
        if (core != null) return publish()
        // An outing already in progress (the app was killed) is continued, never replaced.
        if (repository.activeId() != null) return restore()
        val now = System.currentTimeMillis()
        val entity = repository.begin(sport, now)
        recordingId = entity.id
        seq = 0
        core = TrackRecorderCore(sport).also { it.start(now) }
        acquireWakeLock()
        startLocationUpdates(fast = true)
        publish()
    }

    private suspend fun restore() {
        if (core != null) return publish()
        val loaded = repository.loadActive()
        if (loaded == null) {
            RecordingHub.reset()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val saved = runCatching { RecorderState.valueOf(loaded.recording.recorderState) }.getOrDefault(RecorderState.RECORDING)
        val restored = TrackRecorderCore(loaded.sport)
        restored.restoreClock(
            loaded.recording.startedAtMs,
            loaded.recording.pausedTotalMs,
            if (saved == RecorderState.PAUSED) RecorderState.PAUSED else RecorderState.RECORDING,
            loaded.recording.pauseStartedMs
        )
        loaded.points.forEach { restored.replay(it) }
        core = restored
        recordingId = loaded.recording.id
        seq = repository.nextSeq(loaded.recording.id)
        acquireWakeLock()
        if (restored.state != RecorderState.PAUSED) startLocationUpdates(fast = true)
        publish()
    }

    private suspend fun pause() {
        val c = core ?: return
        c.pause(System.currentTimeMillis())
        stopLocationUpdates()
        saveClock(c)
        publish()
    }

    private suspend fun resume() {
        val c = core ?: return
        c.resume(System.currentTimeMillis())
        startLocationUpdates(fast = true)
        saveClock(c)
        publish()
    }

    private suspend fun stop() {
        val c = core
        var id = recordingId
        if (c == null) {
            // Asked to stop after a restart: finish whatever the database says is in progress.
            val loaded = repository.loadActive()
            if (loaded != null) {
                val rebuilt = TrackRecorderCore(loaded.sport)
                val savedState = runCatching { RecorderState.valueOf(loaded.recording.recorderState) }.getOrDefault(RecorderState.RECORDING)
                rebuilt.restoreClock(
                    loaded.recording.startedAtMs,
                    loaded.recording.pausedTotalMs,
                    if (savedState == RecorderState.PAUSED) RecorderState.PAUSED else RecorderState.RECORDING,
                    loaded.recording.pauseStartedMs
                )
                loaded.points.forEach { rebuilt.replay(it) }
                val now = System.currentTimeMillis()
                repository.finish(loaded.recording.id, rebuilt.stop(now), now)
                id = loaded.recording.id
            }
        } else if (id != null) {
            val now = System.currentTimeMillis()
            repository.finish(id, c.stop(now), now)
        }
        stopLocationUpdates()
        releaseWakeLock()
        core = null
        recordingId = null
        RecordingHub.reset()
        if (id != null) {
            UploadScheduler.enqueue(applicationContext)
            RecordingHub.publishFinished(id)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun onFix(fix: GpsFix) {
        val c = core ?: return
        val id = recordingId ?: return
        val before = c.state
        val point = c.onFix(fix)
        if (point != null) {
            repository.append(id, seq++, point)
            RecordingHub.publishPosition(LatLon(point.lat, point.lon))
        }
        if (c.state != before) {
            saveClock(c)
            // While auto-paused the position matters only to notice that the person moves on again.
            startLocationUpdates(fast = c.state != RecorderState.AUTO_PAUSED)
        }
        publish()
    }

    private suspend fun saveClock(c: TrackRecorderCore) {
        val id = recordingId ?: return
        if (lastSavedState == c.state) return
        lastSavedState = c.state
        repository.saveClock(id, c.state, c.pausedMs, c.pauseStartedAtMs)
    }

    // ---- state and notification ---------------------------------------------------------------

    private fun publish() {
        val c = core
        if (c == null) {
            RecordingHub.reset()
            return
        }
        val now = System.currentTimeMillis()
        val stats = c.stats(now).toActivityStats()
        val state = c.state.toUiState()
        RecordingHub.publish(state, stats, recordingId)
        // The numbers refresh every few seconds, but a change of state (paused, resumed) shows at once.
        if (now - lastNotificationMs > 4_000 || state != lastNotifiedState) {
            lastNotificationMs = now
            lastNotifiedState = state
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state, stats))
        }
    }

    /**
     * The notification for what is happening now. Every command starts the service again, and each start
     * must show the real state: showing an empty "recording 0.00 km" there made a paused outing look
     * as if it had restarted.
     */
    private fun currentNotification(): Notification {
        val c = core ?: return buildNotification(RecordingState.Recording(), ActivityStats())
        return buildNotification(c.state.toUiState(), c.stats(System.currentTimeMillis()).toActivityStats())
    }

    private fun buildNotification(state: RecordingState, stats: ActivityStats): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, TrackingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val paused = state is RecordingState.Paused
        val autoPaused = state is RecordingState.Recording && state.autoPaused
        val km = getString(R.string.unit_km, String.format(java.util.Locale.getDefault(), "%.2f", stats.distance / 1000f))
        val time = DateUtils.formatElapsedTime(stats.duration / 1000)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                getString(
                    when {
                        paused -> R.string.rec_notification_paused
                        autoPaused -> R.string.rec_notification_auto_paused
                        else -> R.string.rec_notification_title
                    }
                )
            )
            .setContentText(getString(R.string.rec_notification_text, km, time))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (paused) builder.addAction(0, getString(R.string.rec_action_resume), servicePending(ACTION_RESUME))
        else builder.addAction(0, getString(R.string.rec_action_pause), servicePending(ACTION_PAUSE))
        builder.addAction(0, getString(R.string.rec_action_finish), servicePending(ACTION_STOP))
        return builder.build()
    }

    private fun servicePending(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.rec_channel_name), NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.rec_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Android 12+ refuses to start a foreground service from the background; do not crash on that. */
    private fun startForegroundSafely(notification: Notification): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (e: Exception) {
        Log.e(TAG, "Could not start the recording service in the foreground", e)
        false
    }

    // ---- location ------------------------------------------------------------------------------

    @SuppressLint("MissingPermission") // The screen asks for the permission before it starts the service.
    private fun startLocationUpdates(fast: Boolean) {
        if (locationRunning && requestedFast == fast) return
        val interval = if (fast) 2_000L else 8_000L
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(if (fast) 1_000L else 4_000L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationRunning = true
            requestedFast = fast
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission is missing; recording cannot continue", e)
        }
    }

    private fun stopLocationUpdates() {
        runCatching { fused.removeLocationUpdates(locationCallback) }
        locationRunning = false
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pathy:recording").apply {
            acquire(MAX_RECORDING_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun Location.toFix() = GpsFix(
        lat = latitude,
        lon = longitude,
        altitudeM = if (hasAltitude()) altitude else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        speedMps = if (hasSpeed()) speed else null,
        timeMs = time
    )

    private fun sportOf(intent: Intent): SportType =
        runCatching { SportType.valueOf(intent.getStringExtra(EXTRA_SPORT) ?: "") }.getOrDefault(SportType.HIKING)

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 4101
        /** A recording longer than this has certainly been forgotten; the wake lock lets go. */
        private const val MAX_RECORDING_MS = 16 * 60 * 60 * 1000L

        const val ACTION_START = "com.example.pathy.recording.START"
        const val ACTION_PAUSE = "com.example.pathy.recording.PAUSE"
        const val ACTION_RESUME = "com.example.pathy.recording.RESUME"
        const val ACTION_STOP = "com.example.pathy.recording.STOP"
        const val ACTION_RESTORE = "com.example.pathy.recording.RESTORE"
        const val EXTRA_SPORT = "sport"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
