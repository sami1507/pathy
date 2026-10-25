package com.example.pathy

import android.location.Location
import com.mapbox.geojson.Point
import kotlin.math.*
import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.size
import com.example.pathy.data.LatLngDTO
import com.example.pathy.core.geo.LatLon
import com.example.pathy.data.osm.ElevationService
import com.mapbox.maps.CoordinateBounds
import java.util.*

fun Point.distanceTo(other: Point): Double {
    val R = 6378000.0 // רדיוס כדור הארץ במטרים
    val dLat = Math.toRadians(other.latitude() - this.latitude())
    val dLon = Math.toRadians(other.longitude() - this.longitude())

    val lat1 = Math.toRadians(this.latitude())
    val lat2 = Math.toRadians(other.latitude())

    val a = sin(dLat / 2).pow(2.0) + sin(dLon / 2).pow(2.0) * cos(lat1) * cos(lat2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return R * c
}

fun pointToAndroidLocation(point: Point): Location {
    return Location("mapbox").apply {
        latitude = point.latitude()
        longitude = point.longitude()
    }
}

fun getMiddlePointLocationName(context: Context, geoPoints: List<LatLngDTO>): String {
    return try {
        if (geoPoints.isEmpty()) return context.getString(R.string.unknown_location)

        val middleIndex = geoPoints.size / 2
        val middlePoint = geoPoints[middleIndex]
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses = geocoder.getFromLocation(middlePoint.latitude, middlePoint.longitude, 1)

        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            address.locality ?: address.subAdminArea ?: address.adminArea ?: context.getString(R.string.unknown_location)
        } else {
            context.getString(R.string.unknown_location)
        }
    } catch (e: Exception) {
        context.getString(R.string.unknown_location)
    }
}

fun interpolatePoints(start: Point, end: Point, steps: Int): List<Point> {
    val interpolated = mutableListOf<Point>()
    for (i in 0..steps) {
        val t = i.toDouble() / steps
        val lon = start.longitude() + t * (end.longitude() - start.longitude())
        val lat = start.latitude() + t * (end.latitude() - start.latitude())
        interpolated.add(Point.fromLngLat(lon, lat))
    }
    return interpolated
}

/**
 * Climb and highest point of a trail from real terrain data, or null when the elevation service cannot
 * be reached. Never an estimate: the screens show "unavailable" for null.
 */
suspend fun trailClimb(points: List<LatLngDTO>): Pair<Int, Int>? {
    val climb = ElevationService.climb(points.map { LatLon(it.latitude, it.longitude) }) ?: return null
    return climb.gainM.toInt() to climb.maxM.toInt()
}

fun convertToLatLngDTO(points: List<Point>): List<LatLngDTO> {
    return points.map { point -> LatLngDTO(point.latitude(), point.longitude()) }
}

fun calculateBounds(points: List<Point>): CoordinateBounds {
    val minLat = points.minOf { it.latitude() }
    val maxLat = points.maxOf { it.latitude() }
    val minLng = points.minOf { it.longitude() }
    val maxLng = points.maxOf { it.longitude() }
    return CoordinateBounds(
        Point.fromLngLat(minLng, minLat),
        Point.fromLngLat(maxLng, maxLat)
    )
}
