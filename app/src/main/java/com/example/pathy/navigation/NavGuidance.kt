package com.example.pathy.navigation

import android.content.Context
import com.example.pathy.R
import com.example.pathy.core.geo.Geo
import com.example.pathy.core.nav.Cue
import com.example.pathy.core.nav.NavPhase
import com.example.pathy.core.nav.NavState
import com.example.pathy.core.nav.Turn
import com.example.pathy.formatDistance
import kotlin.math.abs

/** Which picture goes with an instruction. */
enum class GuideIcon { LOCATING, TO_START, STRAIGHT, LEFT, RIGHT, U_TURN, ARRIVED, OFF_ROUTE }

/** What the navigation banner shows. */
data class Guidance(val text: String, val detail: String?, val icon: GuideIcon)

/** Turns the navigator's state into words in the app language. The thinking is in RouteNavigator. */
object NavGuidance {

    fun of(context: Context, state: NavState?, headingDeg: Float?): Guidance {
        if (state == null) return Guidance(context.getString(R.string.nav_locating), null, GuideIcon.LOCATING)
        return when (state.phase) {
            NavPhase.ARRIVED -> Guidance(context.getString(R.string.nav_arrived), null, GuideIcon.ARRIVED)

            NavPhase.APPROACHING -> Guidance(
                text = context.getString(R.string.nav_go_to_start),
                detail = listOfNotNull(
                    context.getString(R.string.nav_distance_to_start, formatDistance(context, state.offsetM)),
                    directionTo(context, headingDeg, state.bearingToRouteDeg)
                ).joinToString(" · "),
                icon = GuideIcon.TO_START
            )

            NavPhase.OFF_ROUTE -> Guidance(
                text = context.getString(R.string.nav_off_route_title),
                detail = context.getString(
                    R.string.nav_off_route_detail,
                    formatDistance(context, state.offsetM),
                    directionTo(context, headingDeg, state.bearingToRouteDeg)
                        ?: context.getString(R.string.nav_go_to_start)
                ),
                icon = GuideIcon.OFF_ROUTE
            )

            NavPhase.ON_ROUTE -> {
                val maneuver = state.nextManeuver
                val remaining = context.getString(R.string.nav_remaining, formatDistance(context, state.remainingM))
                if (maneuver == null) {
                    Guidance(
                        text = context.getString(R.string.nav_follow_to_end, formatDistance(context, state.remainingM)),
                        detail = null,
                        icon = GuideIcon.STRAIGHT
                    )
                } else {
                    Guidance(
                        text = context.getString(
                            R.string.nav_in_distance,
                            formatDistance(context, state.distanceToManeuverM ?: 0.0),
                            turnWords(context, maneuver.turn)
                        ),
                        detail = remaining,
                        icon = iconFor(maneuver.turn)
                    )
                }
            }
        }
    }

    /** The sentence spoken for a cue. */
    fun speech(context: Context, cue: Cue): String = when (cue) {
        Cue.Joined -> context.getString(R.string.nav_joined)
        is Cue.Ahead -> context.getString(
            R.string.nav_in_distance,
            formatDistance(context, cue.distanceM.toDouble()),
            turnWords(context, cue.maneuver.turn)
        )
        is Cue.TurnNow -> turnWords(context, cue.maneuver.turn)
        Cue.OffRoute -> context.getString(R.string.nav_off_route_title)
        Cue.BackOnRoute -> context.getString(R.string.nav_back_on_route)
        Cue.Arrived -> context.getString(R.string.nav_arrived)
    }

    fun turnWords(context: Context, turn: Turn): String = context.getString(
        when (turn) {
            Turn.LEFT -> R.string.nav_turn_left
            Turn.RIGHT -> R.string.nav_turn_right
            Turn.SLIGHT_LEFT -> R.string.nav_bear_left
            Turn.SLIGHT_RIGHT -> R.string.nav_bear_right
            Turn.SHARP_LEFT -> R.string.nav_sharp_left
            Turn.SHARP_RIGHT -> R.string.nav_sharp_right
            Turn.U_TURN -> R.string.nav_turn_around
        }
    )

    fun iconFor(turn: Turn): GuideIcon = when {
        turn == Turn.U_TURN -> GuideIcon.U_TURN
        turn.isLeft -> GuideIcon.LEFT
        else -> GuideIcon.RIGHT
    }

    /**
     * Which way to turn to face [bearingDeg] when facing [headingDeg]. Null while the compass or the
     * target is unknown: better to say nothing than to point the wrong way.
     */
    fun directionTo(context: Context, headingDeg: Float?, bearingDeg: Double?): String? {
        if (headingDeg == null || bearingDeg == null) return null
        val angle = Geo.turnBetween(headingDeg.toDouble(), bearingDeg)
        val a = abs(angle)
        val res = when {
            a <= 30 -> R.string.nav_continue_straight
            a >= 150 -> R.string.nav_turn_around
            angle > 0 -> if (a >= 60) R.string.nav_turn_right else R.string.nav_bear_right
            else -> if (a >= 60) R.string.nav_turn_left else R.string.nav_bear_left
        }
        return context.getString(res)
    }
}
