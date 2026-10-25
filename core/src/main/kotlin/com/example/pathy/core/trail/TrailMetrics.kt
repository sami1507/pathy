package com.example.pathy.core.trail

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.track.ElevationAccumulator
import kotlin.math.max
import kotlin.math.sqrt

enum class Grade { EASY, MODERATE, HARD, EXTREME }

/**
 * Honest trail numbers. The old code invented climbing as "number of points x 2" and then graded
 * and timed the trail from that invention. Here an unknown climb stays unknown (null) and the grade
 * and the time are worked out only from what is really known.
 */
object TrailMetrics {

    /**
     * Hiking grade from the OSM SAC scale when present, and from effort otherwise. Effort is the
     * Shenandoah National Park formula, sqrt(2 x climb in feet x distance in miles): it weighs a
     * short steep trail and a long gentle one fairly. The harder of the two answers wins.
     */
    fun hikingGrade(distanceM: Double, gainM: Double?, sacScale: String?): Grade {
        val bySac = when (sacScale?.lowercase()?.trim()) {
            "hiking" -> Grade.EASY
            "mountain_hiking" -> Grade.MODERATE
            "demanding_mountain_hiking" -> Grade.HARD
            "alpine_hiking", "demanding_alpine_hiking", "difficult_alpine_hiking" -> Grade.EXTREME
            else -> null
        }
        val byEffort = if (gainM != null) {
            val effort = sqrt(2.0 * (gainM * 3.28084) * (distanceM / 1609.344))
            when {
                effort < 50 -> Grade.EASY
                effort < 100 -> Grade.MODERATE
                effort < 200 -> Grade.HARD
                else -> Grade.EXTREME
            }
        } else {
            // No climb known: judge by length alone and never call a trail extreme from length.
            when {
                distanceM < 6_000 -> Grade.EASY
                distanceM < 14_000 -> Grade.MODERATE
                else -> Grade.HARD
            }
        }
        return if (bySac == null) byEffort else maxOf(bySac, byEffort)
    }

    /** Mountain-bike grade from the IMBA/OSM `mtb:scale` (0 easiest to 6). */
    fun mountainBikeGrade(mtbScale: Int?, distanceM: Double): Grade = when {
        mtbScale == null -> if (distanceM > 30_000) Grade.MODERATE else Grade.EASY
        mtbScale >= 4 -> Grade.EXTREME
        mtbScale == 3 -> Grade.HARD
        mtbScale >= 1 -> Grade.MODERATE
        else -> Grade.EASY
    }

    /** Road-bike / cycle-path grade: length is the only honest signal without a climb. */
    fun cyclingGrade(distanceM: Double, gainM: Double?): Grade {
        val byDistance = when {
            distanceM > 60_000 -> Grade.HARD
            distanceM > 25_000 -> Grade.MODERATE
            else -> Grade.EASY
        }
        val byClimb = when {
            gainM == null -> Grade.EASY
            gainM > 900 -> Grade.HARD
            gainM > 400 -> Grade.MODERATE
            else -> Grade.EASY
        }
        return maxOf(byDistance, byClimb)
    }

    /** 4x4 track grade from the OSM `tracktype` (grade1 solid to grade5 soft). */
    fun offroadGrade(trackType: String?): Grade = when (trackType?.lowercase()?.trim()) {
        "grade5" -> Grade.EXTREME
        "grade4" -> Grade.HARD
        "grade3" -> Grade.MODERATE
        else -> Grade.EASY
    }

    /**
     * Time on the trail in minutes. Walking speed plus extra time for climbing (Naismith's rule,
     * eased for ordinary hikers): 4 km/h and one hour for every 500 m of ascent.
     */
    fun durationMinutes(kind: Kind, distanceM: Double, gainM: Double?): Int {
        val km = distanceM / 1000.0
        val climb = gainM ?: 0.0
        val hours = when (kind) {
            Kind.HIKING -> km / 4.0 + climb / 500.0
            Kind.WALKING -> km / 4.5 + climb / 600.0
            Kind.RUNNING -> km / 9.0 + climb / 900.0
            Kind.MOUNTAIN_BIKING -> km / 11.0 + climb / 700.0
            Kind.CYCLING -> km / 16.0 + climb / 1000.0
            Kind.OFFROAD -> km / 20.0
        }
        return max(1, (hours * 60).toInt())
    }

    enum class Kind { HIKING, WALKING, RUNNING, MOUNTAIN_BIKING, CYCLING, OFFROAD }
}

/** Picks sample points along a route and turns elevations at those points into a climb. */
object ElevationProfile {

    /**
     * Indices of at most [count] points spread evenly by distance along [path], always including the
     * first and last. Elevation services are asked for these instead of every vertex.
     */
    fun sampleIndices(path: List<LatLon>, count: Int): List<Int> {
        if (path.size <= count) return path.indices.toList()
        val cumulative = DoubleArray(path.size)
        for (i in 1 until path.size) cumulative[i] = cumulative[i - 1] + Geo.distance(path[i - 1], path[i])
        val total = cumulative.last()
        if (total <= 0.0) return listOf(0, path.lastIndex)
        val out = ArrayList<Int>(count)
        var next = 0
        for (k in 0 until count) {
            val target = total * k / (count - 1)
            while (next < path.lastIndex && cumulative[next] < target) next++
            if (out.lastOrNull() != next) out += next
        }
        if (out.last() != path.lastIndex) out += path.lastIndex
        return out
    }

    /** Result of walking a profile: climb up, climb down, highest and lowest elevation. */
    data class Climb(val gainM: Double, val lossM: Double, val maxM: Double, val minM: Double)

    /**
     * Climb from sparse elevation samples. Terrain models are good to a few metres, so a rise or
     * fall must exceed [thresholdM] before it counts.
     */
    fun climb(elevations: List<Double>, thresholdM: Double = 4.0): Climb? {
        if (elevations.size < 2) return null
        val acc = ElevationAccumulator(thresholdM = thresholdM, window = 1)
        elevations.forEach { acc.add(it) }
        return Climb(acc.gainM, acc.lossM, elevations.max(), elevations.min())
    }
}
