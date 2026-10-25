package com.example.pathy.core.nav

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.geo.RoutePolyline
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** How sharp a turn is, and which way. */
enum class Turn {
    SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN;

    val isLeft: Boolean get() = this == SLIGHT_LEFT || this == LEFT || this == SHARP_LEFT
    val isRight: Boolean get() = this == SLIGHT_RIGHT || this == RIGHT || this == SHARP_RIGHT
}

/** A point on the route where the walker has to change direction. */
data class Maneuver(
    val turn: Turn,
    /** Signed change of heading in degrees; positive is clockwise (right). */
    val angleDeg: Double,
    val alongM: Double,
    val position: LatLon
)

enum class NavPhase {
    /** Not close enough to the route yet: head for the nearest part of it. */
    APPROACHING,
    ON_ROUTE,
    OFF_ROUTE,
    ARRIVED
}

data class NavState(
    val phase: NavPhase,
    /** Metres from the walker to the nearest point of the route. */
    val offsetM: Double,
    /** Where the walker is along the route, from the start. */
    val alongM: Double,
    val remainingM: Double,
    /** 0..1 */
    val progress: Float,
    val nextManeuver: Maneuver?,
    val distanceToManeuverM: Double?,
    /** While approaching or off route: the compass bearing toward the nearest route point. */
    val bearingToRouteDeg: Double?,
    val nearestRoutePoint: LatLon
)

/**
 * Follows a walker along a fixed route.
 *
 * The old guidance compared the compass bearing to the next point with fixed ranges, so it said
 * "turn right" whenever that point lay east, whatever way the walker faced, and it only started
 * within 5 m of the first point. This version works from the route itself:
 *  - a turn is the change of direction of the line at a vertex, so left and right are always right;
 *  - progress is the walker's projection onto the line, so a GPS error of a few metres does not stall it;
 *  - being off the route needs several fixes in a row, and coming back needs a clear return.
 */
class RouteNavigator(points: List<LatLon>, private val config: Config = Config()) {

    data class Config(
        /** Further than this from the line counts as off route (raised automatically for poor GPS). */
        val offRouteM: Double = 35.0,
        /** How many fixes in a row must be off route before the walker is told. */
        val offRouteFixes: Int = 3,
        /** Within this distance of the line the walker is back on route. */
        val backOnRouteM: Double = 20.0,
        /** Near the end of the route within this distance means arrived. */
        val arrivalM: Double = 20.0,
        /** Within this distance of the line navigation starts, wherever along it the walker joins. */
        val joinM: Double = 40.0,
        /** Smaller direction changes are not worth announcing. */
        val minTurnDeg: Double = 35.0,
        /** Route simplification before looking for turns, to ignore GPS-trace wiggle. */
        val simplifyM: Double = 6.0,
        /** Turns closer together than this are merged into one. */
        val mergeTurnsM: Double = 25.0,
        /** Segments searched ahead of the last match, so a loop route keeps to the right lap. */
        val searchAheadSegments: Int = 250,
        val searchBackSegments: Int = 12
    )

    val route = RoutePolyline(points)
    val maneuvers: List<Maneuver> = findManeuvers(points)

    private var started = false
    private var lastSegment = 0
    private var offStreak = 0
    private var off = false
    private var arrived = false
    private var furthestAlongM = 0.0

    /** Feed the latest position. [accuracyM] widens the off-route limit when GPS is poor. */
    fun update(position: LatLon, accuracyM: Float? = null): NavState {
        val slack = max(config.offRouteM, (accuracyM ?: 0f) * 1.5)
        val limit = min(slack, 200.0)

        val local = route.project(
            position,
            fromSegment = lastSegment - config.searchBackSegments,
            toSegment = lastSegment + config.searchAheadSegments
        )
        // When the local match is poor, check the whole route: the walker may have rejoined elsewhere.
        val global = if (!started || off || local.offsetM > limit) route.project(position) else local
        val match = if (global.offsetM < local.offsetM - 5.0) global else local

        if (!started) {
            if (match.offsetM <= config.joinM) {
                started = true
                lastSegment = match.segment
            } else {
                return state(NavPhase.APPROACHING, match, position)
            }
        }

        if (off) {
            if (match.offsetM <= config.backOnRouteM) {
                off = false
                offStreak = 0
                lastSegment = match.segment
            }
        } else {
            if (match.offsetM > limit) {
                offStreak++
                if (offStreak >= config.offRouteFixes) off = true
            } else {
                offStreak = 0
                lastSegment = match.segment
            }
        }

        if (!off && match.offsetM <= limit) furthestAlongM = max(furthestAlongM, match.alongM)
        val remaining = route.lengthM - match.alongM
        // A loop starts and ends at the same place; only finish after most of it has been walked.
        if (!off && remaining <= config.arrivalM && furthestAlongM >= route.lengthM * 0.8) arrived = true

        val phase = when {
            arrived -> NavPhase.ARRIVED
            off -> NavPhase.OFF_ROUTE
            else -> NavPhase.ON_ROUTE
        }
        return state(phase, match, position)
    }

    private fun state(phase: NavPhase, match: com.example.pathy.core.geo.Projection, position: LatLon): NavState {
        val along = match.alongM
        val next = maneuvers.firstOrNull { it.alongM > along + 3.0 }
        return NavState(
            phase = phase,
            offsetM = match.offsetM,
            alongM = along,
            remainingM = max(0.0, route.lengthM - along),
            progress = (along / route.lengthM).toFloat().coerceIn(0f, 1f),
            nextManeuver = next,
            distanceToManeuverM = next?.let { it.alongM - along },
            bearingToRouteDeg = if (phase == NavPhase.APPROACHING || phase == NavPhase.OFF_ROUTE) {
                Geo.bearing(position, match.point)
            } else null,
            nearestRoutePoint = match.point
        )
    }

    private fun findManeuvers(points: List<LatLon>): List<Maneuver> {
        if (points.size < 3) return emptyList()
        val kept = RoutePolyline.simplifyIndices(points, config.simplifyM)
        if (kept.size < 3) return emptyList()
        val cumulative = route.cumulative
        val found = ArrayList<Maneuver>()
        for (k in 1 until kept.size - 1) {
            val before = points[kept[k - 1]]
            val at = points[kept[k]]
            val after = points[kept[k + 1]]
            // Very short legs give unstable bearings; skip them (the neighbours carry the turn).
            if (Geo.distance(before, at) < 5.0 || Geo.distance(at, after) < 5.0) continue
            val inHeading = Geo.bearing(before, at)
            val outHeading = Geo.bearing(at, after)
            val angle = Geo.turnBetween(inHeading, outHeading)
            if (abs(angle) < config.minTurnDeg) continue
            found += Maneuver(classify(angle), angle, cumulative[kept[k]], at)
        }
        // Merge turns that are practically the same junction, keeping the sharper one.
        val merged = ArrayList<Maneuver>()
        for (m in found) {
            val last = merged.lastOrNull()
            if (last != null && m.alongM - last.alongM < config.mergeTurnsM) {
                if (abs(m.angleDeg) > abs(last.angleDeg)) merged[merged.lastIndex] = m
            } else merged += m
        }
        return merged
    }

    companion object {
        fun classify(angle: Double): Turn {
            val a = abs(angle)
            val right = angle > 0
            return when {
                a >= 160 -> Turn.U_TURN
                a >= 120 -> if (right) Turn.SHARP_RIGHT else Turn.SHARP_LEFT
                a >= 60 -> if (right) Turn.RIGHT else Turn.LEFT
                else -> if (right) Turn.SLIGHT_RIGHT else Turn.SLIGHT_LEFT
            }
        }
    }
}
