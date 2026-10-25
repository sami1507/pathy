package com.example.pathy.data

data class SavedTrail(
    val id: String = "",
    val name: String = "",
    val distanceInMeters: Double = 0.0,
    val durationInMillis: Long = 0L,
    val difficulty: DifficultyLevel = DifficultyLevel.EASY,
    val rating: Float = 0f,
    val geoPoints: List<LatLngDTO> = emptyList(),
    val midpoint: LatLngDTO = LatLngDTO(),
    val locationName: String = "",
    val createdByUid: String = "",
    val tags: TrailTags? = null
)

enum class DifficultyLevel {
    EASY, MEDIUM, HARD, EXTREME
}
data class LatLngDTO(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

fun SavedTrail.toTrailData(): TrailData {
    return TrailData(
        id = this.id,
        name = this.name,
        geoPoints = this.geoPoints,
        midpoint = this.midpoint,
        isHikingTrail = false,
        distanceInMeters = this.distanceInMeters,
        durationInMillis = this.durationInMillis,
        difficulty = this.difficulty,
        locationName = this.locationName,
        imageUrls = emptyList(),
        tags = this.tags ?: TrailTags()
    )
}

fun from(trail: TrailData, createdByUid: String): SavedTrail {
    return SavedTrail(
        id = trail.id,
        name = trail.name,
        distanceInMeters = trail.distanceInMeters,
        durationInMillis = trail.durationInMillis,
        difficulty = trail.difficulty,
        geoPoints = trail.geoPoints,
        midpoint = trail.midpoint ?: LatLngDTO(),
        locationName = trail.locationName,
        createdByUid = createdByUid,
        tags = trail.tags
    )
}


