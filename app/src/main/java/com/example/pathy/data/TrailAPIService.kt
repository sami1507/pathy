package com.example.pathy.data

import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.osm.OsmKind
import com.example.pathy.data.osm.TrailRepository
import com.mapbox.geojson.Point

/**
 * Trails from OpenStreetMap for the discovery screen. The work (query, cache, parse, grade) is in
 * [TrailRepository] and the pure code under core/osm; this class only picks the kinds and maps the result.
 */
class TrailAPIService {

    companion object {
        const val TYPE_HIKING = "hiking"
        const val TYPE_MTB = "mountain-biking"
        const val TYPE_OFFROAD = "offroad"
        const val TYPE_CYCLING = "cycling"
        const val TYPE_RUNNING = "running"
    }

    /** The trails of one search, with what was left out and how wide the search had to go. */
    data class Discovery(val trails: List<EnhancedTrailData>, val hiddenClosed: Int, val radiusKm: Double, val failed: Boolean = false)

    private fun kindsOf(types: List<String>): Set<OsmKind> = types.mapNotNull {
        when (it) {
            TYPE_HIKING, TYPE_RUNNING -> OsmKind.HIKING
            TYPE_MTB -> OsmKind.MTB
            TYPE_CYCLING -> OsmKind.CYCLING
            TYPE_OFFROAD -> OsmKind.OFFROAD
            else -> null
        }
    }.toSet()

    /** Trails around [location] of the requested [types], nearest first. Closed and private ones are left out. */
    suspend fun fetchAllTrails(
        location: Point,
        radiusKm: Double = 50.0,
        types: List<String> = listOf(TYPE_HIKING, TYPE_MTB, TYPE_CYCLING, TYPE_OFFROAD)
    ): List<EnhancedTrailData> {
        val center = LatLon(location.latitude(), location.longitude())
        return TrailRepository.nearby(center, (radiusKm * 1000).toInt(), kindsOf(types))
            .map { TrailRepository.toEnhanced(it, center) }
    }

    /**
     * Trails around any point in the world. When fewer than [minResults] usable trails are found the search
     * widens (up to [maxRadiusKm]) so the person is shown the nearest ones instead of an empty list.
     */
    suspend fun discover(
        location: Point,
        radiusKm: Double,
        types: List<String>,
        minResults: Int = 6,
        maxRadiusKm: Double = 60.0
    ): Discovery {
        val center = LatLon(location.latitude(), location.longitude())
        val found = TrailRepository.nearbyExpanding(
            center, (radiusKm * 1000).toInt(), (maxRadiusKm * 1000).toInt(), minResults, kindsOf(types)
        )
        return Discovery(found.trails.map { TrailRepository.toEnhanced(it, center) }, found.hiddenClosed, found.radiusM / 1000.0, found.failed)
    }
}

/** A trail as listed on the discovery screen. */
data class EnhancedTrailData(
    val id: String,
    val name: String,
    val description: String,
    val trailType: String, // hiking, mtb, offroad, cycling
    val difficulty: DifficultyLevel,
    val distanceInMeters: Double,
    /** Climb in metres when known; null means not known yet (never guessed). */
    val elevationGain: Int?,
    val estimatedDuration: Long,
    val rating: Float, // 0-5
    val reviewCount: Int,
    val geoPoints: List<LatLngDTO>,
    val midpoint: LatLngDTO,
    val distanceFromUser: Double?,
    val locationName: String,
    val features: List<String>, // viewpoint, waterfall, forest, etc.
    val photoUrls: List<String>,
    val surface: String, // paved, gravel, dirt, etc.
    val isLoop: Boolean,
    val tags: TrailTags,
    val info: TrailInfo? = null,

    // Additional premium features
    val conditions: TrailConditions? = null,
    val weather: TrailWeather? = null,
    val completionCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class TrailConditions(
    val status: String, // "good", "muddy", "snow", "closed"
    val lastReported: Long,
    val reportedBy: String,
    val notes: String
)

data class TrailWeather(
    val temperature: Int,
    val condition: String,
    val windSpeed: Int,
    val precipitation: Int
)

fun EnhancedTrailData.toTrailData(): TrailData {
    return TrailData(
        id = this.id,
        name = this.name,
        geoPoints = this.geoPoints,
        midpoint = this.midpoint,
        isHikingTrail = this.trailType == TrailAPIService.TYPE_HIKING,
        distanceInMeters = this.distanceInMeters,
        durationInMillis = this.estimatedDuration,
        difficulty = this.difficulty,
        locationName = this.locationName,
        imageUrls = this.photoUrls,
        tags = this.tags,
        info = this.info
    )
}

enum class TrailType {
    HIKING, MTB, CYCLING, OFFROAD;

    fun toApiConstant(): String = when (this) {
        HIKING -> TrailAPIService.TYPE_HIKING
        MTB -> TrailAPIService.TYPE_MTB
        CYCLING -> TrailAPIService.TYPE_CYCLING
        OFFROAD -> TrailAPIService.TYPE_OFFROAD
    }
}
