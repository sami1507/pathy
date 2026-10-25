package com.example.pathy.core.osm

/** What a trail passes through or is good for, as far as its OpenStreetMap tags and name tell. */
data class TrailFlags(
    val forest: Boolean,
    val water: Boolean,
    val viewpoint: Boolean,
    val extremeSection: Boolean,
    val accessible: Boolean,
    val walking: Boolean,
    val bicycle: Boolean,
    val vehicle: Boolean,
    val family: Boolean,
    val smallChildren: Boolean,
    val couple: Boolean
)

/**
 * Reads the flags from tags. The name is searched for words in English, Arabic and Hebrew, because
 * most of the trails PATHY lists are named in the local language.
 */
object TrailTagging {

    private val FOREST = listOf("wood", "forest", "nature_reserve", "park", "grove", "trees", "غابة", "حرش", "محمية", "יער", "חורש", "שמור", "גן לאומי", "פארק")
    private val WATER = listOf("water", "stream", "river", "reservoir", "lake", "spring", "pond", "canal", "نهر", "وادي", "عين", "ينبوع", "بحيرة", "جدول", "נחל", "מעיין", "בריכה", "אגם", "יובל")
    private val VIEW = listOf("viewpoint", "lookout", "scenic", "panorama", "observation", "مطل", "إطلالة", "تلة", "תצפית", "מצפור")
    private val EXTREME = listOf("extreme", "cliff", "rocky", "technical", "scramble", "وعر", "صخري", "منحدر", "אקסטרים", "מצוק", "אתגרי", "סלעי", "טכני")
    private val FAMILY = listOf("family", "משפחה", "عائل")
    private val CHILDREN = listOf("kids", "children", "ילדים", "أطفال")
    private val COUPLE = listOf("romantic", "זוג", "רומנטי", "رومانس")

    fun flags(tags: Map<String, String>, kind: OsmKind): TrailFlags {
        val name = tags["name"].orEmpty().lowercase()
        fun nameHas(words: List<String>) = words.any { name.contains(it) }
        fun tagHas(keys: List<String>, words: List<String>) =
            keys.any { key -> tags[key]?.lowercase()?.let { v -> words.any { v.contains(it) } } == true }

        val foot = tags["foot"] in YES
        val bike = tags["bicycle"] in YES
        val motor = tags["motor_vehicle"] in YES || tags["highway"] == "track"
        val byKind = when (kind) {
            OsmKind.HIKING -> Triple(true, false, false)
            OsmKind.MTB, OsmKind.CYCLING -> Triple(false, true, false)
            OsmKind.OFFROAD -> Triple(false, false, true)
        }

        return TrailFlags(
            forest = tagHas(listOf("natural", "landuse", "wood", "leisure"), FOREST) || nameHas(FOREST),
            water = tagHas(listOf("natural", "waterway", "landuse"), WATER) || nameHas(WATER),
            viewpoint = tags["tourism"] == "viewpoint" || tags.containsKey("viewpoint") || tags["man_made"] == "tower" || nameHas(VIEW),
            extremeSection = tags["sac_scale"].orEmpty().let { it.contains("demanding") || it.contains("mountain") || it.contains("alpine") } ||
                tags["trail_visibility"].orEmpty().contains("bad") ||
                (tags["mtb:scale"]?.toIntOrNull() ?: 0) >= 3 || nameHas(EXTREME),
            accessible = tags["wheelchair"] in YES || tags["wheelchair"] == "limited" || tags["accessibility"] in YES,
            walking = foot || byKind.first,
            bicycle = bike || byKind.second,
            vehicle = (motor && kind == OsmKind.OFFROAD) || byKind.third,
            family = nameHas(FAMILY) || tags["family_friendly"] == "yes",
            smallChildren = nameHas(CHILDREN) || tags["children"] == "yes",
            couple = nameHas(COUPLE)
        )
    }

    private val YES = setOf("yes", "designated")
}
