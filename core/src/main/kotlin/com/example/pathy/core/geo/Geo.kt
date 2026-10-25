package com.example.pathy.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A WGS84 position. The core package has no Android or Mapbox types, so it runs in plain JVM tests. */
data class LatLon(val lat: Double, val lon: Double)

/** Spherical-earth maths: good to about 0.5% over trail distances, which is far below GPS error. */
object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun rad(deg: Double) = deg * PI / 180.0
    private fun deg(rad: Double) = rad * 180.0 / PI

    /** Great-circle (haversine) distance in metres. */
    fun distance(a: LatLon, b: LatLon): Double = distance(a.lat, a.lon, b.lat, b.lon)

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = rad(lat2 - lat1)
        val dLon = rad(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(rad(lat1)) * cos(rad(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial compass bearing from [from] to [to], in degrees clockwise from north, 0 until 360. */
    fun bearing(from: LatLon, to: LatLon): Double {
        val lat1 = rad(from.lat)
        val lat2 = rad(to.lat)
        val dLon = rad(to.lon - from.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalize(deg(atan2(y, x)))
    }

    /** Wraps any angle to 0 until 360. */
    fun normalize(degrees: Double): Double {
        val r = degrees % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /**
     * Signed turn needed to go from heading [from] to heading [to], in -180..180.
     * Positive means turn right (clockwise), negative means turn left.
     */
    fun turnBetween(from: Double, to: Double): Double {
        var d = normalize(to) - normalize(from)
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    /** The point reached by travelling [distanceM] from [from] on heading [bearingDeg]. */
    fun destination(from: LatLon, bearingDeg: Double, distanceM: Double): LatLon {
        val ang = distanceM / EARTH_RADIUS_M
        val b = rad(bearingDeg)
        val lat1 = rad(from.lat)
        val lon1 = rad(from.lon)
        val lat2 = asin(sin(lat1) * cos(ang) + cos(lat1) * sin(ang) * cos(b))
        val lon2 = lon1 + atan2(sin(b) * sin(ang) * cos(lat1), cos(ang) - sin(lat1) * sin(lat2))
        return LatLon(deg(lat2), ((deg(lon2) + 540.0) % 360.0) - 180.0)
    }

    fun length(points: List<LatLon>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        return total
    }

    /** True when the angle is within [tolerance] degrees of [target], wrapping around north. */
    fun isNear(angle: Double, target: Double, tolerance: Double) = abs(turnBetween(angle, target)) <= tolerance
}
