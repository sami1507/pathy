package com.example.pathy.core.geo

import kotlin.math.roundToLong

/**
 * Google's encoded polyline format (the same one Mapbox and Google Maps use) at 6 decimal digits,
 * about 11 cm. A recorded route of 10 000 points becomes a string of roughly 60 KB, so a whole
 * activity fits in one Firestore document instead of one document per point.
 */
object PolylineCodec {
    private const val SCALE = 1e6

    fun encode(points: List<LatLon>): String {
        val out = StringBuilder(points.size * 6)
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = (p.lat * SCALE).roundToLong()
            val lon = (p.lon * SCALE).roundToLong()
            append(out, lat - prevLat)
            append(out, lon - prevLon)
            prevLat = lat
            prevLon = lon
        }
        return out.toString()
    }

    fun decode(encoded: String): List<LatLon> {
        val out = ArrayList<LatLon>(encoded.length / 6)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val (dLat, afterLat) = read(encoded, index)
            if (afterLat >= encoded.length) break // a latitude with no longitude: truncated data
            val (dLon, afterLon) = read(encoded, afterLat)
            index = afterLon
            lat += dLat
            lon += dLon
            out += LatLon(lat / SCALE, lon / SCALE)
        }
        return out
    }

    private fun append(out: StringBuilder, value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append((((v and 0x1f) or 0x20) + 63).toInt().toChar())
            v = v shr 5
        }
        out.append((v + 63).toInt().toChar())
    }

    private fun read(s: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < s.length) {
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }
}
