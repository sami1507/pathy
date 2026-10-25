package com.example.pathy.data.osm

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.osm.OsmKind
import com.example.pathy.core.osm.OverpassParser
import com.example.pathy.core.osm.OverpassQueries
import com.example.pathy.core.osm.TrailBuilder
import com.example.pathy.core.osm.TrailCandidate
import com.example.pathy.core.osm.TrailTagging
import com.example.pathy.core.trail.Grade
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.EnhancedTrailData
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailInfo
import com.example.pathy.data.TrailTags
import com.example.pathy.i18n.AppLocale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * Trails near a place. Every screen asks here, so the same area is never fetched twice in a row: the
 * search point is snapped to a grid of about 500 m, answers are kept in memory for ten minutes, and
 * the client behind keeps them on disk.
 */
object TrailRepository {
    private const val GRID_DEG = 0.005
    private const val MEMORY_MS = 10 * 60 * 1000L
    private const val FAILURE_MS = 60 * 1000L

    private data class Key(val cellLat: Long, val cellLon: Long, val radiusM: Int, val kinds: Set<OsmKind>, val language: String)
    private class Entry(val at: Long, val trails: List<TrailCandidate>, val failed: Boolean = false)

    private val memory = LinkedHashMap<Key, Entry>()
    private val mutex = Mutex()

    @Volatile
    private var appContext: android.content.Context? = null

    /** Called once by the app: gives the Overpass client a folder to keep answers in, and the language to name trails in. */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        OverpassClient.cacheDir = context.applicationContext.cacheDir
    }

    private fun appLanguage(): String? = appContext?.let { AppLocale.current(it) }

    /** Trails found around a place, the closed or private ones counted but left out, and how far the search went. */
    data class TrailSearch(val trails: List<TrailCandidate>, val hiddenClosed: Int, val radiusM: Int, val failed: Boolean = false)

    /**
     * Trails within [radiusM] of [center] of the given [kinds], nearest first. Trails the map data marks
     * closed, abandoned, planned or private are never returned. Returns an empty list when nothing could
     * be fetched; a failure never throws to the screen.
     */
    suspend fun nearby(
        center: LatLon,
        radiusM: Int,
        kinds: Set<OsmKind> = OsmKind.values().toSet(),
        language: String? = appLanguage()
    ): List<TrailCandidate> = search(center, radiusM, kinds, language).trails

    /**
     * Like [nearby], but widens the search (2x each time, up to [maxRadiusM]) until at least [minResults]
     * usable trails are found, so a person in a quiet area is shown the nearest trails instead of an empty screen.
     */
    suspend fun nearbyExpanding(
        center: LatLon,
        startRadiusM: Int,
        maxRadiusM: Int,
        minResults: Int,
        kinds: Set<OsmKind> = OsmKind.values().toSet(),
        language: String? = appLanguage()
    ): TrailSearch {
        var radius = startRadiusM
        var result = search(center, radius, kinds, language)
        while (!result.failed && result.trails.size < minResults && radius < maxRadiusM) {
            radius = minOf(radius * 2, maxRadiusM)
            result = search(center, radius, kinds, language)
        }
        return result
    }

    private suspend fun search(center: LatLon, radiusM: Int, kinds: Set<OsmKind>, language: String?): TrailSearch =
        withContext(Dispatchers.Default) {
            if (kinds.isEmpty()) return@withContext TrailSearch(emptyList(), 0, radiusM)
            val cellLat = floor(center.lat / GRID_DEG).toLong()
            val cellLon = floor(center.lon / GRID_DEG).toLong()
            val key = Key(cellLat, cellLon, radiusM, kinds, language.orEmpty())

            // One request at a time per repository: a second caller for the same area waits and then reuses the answer.
            mutex.withLock {
                val now = System.currentTimeMillis()
                memory[key]?.takeIf { now - it.at < MEMORY_MS }?.let { return@withLock result(it.trails, center, radiusM).copy(failed = it.failed) }

                // Search from the middle of the grid cell, wide enough to still cover the real position.
                val snapped = LatLon((cellLat + 0.5) * GRID_DEG, (cellLon + 0.5) * GRID_DEG)
                val query = OverpassQueries.nearby(snapped, radiusM + 600, kinds)
                val json = try {
                    OverpassClient.query(query)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (json == null) {
                    // Remember the failure for a minute, so a weak connection is not asked again at every step.
                    memory[key] = Entry(now - MEMORY_MS + FAILURE_MS, emptyList(), failed = true)
                    return@withLock TrailSearch(emptyList(), 0, radiusM, failed = true)
                }

                val trails = try {
                    TrailBuilder.dedupe(OverpassParser.parse(json).mapNotNull { TrailBuilder.from(it, language, now) })
                        .filter { it.kind in kinds }
                } catch (e: Exception) {
                    emptyList()
                }
                memory[key] = Entry(now, trails)
                while (memory.size > 12) memory.remove(memory.keys.first())
                result(trails, center, radiusM)
            }
        }

    private fun result(all: List<TrailCandidate>, center: LatLon, radiusM: Int): TrailSearch {
        val usable = all.filter { it.assessment.isListable }
        return TrailSearch(sortedByDistance(usable, center), all.size - usable.size, radiusM)
    }

    private fun sortedByDistance(trails: List<TrailCandidate>, center: LatLon): List<TrailCandidate> =
        trails.sortedBy { Geo.distance(center, it.midpoint) }

    // ---- mapping to the shapes the screens already use -------------------------------------------

    fun difficulty(grade: Grade): DifficultyLevel = when (grade) {
        Grade.EASY -> DifficultyLevel.EASY
        Grade.MODERATE -> DifficultyLevel.MEDIUM
        Grade.HARD -> DifficultyLevel.HARD
        Grade.EXTREME -> DifficultyLevel.EXTREME
    }

    // Stored trail data keeps Hebrew labels for "suitable for" (the screens map them to the app language).
    private const val WALKING = "הליכה"
    private const val BICYCLE = "אופניים"
    private const val VEHICLE = "רכב"
    private const val FAMILY = "משפחה"
    private const val SMALL_CHILDREN = "ילדים קטנים"
    private const val COUPLE = "זוג‍️"

    fun tagsOf(c: TrailCandidate): TrailTags {
        val f = TrailTagging.flags(c.tags, c.kind)
        return TrailTags(
            isForest = f.forest,
            hasWater = f.water,
            hasViewpoint = f.viewpoint,
            hasExtremeSection = f.extremeSection,
            isAccessibility = f.accessible,
            elevationGain = c.gainM?.toInt(),
            suitableFor = buildList {
                if (f.walking) add(WALKING)
                if (f.bicycle) add(BICYCLE)
                if (f.vehicle) add(VEHICLE)
            },
            suggestedGroupKind = buildList {
                if (f.family) add(FAMILY)
                if (f.smallChildren) add(SMALL_CHILDREN)
                if (f.couple) add(COUPLE)
            }
        )
    }

    private fun dto(p: LatLon) = LatLngDTO(p.lat, p.lon)

    fun infoOf(c: TrailCandidate): TrailInfo = TrailInfo(
        waymark = c.waymark,
        network = c.network,
        warnings = c.assessment.warnings.map { it.name },
        updatedMs = c.assessment.freshestMs,
        verified = c.assessment.verifiedAtMs != null,
        quality = c.assessment.quality
    )

    fun toTrailData(c: TrailCandidate): TrailData = TrailData(
        id = c.id,
        name = c.name.orEmpty(),
        geoPoints = c.path.map(::dto),
        midpoint = dto(c.midpoint),
        isHikingTrail = c.kind == OsmKind.HIKING,
        distanceInMeters = c.distanceM,
        durationInMillis = c.durationMin * 60_000L,
        difficulty = difficulty(c.grade),
        locationName = c.tags["place"].orEmpty(),
        imageUrls = emptyList(),
        tags = tagsOf(c),
        info = infoOf(c)
    )

    fun toEnhanced(c: TrailCandidate, user: LatLon?): EnhancedTrailData {
        val f = TrailTagging.flags(c.tags, c.kind)
        return EnhancedTrailData(
            id = c.id,
            name = c.name.orEmpty(),
            description = c.tags["description"].orEmpty(),
            trailType = when (c.kind) {
                OsmKind.HIKING -> "hiking"
                OsmKind.MTB -> "mountain-biking"
                OsmKind.CYCLING -> "cycling"
                OsmKind.OFFROAD -> "offroad"
            },
            difficulty = difficulty(c.grade),
            distanceInMeters = c.distanceM,
            elevationGain = c.gainM?.toInt(),
            estimatedDuration = c.durationMin * 60_000L,
            rating = 0f,
            reviewCount = 0,
            geoPoints = c.path.map(::dto),
            midpoint = dto(c.midpoint),
            distanceFromUser = user?.let { Geo.distance(it, c.midpoint) },
            locationName = c.tags["place"].orEmpty(),
            features = buildList {
                if (f.viewpoint) add("Viewpoint")
                if (f.water) add("Waterfall")
                if (f.forest) add("Forest")
                if (f.accessible) add("Accessible")
                if (c.kind == OsmKind.MTB) add("Technical")
                if (c.tags["4wd_only"] == "yes") add("4WD Only")
            },
            photoUrls = emptyList(),
            surface = c.surface ?: "unknown",
            isLoop = c.isLoop,
            tags = tagsOf(c),
            info = infoOf(c)
        )
    }

    /** The app language as OSM `name:xx` code. */
    fun osmLanguage(context: android.content.Context): String = AppLocale.current(context)
}
