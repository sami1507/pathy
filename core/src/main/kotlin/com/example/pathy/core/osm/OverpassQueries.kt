package com.example.pathy.core.osm

import com.example.pathy.core.geo.LatLon
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

/** The kinds of trail PATHY looks for in OpenStreetMap. */
enum class OsmKind { HIKING, MTB, CYCLING, OFFROAD }

/** Decides what kind of trail an OSM element is, from its tags. */
object OsmClassifier {

    /**
     * The kind of trail these tags describe, or null when they are not a trail worth listing (a pavement,
     * an unnamed field path). A named route relation is always worth listing.
     */
    fun classify(tags: Map<String, String>, isRelation: Boolean): OsmKind? {
        when (tags["route"]) {
            "mtb" -> return OsmKind.MTB
            "bicycle" -> return OsmKind.CYCLING
            "hiking", "foot", "walking" -> return OsmKind.HIKING
        }
        if (isRelation) return null

        val highway = tags["highway"] ?: return null
        val notable = !tags["name"].isNullOrBlank() || !tags["ref"].isNullOrBlank() ||
            tags.containsKey("sac_scale") || tags.containsKey("mtb:scale")

        return when {
            highway == "track" && (tags["tracktype"] in setOf("grade4", "grade5") || tags["4wd_only"] == "yes") -> OsmKind.OFFROAD
            highway == "cycleway" && notable -> OsmKind.CYCLING
            tags.containsKey("mtb:scale") && highway in setOf("path", "track", "cycleway") -> OsmKind.MTB
            highway == "path" && tags["bicycle"] == "designated" && notable -> OsmKind.CYCLING
            // A named footway is usually a pavement or a staircase in a town. It counts as a trail only when
            // the data says it is rough ground a hiker would choose.
            highway == "footway" && (tags.containsKey("sac_scale") || tags.containsKey("trail_visibility")) -> OsmKind.HIKING
            highway == "path" && notable -> OsmKind.HIKING
            highway == "track" && notable && tags["foot"] in setOf("yes", "designated") -> OsmKind.HIKING
            else -> null
        }
    }
}

/** Overpass QL text. One request covers every kind that was asked for. */
object OverpassQueries {

    /**
     * Trails within [radiusM] of [center]. Geometry comes back with the result (`out geom`) and is cut
     * to the search square, so a country-long route relation sends only the part that is near.
     */
    fun nearby(center: LatLon, radiusM: Int, kinds: Set<OsmKind>, timeoutS: Int = 25): String {
        val around = "(around:$radiusM,${coord(center.lat)},${coord(center.lon)})"
        val parts = ArrayList<String>()
        if (OsmKind.HIKING in kinds) {
            parts += """relation["route"~"^(hiking|foot|walking)$"]$around;"""
            // Named footways are mostly pavements and staircases in towns, so only paths and signed rough footways are fetched.
            parts += """way["highway"="path"]["name"]$around;"""
            parts += """way["highway"~"^(path|footway)$"]["sac_scale"]$around;"""
            parts += """way["highway"="track"]["name"]["foot"~"^(yes|designated)$"]$around;"""
        }
        if (OsmKind.MTB in kinds) {
            parts += """relation["route"="mtb"]$around;"""
            parts += """way["highway"~"^(path|track|cycleway)$"]["mtb:scale"]$around;"""
        }
        if (OsmKind.CYCLING in kinds) {
            parts += """relation["route"="bicycle"]$around;"""
            parts += """way["highway"="cycleway"]["name"]$around;"""
        }
        if (OsmKind.OFFROAD in kinds) {
            parts += """way["highway"="track"]["tracktype"~"^grade[45]$"]$around;"""
            parts += """way["highway"="track"]["4wd_only"="yes"]$around;"""
        }
        if (parts.isEmpty()) return ""
        return buildString {
            append("[out:json][timeout:").append(timeoutS).append("];\n(\n")
            parts.forEach { append("  ").append(it).append('\n') }
            append(");\nout meta geom(").append(boundingBox(center, radiusM)).append(");")
        }
    }

    /** south,west,north,east of the square around [center] that contains the search circle. */
    fun boundingBox(center: LatLon, radiusM: Int): String {
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * max(0.01, cos(Math.toRadians(center.lat))))
        return listOf(center.lat - dLat, center.lon - dLon, center.lat + dLat, center.lon + dLon)
            .joinToString(",") { coord(it) }
    }

    /** Fixed-point text with a dot, whatever the phone's language is (Arabic locales print other digits). */
    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)
}
