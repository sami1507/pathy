package com.example.pathy.tracking

import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.track.RecorderState
import com.example.pathy.core.track.TrackStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the recording screen shows. */
sealed class RecordingState {
    object Idle : RecordingState()

    /** Recording. [autoPaused] is true while the recorder has paused itself because the person stopped. */
    data class Recording(val autoPaused: Boolean = false) : RecordingState()

    object Paused : RecordingState()
}

/** Live numbers for the recording screen. */
data class ActivityStats(
    val distance: Float = 0f, // metres
    val duration: Long = 0, // milliseconds
    val elevationGain: Float = 0f, // metres
    val averageSpeed: Float = 0f, // km/h
    val currentSpeed: Float = 0f, // km/h
    val averagePace: Float = 0f, // min/km
    val currentElevation: Float = 0f, // metres
    val maxSpeed: Float = 0f, // km/h
    val calories: Int = 0
)

internal fun TrackStats.toActivityStats() = ActivityStats(
    distance = distanceM.toFloat(),
    duration = elapsedMs,
    elevationGain = elevationGainM.toFloat(),
    averageSpeed = averageSpeedKmh.toFloat(),
    currentSpeed = currentSpeedKmh.toFloat(),
    averagePace = paceMinPerKm.toFloat(),
    currentElevation = (currentElevationM ?: 0.0).toFloat(),
    maxSpeed = maxSpeedKmh.toFloat(),
    calories = calories
)

internal fun RecorderState.toUiState(): RecordingState = when (this) {
    RecorderState.IDLE, RecorderState.FINISHED -> RecordingState.Idle
    RecorderState.RECORDING -> RecordingState.Recording()
    RecorderState.AUTO_PAUSED -> RecordingState.Recording(autoPaused = true)
    RecorderState.PAUSED -> RecordingState.Paused
}

/**
 * The live state of the recording, shared between the foreground service (which writes it) and the
 * screens (which read it). Only the service changes it; the screens send commands through
 * [RecordingController].
 */
object RecordingHub {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(ActivityStats())
    val stats: StateFlow<ActivityStats> = _stats.asStateFlow()

    private val _position = MutableStateFlow<LatLon?>(null)
    /** The latest accepted position, for showing "you are here". */
    val position: StateFlow<LatLon?> = _position.asStateFlow()

    private val _recordingId = MutableStateFlow<String?>(null)
    /** The id of the recording in progress, or null when idle. */
    val recordingId: StateFlow<String?> = _recordingId.asStateFlow()

    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Emits the id of a recording each time one is finished and saved. */
    val finished: SharedFlow<String> = _finished.asSharedFlow()

    internal fun publish(state: RecordingState, stats: ActivityStats, id: String?) {
        _state.value = state
        _stats.value = stats
        _recordingId.value = id
    }

    internal fun publishPosition(position: LatLon?) {
        _position.value = position
    }

    internal fun publishFinished(id: String) {
        _finished.tryEmit(id)
    }

    internal fun reset() {
        _state.value = RecordingState.Idle
        _stats.value = ActivityStats()
        _position.value = null
        _recordingId.value = null
    }
}
