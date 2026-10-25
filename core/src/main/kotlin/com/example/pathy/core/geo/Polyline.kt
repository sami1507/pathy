package com.example.pathy.core.geo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Where a point lands on a route: the nearest spot on the line and how far along it is. */
data class Projection(
    /** Index of the segment (from point [segment] to [segment] + 1). */
    val segment: Int,
    /** 0..1 position inside that segment. */
    val t: Double,
    /** Distance from the query point to the route, in metres. */
    val offsetM: Double,
    /** Distance from the start of the route to the projected spot, in metres. */
    val alongM: Double,
    val point: LatLon
)

/**
 * A route with its cumulative distances worked out once, so matching a GPS fix costs a short scan
 * instead of a full recomputation.
 */
class RoutePolyline(val points: List<LatLon>) {
    init {
        require(points.size >= 2) { "A route needs at least two points" }
    }

    /** cumulative[i] = metres from the start to point i. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
    }

    val lengthM: Double get() = cumulative.last()

    /**
     * Nearest spot on the route to [p], looking only at segments [fromSegment]..[toSegment]
     * (both inclusive, clamped). Searching a window keeps a loop route from snapping to the wrong lap.
     */
    fun project(p: LatLon, fromSegment: Int = 0, toSegment: Int = points.size - 2): Projection {
        val first = max(0, fromSegment)
        val last = min(points.size - 2, toSegment)
        // Work in a flat metre grid around p: accurate to centimetres over a segment.
        val metresPerDegLat = Geo.EARTH_RADIUS_M * PI / 180.0
        val metresPerDegLon = metresPerDegLat * cos(p.lat * PI / 180.0)
        var best: Projection? = null
        for (i in first..last) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lon - p.lon) * metresPerDegLon
            val ay = (a.lat - p.lat) * metresPerDegLat
            val bx = (b.lon - p.lon) * metresPerDegLon
            val by = (b.lat - p.lat) * metresPerDegLat
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-9) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
            val tc = t.coerceIn(0.0, 1.0)
            val px = ax + tc * dx
            val py = ay + tc * dy
            val off = sqrt(px * px + py * py)
            if (best == null || off < best.offsetM) {
                val segLen = cumulative[i + 1] - cumulative[i]
                best = Projection(
                    segment = i,
                    t = tc,
                    offsetM = off,
                    alongM = cumulative[i] + tc * segLen,
                    point = LatLon(a.lat + tc * (b.lat - a.lat), a.lon + tc * (b.lon - a.lon))
                )
            }
        }
        return checkNotNull(best) { "A route needs at least one segment" }
    }

    /** The point [alongM] metres from the start (clamped to the route). */
    fun pointAt(alongM: Double): LatLon {
        if (alongM <= 0) return points.first()
        if (alongM >= lengthM) return points.last()
        var lo = 0
        var hi = points.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] <= alongM) lo = mid else hi = mid
        }
        val seg = cumulative[hi] - cumulative[lo]
        val t = if (seg < 1e-9) 0.0 else (alongM - cumulative[lo]) / seg
        return LatLon(
            points[lo].lat + t * (points[hi].lat - points[lo].lat),
            points[lo].lon + t * (points[hi].lon - points[lo].lon)
        )
    }

    companion object {
        /**
         * Douglas-Peucker simplification. Returns the indices of the points that survive, always
         * including the first and last, so callers can map a kept point back to the original route.
         */
        fun simplifyIndices(points: List<LatLon>, toleranceM: Double): List<Int> {
            if (points.size <= 2) return points.indices.toList()
            val keep = BooleanArray(points.size)
            keep[0] = true
            keep[points.size - 1] = true
            val stack = ArrayDeque<IntArray>()
            stack.addLast(intArrayOf(0, points.size - 1))
            while (stack.isNotEmpty()) {
                val (s, e) = stack.removeLast()
                if (e <= s + 1) continue
                val seg = RoutePolyline(listOf(points[s], points[e]))
                var worst = -1.0
                var worstIdx = -1
                for (i in s + 1 until e) {
                    val off = seg.project(points[i]).offsetM
                    if (off > worst) {
                        worst = off
                        worstIdx = i
                    }
                }
                if (worst > toleranceM && worstIdx != -1) {
                    keep[worstIdx] = true
                    stack.addLast(intArrayOf(s, worstIdx))
                    stack.addLast(intArrayOf(worstIdx, e))
                }
            }
            return points.indices.filter { keep[it] }
        }
    }
}
