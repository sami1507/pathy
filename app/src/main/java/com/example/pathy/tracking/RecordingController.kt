package com.example.pathy.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.pathy.core.track.SportType

/**
 * What the screens call to drive the recording. Each call is a command to [RecordingService]; the
 * result comes back through [RecordingHub]. The screens never hold the recorder themselves.
 */
object RecordingController {

    fun start(context: Context, sport: SportType = SportType.HIKING) =
        send(context, RecordingService.ACTION_START, sport)

    fun pause(context: Context) = send(context, RecordingService.ACTION_PAUSE)

    fun resume(context: Context) = send(context, RecordingService.ACTION_RESUME)

    fun stop(context: Context) = send(context, RecordingService.ACTION_STOP)

    /** Brings back an outing that was in progress when the app was closed or killed. */
    fun restore(context: Context) = send(context, RecordingService.ACTION_RESTORE)

    private fun send(context: Context, action: String, sport: SportType? = null) {
        val intent = Intent(context, RecordingService::class.java).setAction(action)
        sport?.let { intent.putExtra(RecordingService.EXTRA_SPORT, it.name) }
        // Always a foreground start: the service promotes itself within seconds, and this also works
        // when the service was killed and is being started again.
        ContextCompat.startForegroundService(context, intent)
    }
}
