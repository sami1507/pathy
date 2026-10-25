package com.example.pathy.core.nav

import kotlin.math.roundToInt

/** Something worth saying out loud. The screen turns each one into a sentence in the app language. */
sealed interface Cue {
    /** The walker reached the route and navigation has begun. */
    object Joined : Cue

    /** A turn is ahead: first heard from far away, again when close. */
    data class Ahead(val maneuver: Maneuver, val distanceM: Int) : Cue

    /** The turn is here. */
    data class TurnNow(val maneuver: Maneuver) : Cue

    object OffRoute : Cue
    object BackOnRoute : Cue
    object Arrived : Cue
}

/**
 * Decides when to speak, so the walker hears each instruction once at a useful moment instead of
 * every few seconds. The old guidance repeated itself and stayed silent at the turns that mattered.
 */
class VoiceCueScheduler(
    private val farM: Int = 150,
    private val nearM: Int = 40,
    private val nowM: Int = 12
) {
    private enum class Stage { FAR, NEAR, NOW }

    private val said = HashSet<Pair<Int, Stage>>()
    private var lastPhase: NavPhase? = null
    private var announcedArrival = false

    /** Returns the cue to speak for this update, or null when there is nothing new to say. */
    fun next(state: NavState): Cue? {
        val previous = lastPhase
        lastPhase = state.phase

        when (state.phase) {
            NavPhase.ARRIVED -> {
                if (announcedArrival) return null
                announcedArrival = true
                return Cue.Arrived
            }
            NavPhase.OFF_ROUTE -> return if (previous != NavPhase.OFF_ROUTE) Cue.OffRoute else null
            NavPhase.APPROACHING -> return null
            NavPhase.ON_ROUTE -> Unit
        }

        if (previous == NavPhase.OFF_ROUTE) return Cue.BackOnRoute
        if (previous == NavPhase.APPROACHING || previous == null) return Cue.Joined

        val maneuver = state.nextManeuver ?: return null
        val distance = state.distanceToManeuverM ?: return null
        val key = maneuver.alongM.roundToInt()

        // The closest stage that applies wins; passing it also settles the farther ones, so a walker who
        // joins the route close to a turn is not told about it twice.
        val stage = when {
            distance <= nowM -> Stage.NOW
            distance <= nearM -> Stage.NEAR
            distance <= farM -> Stage.FAR
            else -> return null
        }
        if ((key to stage) in said) return null
        for (s in Stage.values()) if (s.ordinal <= stage.ordinal) said += key to s

        return when (stage) {
            Stage.NOW -> Cue.TurnNow(maneuver)
            else -> Cue.Ahead(maneuver, roundForSpeech(distance))
        }
    }

    /** "In 150 metres", not "in 147": round to a figure a person would say. */
    private fun roundForSpeech(distanceM: Double): Int = when {
        distanceM >= 100 -> (distanceM / 50).roundToInt() * 50
        else -> (distanceM / 10).roundToInt() * 10
    }
}
