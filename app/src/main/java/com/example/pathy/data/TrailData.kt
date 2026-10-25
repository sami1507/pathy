package com.example.pathy.data

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TrailData(
    val id: String,
    val name: String,
    val geoPoints: List<LatLngDTO> = emptyList(),
    val midpoint: LatLngDTO? = null,
    val isHikingTrail: Boolean,
    val distanceInMeters: Double = 0.0,
    val durationInMillis: Long = 0L,
    val difficulty: DifficultyLevel = DifficultyLevel.EASY,
    val locationName: String,
    val imageUrls: List<String> = emptyList(),
    val tags: TrailTags = TrailTags(),
    /** What is known about whether the trail can be used and how recent that is (null for older saved trails). */
    val info: TrailInfo? = null
) {

    //empty for links
    @Keep
    constructor() : this(
        id = "",
        name = "",
        geoPoints = emptyList(),
        midpoint = LatLngDTO(0.0, 0.0),
        isHikingTrail = false,
        distanceInMeters = 0.0,
        durationInMillis = 0L,
        difficulty = DifficultyLevel.EASY,
        locationName = "",
        imageUrls = emptyList()
    )

    companion object {
        fun toJson(list: List<TrailData>): String = Gson().toJson(list)
        fun fromJson(json: String): List<TrailData> {
            val type = object : TypeToken<List<TrailData>>() {}.type
            return Gson().fromJson(json, type)
        }
    }
}

/**
 * Facts that help a person decide whether to go: the trail's paint mark, whether it is part of a
 * walking network, warnings from the map data, and how recently someone looked at it.
 */
data class TrailInfo(
    /** red, blue, green... when the route is waymarked. */
    val waymark: String? = null,
    /** iwn, nwn, rwn or lwn. */
    val network: String? = null,
    /** SEASONAL, HARD_TO_FOLLOW, NEEDS_CHECK, OUTDATED. */
    val warnings: List<String> = emptyList(),
    /** When the data was last checked on the ground, or failing that last edited (epoch ms). */
    val updatedMs: Long? = null,
    /** True when updatedMs is a ground check rather than an edit. */
    val verified: Boolean = false,
    /** 0..10, used to put the best trails first. */
    val quality: Int = 0
)

data class OfflineTrailData(
    val trail: TrailData,
    val tileUrls: List<String>
)

data class TrailTags(
    val isForest: Boolean = false,
    val hasWater: Boolean = false,
    val hasViewpoint: Boolean = false,
    val hasExtremeSection: Boolean = false,
    val isAccessibility: Boolean = false,
    val elevationGain: Int? = null,
    val maxElevation: Int? = null,
    val suitableFor: List<String> = emptyList(),
    val suggestedGroupKind: List<String> = emptyList(),
)

enum class TrailKind { HIKING, BICYCLE, CAR }

object NearbyTrailsHolder {
    private val _trails = MutableStateFlow<List<TrailData>>(emptyList())
    val trails: StateFlow<List<TrailData>> = _trails
    fun update(list: List<TrailData>) { _trails.value = list }
}
