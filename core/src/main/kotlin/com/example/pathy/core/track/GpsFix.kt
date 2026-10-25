package com.example.pathy.core.track

import com.example.pathy.core.geo.LatLon

/** One GPS reading, free of Android types. */
data class GpsFix(
    val lat: Double,
    val lon: Double,
    /** Metres above sea level as reported by the receiver, or null when unknown. */
    val altitudeM: Double? = null,
    /** Horizontal accuracy radius in metres, or null when unknown. */
    val accuracyM: Float? = null,
    /** Speed reported by the receiver in m/s, or null when unknown. */
    val speedMps: Float? = null,
    val timeMs: Long
) {
    val position: LatLon get() = LatLon(lat, lon)
}

/** The kinds of outing PATHY records. */
enum class SportType(
    /** Anything faster than this between two fixes is a GPS jump, not movement (m/s). */
    val maxPlausibleSpeedMps: Double,
    /** Below this speed the person is treated as standing still (m/s). */
    val stoppedBelowMps: Double,
    /** Metabolic equivalent used for the calorie estimate at an easy pace. */
    val baseMet: Double
) {
    HIKING(maxPlausibleSpeedMps = 7.0, stoppedBelowMps = 0.3, baseMet = 6.0),
    WALKING(maxPlausibleSpeedMps = 5.0, stoppedBelowMps = 0.3, baseMet = 3.8),
    RUNNING(maxPlausibleSpeedMps = 12.0, stoppedBelowMps = 0.6, baseMet = 9.8),
    CYCLING(maxPlausibleSpeedMps = 25.0, stoppedBelowMps = 0.8, baseMet = 7.5),
    MOUNTAIN_BIKING(maxPlausibleSpeedMps = 22.0, stoppedBelowMps = 0.8, baseMet = 8.5),
    OFFROAD(maxPlausibleSpeedMps = 40.0, stoppedBelowMps = 1.0, baseMet = 2.5)
}
