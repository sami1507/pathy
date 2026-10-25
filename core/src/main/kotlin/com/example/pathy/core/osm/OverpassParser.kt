package com.example.pathy.core.osm

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.roundToLong

/**
 * A trail read from OpenStreetMap.
 *
 * Official routes (a marked trail, a long-distance path) are *relations*: a list of many ways that
 * together make the route, with the name on the relation. The old parser looked at plain ways only,
 * so a 12 km marked trail arrived as dozens of unnamed fragments. This keeps the relation, joins its
 * ways into one line and keeps its name and tags.
 */
data class OsmTrail(
    val id: String,
    val tags: Map<String, String>,
    /** The route as one ordered line (the longest chain of joined ways). */
    val path: List<LatLon>,
    /** Pieces that could not be joined to the main line (branches, gaps in the data). */
    val extraSegments: List<List<LatLon>>,
    val isRelation: Boolean,
    /** When this element was last edited on OpenStreetMap (needs `out meta`), in epoch milliseconds. */
    val lastEditMs: Long? = null
) {
    val lengthM: Double by lazy { Geo.length(path) }

    /** The best name for a language: name:xx, then name, then the route reference. */
    fun displayName(language: String? = null): String? =
        language?.let { tags["name:$it"] }?.takeIf { it.isNotBlank() }
            ?: tags["name"]?.takeIf { it.isNotBlank() }
            ?: tags["ref"]?.takeIf { it.isNotBlank() }
}

object OverpassParser {

    /** Parses an Overpass JSON answer that was requested with `out geom`. Bad elements are skipped. */
    fun parse(json: String): List<OsmTrail> {
        val root = JsonParser.parseString(json).asJsonObject
        val elements = root.getAsJsonArray("elements") ?: return emptyList()
        val trails = ArrayList<OsmTrail>()
        for (element in elements) {
            val obj = element.asJsonObject
            when (obj.get("type")?.asString) {
                "relation" -> relationTrail(obj)?.let { trails += it }
                "way" -> wayTrail(obj)?.let { trails += it }
            }
        }
        return trails
    }

    private fun tagsOf(obj: JsonObject): Map<String, String> {
        val tags = obj.getAsJsonObject("tags") ?: return emptyMap()
        return tags.entrySet().associate { it.key to it.value.asString }
    }

    private fun editTime(obj: JsonObject): Long? =
        obj.get("timestamp")?.takeIf { it.isJsonPrimitive }?.asString?.let {
            try { java.time.Instant.parse(it).toEpochMilli() } catch (e: Exception) { null }
        }

    private fun geometry(arr: JsonArray?): List<LatLon> {
        if (arr == null) return emptyList()
        val out = ArrayList<LatLon>(arr.size())
        for (p in arr) {
            val o = p.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: continue
            val lat = o.get("lat")?.asDouble ?: continue
            val lon = o.get("lon")?.asDouble ?: continue
            out += LatLon(lat, lon)
        }
        return out
    }

    private fun wayTrail(obj: JsonObject): OsmTrail? {
        val pts = geometry(obj.getAsJsonArray("geometry"))
        if (pts.size < 2) return null
        return OsmTrail("osm_w${obj.get("id").asLong}", tagsOf(obj), pts, emptyList(), isRelation = false, lastEditMs = editTime(obj))
    }

    private fun relationTrail(obj: JsonObject): OsmTrail? {
        val members = obj.getAsJsonArray("members") ?: return null
        val ways = ArrayList<List<LatLon>>()
        for (m in members) {
            val mo = m.asJsonObject
            if (mo.get("type")?.asString != "way") continue
            val pts = geometry(mo.getAsJsonArray("geometry"))
            if (pts.size >= 2) ways += pts
        }
        if (ways.isEmpty()) return null
        val chains = stitch(ways)
        val main = chains.first()
        return OsmTrail("osm_r${obj.get("id").asLong}", tagsOf(obj), main, chains.drop(1), isRelation = true, lastEditMs = editTime(obj))
    }

    /**
     * Joins ways that share an end point into chains, longest chain first. Ways may be listed in any
     * order and may be drawn in either direction; they are turned around where needed.
     */
    fun stitch(ways: List<List<LatLon>>): List<List<LatLon>> {
        if (ways.isEmpty()) return emptyList()
        // Two end points within about a metre are the same junction.
        fun key(p: LatLon) = (p.lat * 1e5).roundToLong() to (p.lon * 1e5).roundToLong()

        val ends = HashMap<Pair<Long, Long>, MutableList<Int>>()
        ways.forEachIndexed { i, w ->
            ends.getOrPut(key(w.first())) { ArrayList() } += i
            ends.getOrPut(key(w.last())) { ArrayList() } += i
        }
        val used = BooleanArray(ways.size)

        fun takeNext(at: LatLon): List<LatLon>? {
            val list = ends[key(at)] ?: return null
            for (i in list) {
                if (used[i]) continue
                used[i] = true
                val w = ways[i]
                return if (key(w.first()) == key(at)) w else w.reversed()
            }
            return null
        }

        val chains = ArrayList<List<LatLon>>()
        for (start in ways.indices) {
            if (used[start]) continue
            used[start] = true
            val chain = ArrayDeque<LatLon>(ways[start])
            // Grow forward from the end, then backward from the start.
            while (true) {
                val next = takeNext(chain.last()) ?: break
                chain.addAll(next.drop(1))
            }
            while (true) {
                val prev = takeNext(chain.first()) ?: break
                // prev starts at the junction; we need it to END at the junction to prepend.
                val oriented = prev.reversed()
                for (p in oriented.dropLast(1).asReversed()) chain.addFirst(p)
            }
            chains += chain.toList()
        }
        return chains.sortedByDescending { Geo.length(it) }
    }
}
