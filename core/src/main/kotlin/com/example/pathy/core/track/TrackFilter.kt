package com.example.pathy.core.track

import com.example.pathy.core.geo.Geo
import kotlin.math.max
import kotlin.math.min

/** Why a fix was not added to the track. */
enum class RejectReason { LOW_ACCURACY, OUT_OF_ORDER, GPS_JUMP }

sealed interface FixDecision {
    /** The fix is a real movement of [movedM] metres from the previous accepted fix. */
    data class Accept(val movedM: Double) : FixDecision

    /** Good reading but the person has not really moved: GPS wander while standing still. */
    object Stationary : FixDecision

    data class Reject(val reason: RejectReason) : FixDecision
}

/**
 * Keeps a recorded track clean. Raw phone GPS drifts several metres while standing still, jumps
 * after a tunnel or a cold start, and reports poor fixes under tree cover. Without filtering those
 * errors are added to the distance and the elevation and a short walk looks like a long one.
 */
class TrackFilter(
    private val sport: SportType,
    /** Fixes less accurate than this are ignored (metres). */
    private val maxAccuracyM: Float = 30f
) {
    fun evaluate(previous: GpsFix?, fix: GpsFix): FixDecision {
        val accuracy = fix.accuracyM
        if (accuracy == null || accuracy > maxAccuracyM) return FixDecision.Reject(RejectReason.LOW_ACCURACY)
        if (previous == null) return FixDecision.Accept(0.0)
        if (fix.timeMs <= previous.timeMs) return FixDecision.Reject(RejectReason.OUT_OF_ORDER)

        val moved = Geo.distance(previous.position, fix.position)
        val seconds = (fix.timeMs - previous.timeMs) / 1000.0
        if (moved / seconds > sport.maxPlausibleSpeedMps) return FixDecision.Reject(RejectReason.GPS_JUMP)

        // Movement inside the uncertainty of the two fixes is noise, not progress.
        val noiseFloor = max(2.5, min(10.0, (accuracy + (previous.accuracyM ?: accuracy)) / 2.0 * 0.6))
        return if (moved < noiseFloor) FixDecision.Stationary else FixDecision.Accept(moved)
    }
}
