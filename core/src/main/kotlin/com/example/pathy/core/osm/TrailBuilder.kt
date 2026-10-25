package com.example.pathy.core.osm

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.geo.RoutePolyline
import com.example.pathy.core.trail.Grade
import com.example.pathy.core.trail.TrailMetrics
import kotlin.math.roundToLong

/** A trail ready to show: what OSM knows, plus honest numbers worked out from it. */
data class TrailCandidate(
    val id: String,
    /** Null when OSM has no name; the screens then describe the place instead. */
    val name: String?,
    val kind: OsmKind,
    val path: List<LatLon>,
    val distanceM: Double,
    /** Climb in metres when OSM states it. Null means unknown, never a guess. */
    val gainM: Double?,
    val grade: Grade,
    val durationMin: Int,
    val surface: String?,
    val isLoop: Boolean,
    val tags: Map<String, String>,
    /** Whether the trail can be used, how recently it was checked, and what to warn about. */
    val assessment: TrailAssessment,
    /** The colour of the paint marks on the trail (red, blue...), when the route is waymarked. */
    val waymark: String? = null,
    /** International, national, regional or local walking network (iwn, nwn, rwn, lwn). */
    val network: String? = null
) {
    val midpoint: LatLon get() = path[path.size / 2]
}

object TrailBuilder {
    const val MIN_LENGTH_M = 400.0

    /** Trails are passed between screens as text; thousands of points would not fit. */
    const val MAX_POINTS = 1_500

    fun from(osm: OsmTrail, language: String? = null, nowMs: Long = System.currentTimeMillis()): TrailCandidate? {
        val kind = OsmClassifier.classify(osm.tags, osm.isRelation) ?: return null
        val distance = osm.lengthM
        if (distance < MIN_LENGTH_M || osm.path.size < 2) return null
        val assessment = TrailAvailability.assess(kind, osm.tags, osm.isRelation, osm.lastEditMs, nowMs)

        val gain = osm.tags["ascent"]?.let(::leadingNumber)
        val grade = when (kind) {
            OsmKind.HIKING -> TrailMetrics.hikingGrade(distance, gain, osm.tags["sac_scale"])
            OsmKind.MTB -> TrailMetrics.mountainBikeGrade(osm.tags["mtb:scale"]?.let(::leadingNumber)?.toInt(), distance)
            OsmKind.CYCLING -> TrailMetrics.cyclingGrade(distance, gain)
            OsmKind.OFFROAD -> TrailMetrics.offroadGrade(osm.tags["tracktype"])
        }
        val metricsKind = when (kind) {
            OsmKind.HIKING -> TrailMetrics.Kind.HIKING
            OsmKind.MTB -> TrailMetrics.Kind.MOUNTAIN_BIKING
            OsmKind.CYCLING -> TrailMetrics.Kind.CYCLING
            OsmKind.OFFROAD -> TrailMetrics.Kind.OFFROAD
        }
        val path = thin(osm.path)
        return TrailCandidate(
            id = osm.id,
            name = osm.displayName(language),
            kind = kind,
            path = path,
            distanceM = distance,
            gainM = gain,
            grade = grade,
            durationMin = TrailMetrics.durationMinutes(metricsKind, distance, gain),
            surface = osm.tags["surface"],
            isLoop = Geo.distance(osm.path.first(), osm.path.last()) < 100.0,
            tags = osm.tags,
            assessment = assessment,
            waymark = waymarkColour(osm.tags),
            network = osm.tags["network"]?.takeIf { it in setOf("iwn", "nwn", "rwn", "lwn") }
        )
    }

    /**
     * Removes duplicates: the same element found twice, and a named relation that is also drawn as
     * its own ways (the relation wins, with its name). Two trails with the same name whose middles
     * are within about 100 m are one trail.
     */
    fun dedupe(trails: List<TrailCandidate>): List<TrailCandidate> {
        val byId = LinkedHashMap<String, TrailCandidate>()
        trails.forEach { byId.putIfAbsent(it.id, it) }
        val seen = HashSet<Triple<String, Long, Long>>()
        return byId.values
            .sortedWith(compareByDescending<TrailCandidate> { it.name != null }.thenByDescending { it.assessment.quality }) // best first, so it wins a tie
            .filter { t ->
                val name = t.name?.lowercase() ?: return@filter true
                seen.add(Triple(name, (t.midpoint.lat * 1_000).roundToLong(), (t.midpoint.lon * 1_000).roundToLong()))
            }
    }

    /**
     * The first colour of the paint mark: `osmc:symbol=red:white:red_stripe` is a red trail, and so is
     * `colour=red`. Marked routes often have no name, so the colour is how a hiker recognises them.
     */
    internal fun waymarkColour(tags: Map<String, String>): String? {
        val raw = tags["osmc:symbol"]?.substringBefore(':') ?: tags["colour"] ?: tags["color"]
        val c = raw?.trim()?.lowercase() ?: return null
        return c.takeIf { it in KNOWN_COLOURS }
    }

    private val KNOWN_COLOURS = setOf("red", "blue", "green", "black", "orange", "yellow", "white", "purple", "brown", "pink", "grey", "gray")

    /** Reduces a long path to at most [MAX_POINTS] points that keep its shape. */
    fun thin(path: List<LatLon>, maxPoints: Int = MAX_POINTS): List<LatLon> {
        if (path.size <= maxPoints) return path
        var tolerance = 2.0
        while (tolerance <= 500.0) {
            val keep = RoutePolyline.simplifyIndices(path, tolerance)
            if (keep.size <= maxPoints) return keep.map { path[it] }
            tolerance *= 1.5
        }
        val step = path.size / maxPoints + 1
        return path.filterIndexed { i, _ -> i % step == 0 || i == path.lastIndex }
    }

    /** "250", "250 m" and "250.5" all give a number; anything else gives null. */
    internal fun leadingNumber(text: String): Double? =
        Regex("""^\s*(\d+(?:[.,]\d+)?)""").find(text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
}
