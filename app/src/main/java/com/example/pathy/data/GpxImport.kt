package com.example.pathy.data

import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.gpx.Gpx
import com.example.pathy.core.gpx.GpxTrack
import com.example.pathy.core.osm.TrailBuilder
import com.example.pathy.core.track.ElevationAccumulator
import com.example.pathy.core.trail.Grade
import com.example.pathy.core.trail.TrailMetrics
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Turns a GPX file from a website, a friend or another app into a trail PATHY can show and guide along.
 */
object GpxImport {
    /** A route file is a few hundred KB; anything near this size is not a route. */
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MIN_LENGTH_M = 100.0

    /** Reads and parses the file. Throws [IllegalArgumentException] when it is not a usable GPX route. */
    fun read(input: InputStream): GpxTrack {
        val bytes = input.readBounded(MAX_BYTES)
        return try {
            Gpx.parse(bytes.inputStream())
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a readable GPX file", e)
        }
    }

    fun toTrailData(track: GpxTrack): TrailData {
        val line = track.points.map { LatLon(it.lat, it.lon) }
        require(line.size >= 2) { "The route has fewer than two points" }
        val distance = Geo.length(line)
        require(distance >= MIN_LENGTH_M) { "The route is too short to follow" }

        // Climb only when the file carries altitudes for (nearly) every point; otherwise unknown.
        val altitudes = track.points.mapNotNull { it.altitudeM }
        val gain: Double?
        val highest: Double?
        if (altitudes.size >= track.points.size * 0.8 && altitudes.size >= 2) {
            val acc = ElevationAccumulator()
            altitudes.forEach { acc.add(it) }
            gain = acc.gainM
            highest = acc.maxM
        } else {
            gain = null
            highest = null
        }

        val path = TrailBuilder.thin(line)
        val grade = TrailMetrics.hikingGrade(distance, gain, null)
        val minutes = TrailMetrics.durationMinutes(TrailMetrics.Kind.HIKING, distance, gain)
        val middle = path[path.size / 2]
        return TrailData(
            id = idFor(line, distance),
            name = track.name,
            geoPoints = path.map { LatLngDTO(it.lat, it.lon) },
            midpoint = LatLngDTO(middle.lat, middle.lon),
            isHikingTrail = true,
            distanceInMeters = distance,
            durationInMillis = minutes * 60_000L,
            difficulty = when (grade) {
                Grade.EASY -> DifficultyLevel.EASY
                Grade.MODERATE -> DifficultyLevel.MEDIUM
                Grade.HARD -> DifficultyLevel.HARD
                Grade.EXTREME -> DifficultyLevel.EXTREME
            },
            locationName = "",
            tags = TrailTags(
                elevationGain = gain?.toInt(),
                maxElevation = highest?.toInt(),
                suitableFor = listOf("הליכה")
            )
        )
    }

    /** The same file always gives the same id, so importing it twice does not make two trails. */
    internal fun idFor(line: List<LatLon>, distanceM: Double): String {
        val seed = "${line.first()}|${line.last()}|${distanceM.toLong()}|${line.size}"
        val hash = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray())
        return "gpx_" + hash.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > limit) throw IOException("The file is too large to be a route")
        }
        return out.toByteArray()
    }
}
