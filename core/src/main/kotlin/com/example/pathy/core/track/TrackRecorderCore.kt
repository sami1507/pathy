package com.example.pathy.core.track

import com.example.pathy.core.geo.Geo
import kotlin.math.max
import kotlin.math.min

/** A point that made it into the recorded track. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?,
    val timeMs: Long,
    val accuracyM: Float?
)

enum class RecorderState { IDLE, RECORDING, PAUSED, AUTO_PAUSED, FINISHED }

data class TrackStats(
    val distanceM: Double = 0.0,
    /** Wall-clock time since the start, minus manual pauses. */
    val elapsedMs: Long = 0,
    /** Time actually spent moving. */
    val movingMs: Long = 0,
    val elevationGainM: Double = 0.0,
    val elevationLossM: Double = 0.0,
    val currentElevationM: Double? = null,
    val minElevationM: Double? = null,
    val maxElevationM: Double? = null,
    /** Average over the moving time, km/h. */
    val averageSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    /** Minutes per kilometre over the moving time; 0 when not moving yet. */
    val paceMinPerKm: Double = 0.0,
    val calories: Int = 0
)

/**
 * The recording brain, with no Android types: feed it GPS fixes and pause/resume commands, read the
 * cleaned track and live statistics. The foreground service owns one of these and saves every
 * accepted point to disk, so a process kill loses nothing.
 */
class TrackRecorderCore(
    val sport: SportType = SportType.HIKING,
    private val weightKg: Double = 70.0,
    private val filter: TrackFilter = TrackFilter(sport),
    /** How long below the stopped speed before the recorder pauses itself. */
    private val autoPauseAfterMs: Long = 20_000,
    private val autoPauseEnabled: Boolean = true
) {
    var state: RecorderState = RecorderState.IDLE
        private set

    private val _points = ArrayList<TrackPoint>()
    val points: List<TrackPoint> get() = _points

    private val elevation = ElevationAccumulator()
    private var startMs = 0L
    private var pausedTotalMs = 0L
    private var pauseStartedMs = 0L
    private var lastFix: GpsFix? = null
    private var distanceM = 0.0
    private var movingMs = 0L
    private var maxSpeedMps = 0.0
    private var currentSpeedMps = 0.0
    private var stillSinceMs: Long? = null
    private var finishedAtMs = 0L

    fun start(timeMs: Long) {
        check(state == RecorderState.IDLE) { "Already started" }
        startMs = timeMs
        state = RecorderState.RECORDING
    }

    fun pause(timeMs: Long) {
        if (state != RecorderState.RECORDING && state != RecorderState.AUTO_PAUSED) return
        pauseStartedMs = timeMs
        state = RecorderState.PAUSED
        currentSpeedMps = 0.0
        stillSinceMs = null
    }

    fun resume(timeMs: Long) {
        if (state != RecorderState.PAUSED) return
        pausedTotalMs += timeMs - pauseStartedMs
        // The next fix must not be measured against a position from before the pause.
        lastFix = null
        state = RecorderState.RECORDING
    }

    fun stop(timeMs: Long): TrackStats {
        if (state == RecorderState.PAUSED) pausedTotalMs += timeMs - pauseStartedMs
        finishedAtMs = timeMs
        state = RecorderState.FINISHED
        return stats(timeMs)
    }

    /**
     * Offers a GPS fix. Returns the track point that was added, or null when the fix was filtered
     * out, arrived while paused, or showed no real movement.
     */
    fun onFix(fix: GpsFix): TrackPoint? {
        if (state != RecorderState.RECORDING && state != RecorderState.AUTO_PAUSED) return null

        val previous = lastFix
        when (val decision = filter.evaluate(previous, fix)) {
            is FixDecision.Reject -> return null
            FixDecision.Stationary -> {
                noteStill(fix.timeMs)
                return null
            }
            is FixDecision.Accept -> {
                val seconds = if (previous == null) 0.0 else (fix.timeMs - previous.timeMs) / 1000.0
                val speed = if (seconds > 0) decision.movedM / seconds else 0.0
                currentSpeedMps = speed

                if (state == RecorderState.AUTO_PAUSED) {
                    // Only a clear step forward ends an automatic pause.
                    if (speed < sport.stoppedBelowMps * 1.5 || decision.movedM < 4.0) {
                        lastFix = fix
                        return null
                    }
                    state = RecorderState.RECORDING
                }

                distanceM += decision.movedM
                if (speed >= sport.stoppedBelowMps) {
                    movingMs += ((fix.timeMs - (previous?.timeMs ?: fix.timeMs))).coerceAtLeast(0)
                    stillSinceMs = null
                } else {
                    noteStill(fix.timeMs)
                }
                if (seconds >= 2.0) maxSpeedMps = max(maxSpeedMps, speed)
                fix.altitudeM?.let { elevation.add(it) }
                lastFix = fix
                return TrackPoint(fix.lat, fix.lon, fix.altitudeM, fix.timeMs, fix.accuracyM).also { _points.add(it) }
            }
        }
    }

    private fun noteStill(timeMs: Long) {
        currentSpeedMps = 0.0
        if (!autoPauseEnabled || state != RecorderState.RECORDING) return
        val since = stillSinceMs ?: timeMs.also { stillSinceMs = it }
        if (timeMs - since >= autoPauseAfterMs) state = RecorderState.AUTO_PAUSED
    }

    /** Adds an already-filtered point loaded from disk, to rebuild state after a restart. */
    fun replay(point: TrackPoint) {
        val prev = _points.lastOrNull()
        if (prev != null) {
            val moved = Geo.distance(prev.lat, prev.lon, point.lat, point.lon)
            val dt = point.timeMs - prev.timeMs
            distanceM += moved
            if (dt > 0 && moved / (dt / 1000.0) >= sport.stoppedBelowMps) movingMs += dt
        }
        point.altitudeM?.let { elevation.add(it) }
        _points.add(point)
        lastFix = GpsFix(point.lat, point.lon, point.altitudeM, point.accuracyM, null, point.timeMs)
    }

    /** Re-creates the clock after a restart. Call before [replay]. */
    fun restoreClock(startMs: Long, pausedTotalMs: Long, state: RecorderState, pauseStartedMs: Long = 0L, nowMs: Long = System.currentTimeMillis()) {
        this.startMs = startMs
        this.pausedTotalMs = pausedTotalMs
        this.state = state
        // A pause that was running when the app died keeps counting from when it began.
        if (state == RecorderState.PAUSED) this.pauseStartedMs = if (pauseStartedMs > 0) pauseStartedMs else nowMs
    }

    val startedAtMs: Long get() = startMs
    val pausedMs: Long get() = pausedTotalMs
    /** When the current pause began, or 0 when not paused. */
    val pauseStartedAtMs: Long get() = if (state == RecorderState.PAUSED) pauseStartedMs else 0L

    fun stats(nowMs: Long): TrackStats {
        val end = if (state == RecorderState.FINISHED) finishedAtMs else nowMs
        val extraPause = if (state == RecorderState.PAUSED) end - pauseStartedMs else 0L
        val elapsed = max(0L, end - startMs - pausedTotalMs - extraPause)
        val movingHours = movingMs / 3_600_000.0
        val avgKmh = if (movingHours > 0) distanceM / 1000.0 / movingHours else 0.0
        return TrackStats(
            distanceM = distanceM,
            elapsedMs = elapsed,
            movingMs = movingMs,
            elevationGainM = elevation.gainM,
            elevationLossM = elevation.lossM,
            currentElevationM = elevation.currentM,
            minElevationM = elevation.minM,
            maxElevationM = elevation.maxM,
            averageSpeedKmh = avgKmh,
            maxSpeedKmh = maxSpeedMps * 3.6,
            currentSpeedKmh = currentSpeedMps * 3.6,
            paceMinPerKm = if (avgKmh > 0.1) 60.0 / avgKmh else 0.0,
            calories = CalorieEstimator.estimate(sport, weightKg, movingMs, avgKmh, elevation.gainM)
        )
    }
}

/**
 * Rough calories from a metabolic equivalent (MET): MET x body weight x hours. Real burn depends on
 * the person, so treat the result as an estimate; it is meant to be sensible, not exact.
 */
object CalorieEstimator {
    fun estimate(sport: SportType, weightKg: Double, movingMs: Long, avgSpeedKmh: Double, gainM: Double): Int {
        val hours = movingMs / 3_600_000.0
        if (hours <= 0.0) return 0
        val met = when (sport) {
            SportType.RUNNING -> max(sport.baseMet, avgSpeedKmh)
            SportType.CYCLING, SportType.MOUNTAIN_BIKING ->
                sport.baseMet + 0.15 * (avgSpeedKmh - 16.0).coerceIn(-8.0, 12.0)
            SportType.HIKING, SportType.WALKING ->
                // Climbing costs more: add up to 4 MET for a steep ascent rate.
                sport.baseMet + min(4.0, (gainM / hours) / 150.0)
            SportType.OFFROAD -> sport.baseMet
        }
        return (met * weightKg * hours).toInt()
    }
}
