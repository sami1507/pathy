package com.example.pathy.core.osm

import java.time.LocalDate
import java.time.ZoneOffset

/** Can a person go there? Worked out from what OpenStreetMap says; it is evidence, not a promise. */
enum class Availability {
    /** Nothing in the data says otherwise. */
    OPEN,

    /** Only open part of the year, or by permit/hours: shown with a warning. */
    CONDITIONAL,

    /** Marked closed, under construction, abandoned, or planned but not built. Never listed. */
    CLOSED,

    /** Private land or a no-entry area. Never listed. */
    RESTRICTED
}

enum class Warning { SEASONAL, HARD_TO_FOLLOW, NEEDS_CHECK, OUTDATED }

data class TrailAssessment(
    val availability: Availability,
    val warnings: Set<Warning>,
    /** When someone last checked the trail on the ground (the `check_date` / `survey:date` tag), if stated. */
    val verifiedAtMs: Long?,
    /** When the trail's data was last edited on OpenStreetMap. */
    val lastEditMs: Long?,
    /** 0 (a bare path) to 10 (a marked, named, recently kept route). Used to put the best trails first. */
    val quality: Int
) {
    /** Trails that are closed or private are not shown at all. */
    val isListable: Boolean get() = availability == Availability.OPEN || availability == Availability.CONDITIONAL

    /** The date shown as "updated": a ground check when there is one, otherwise the last edit. */
    val freshestMs: Long? get() = verifiedAtMs ?: lastEditMs
}

object TrailAvailability {
    private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

    /** Prefixes OSM uses for things that used to be a path or route and no longer are. */
    private val LIFECYCLE_PREFIXES = listOf("disused:", "abandoned:", "razed:", "demolished:", "removed:", "was:", "proposed:", "construction:")
    private val LIFECYCLE_KEYS = setOf("highway", "route", "railway", "footway", "path")

    fun assess(kind: OsmKind, tags: Map<String, String>, isRelation: Boolean, lastEditMs: Long?, nowMs: Long): TrailAssessment {
        val warnings = linkedSetOf<Warning>()

        val availability = when {
            isClosed(tags) -> Availability.CLOSED
            isRestricted(kind, tags) -> Availability.RESTRICTED
            isConditional(tags) -> Availability.CONDITIONAL
            else -> Availability.OPEN
        }
        if (availability == Availability.CONDITIONAL) warnings += Warning.SEASONAL

        if (tags["trail_visibility"] in setOf("bad", "horrible", "no") || tags["smoothness"] in setOf("very_horrible", "impassable")) {
            warnings += Warning.HARD_TO_FOLLOW
        }
        if (tags.containsKey("fixme") || tags.containsKey("FIXME")) warnings += Warning.NEEDS_CHECK

        val verified = parseDate(tags["check_date"]) ?: parseDate(tags["survey:date"]) ?: parseDate(tags["check_date:hiking"])
        val freshest = verified ?: lastEditMs
        if (freshest != null && nowMs - freshest > 6 * YEAR_MS) warnings += Warning.OUTDATED

        return TrailAssessment(availability, warnings, verified, lastEditMs, quality(tags, isRelation, verified, lastEditMs, nowMs, warnings))
    }

    private fun isClosed(t: Map<String, String>): Boolean {
        if (t["access"] == "no" && t["foot"] !in setOf("yes", "designated", "permissive")) return true
        if (t["highway"] == "construction" || t["highway"] == "proposed" || t.containsKey("construction") || t.containsKey("proposed")) return true
        if (t["disused"] == "yes" || t["abandoned"] == "yes") return true
        if (t["state"] in setOf("proposed", "disused", "abandoned", "construction")) return true
        if (t["operational_status"] in setOf("closed", "abandoned", "disused") || t["status"] in setOf("closed", "abandoned")) return true
        if (t["route"] == "disused_hiking" || t["route"] == "proposed") return true
        // "disused:highway=path" and friends: the path is gone, only the memory is left in the data.
        return t.keys.any { key -> LIFECYCLE_PREFIXES.any { key.startsWith(it) && key.removePrefix(it) in LIFECYCLE_KEYS } } &&
            t["highway"] == null && t["route"] == null
    }

    private fun isRestricted(kind: OsmKind, t: Map<String, String>): Boolean {
        if (t["access"] in setOf("private", "no", "customers", "delivery", "military")) {
            // A specific permission for the way of travel wins over a general restriction.
            val own = when (kind) {
                OsmKind.HIKING -> t["foot"]
                OsmKind.MTB, OsmKind.CYCLING -> t["bicycle"]
                OsmKind.OFFROAD -> t["motor_vehicle"] ?: t["motorcar"]
            }
            if (own !in setOf("yes", "designated", "permissive")) return true
        }
        val own = when (kind) {
            OsmKind.HIKING -> t["foot"] ?: t["hiking"]
            OsmKind.MTB, OsmKind.CYCLING -> t["bicycle"]
            OsmKind.OFFROAD -> t["motor_vehicle"] ?: t["motorcar"]
        }
        if (own == "no" || own == "private") return true
        return t["military"] != null && t["access"] != "yes"
    }

    private fun isConditional(t: Map<String, String>): Boolean =
        (t["seasonal"] != null && t["seasonal"] != "no") ||
            t["winter_road"] == "yes" ||
            t.keys.any { it.startsWith("access:conditional") || it == "opening_hours" && !t[it].isNullOrBlank() } ||
            t["access"] == "permit" || t["access"] == "permissive" && t["fee"] == "yes"

    private fun quality(
        t: Map<String, String>, isRelation: Boolean, verifiedMs: Long?, lastEditMs: Long?, nowMs: Long, warnings: Set<Warning>
    ): Int {
        var q = 0
        if (isRelation) q += 3
        if (!t["name"].isNullOrBlank()) q += 2
        if (t["network"] in setOf("iwn", "nwn", "rwn", "lwn")) q += 2
        if (!t["ref"].isNullOrBlank()) q += 1
        if (t.containsKey("osmc:symbol") || t.containsKey("colour")) q += 1
        if (!t["operator"].isNullOrBlank() || !t["website"].isNullOrBlank() || !t["description"].isNullOrBlank()) q += 1
        val fresh = verifiedMs ?: lastEditMs
        if (fresh != null && nowMs - fresh < 3 * YEAR_MS) q += 1
        q -= warnings.count { it == Warning.HARD_TO_FOLLOW || it == Warning.NEEDS_CHECK || it == Warning.OUTDATED }
        return q.coerceIn(0, 10)
    }

    /** "2024-05-03", "2024-05" and "2024" are all dates people write in check_date. */
    internal fun parseDate(text: String?): Long? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            val parts = s.take(10).split("-")
            val year = parts[0].toInt()
            val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
            val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
            if (year !in 1990..2100) null else LocalDate.of(year, month.coerceIn(1, 12), day.coerceIn(1, 28)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
