package com.example.pathy.data.osm

import android.util.Log
import com.example.pathy.core.geo.Geo
import com.example.pathy.core.geo.LatLon
import com.example.pathy.core.trail.ElevationProfile
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Real elevations for a trail, from the Open-Meteo elevation service (a terrain model, free, no key).
 *
 * The old code asked a different public service for every vertex, 50 at a time, so one long trail made
 * dozens of requests and any failure silently gave a wrong climb. Here a trail is sampled about every
 * 50 m (at most [MAX_SAMPLES] points), asked for in batches of 100, and a failure gives null - the
 * screens then say "unavailable" instead of showing a made-up number.
 */
object ElevationService {
    private const val TAG = "ElevationService"
    private const val BATCH = 100
    private const val MAX_SAMPLES = 300
    private const val SAMPLE_SPACING_M = 50.0

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val memory = object : LinkedHashMap<String, ElevationProfile.Climb>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ElevationProfile.Climb>?) = size > 40
    }

    /** Climb and highest point of [path], or null when the service cannot be reached. */
    suspend fun climb(path: List<LatLon>): ElevationProfile.Climb? {
        if (path.size < 2) return null
        val key = "${path.size}:${path.first()}:${path.last()}"
        synchronized(memory) { memory[key] }?.let { return it }

        val total = Geo.length(path)
        val count = (total / SAMPLE_SPACING_M).toInt().coerceIn(2, MAX_SAMPLES)
        val indices = ElevationProfile.sampleIndices(path, count)
        val elevations = ArrayList<Double>(indices.size)
        for (chunk in indices.chunked(BATCH)) {
            val batch = fetch(chunk.map { path[it] }) ?: return null
            elevations += batch
        }
        val result = ElevationProfile.climb(elevations) ?: return null
        synchronized(memory) { memory[key] = result }
        return result
    }

    private suspend fun fetch(points: List<LatLon>): List<Double>? {
        val lats = points.joinToString(",") { String.format(Locale.US, "%.5f", it.lat) }
        val lons = points.joinToString(",") { String.format(Locale.US, "%.5f", it.lon) }
        val request = Request.Builder()
            .url("https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons")
            .header("User-Agent", "PATHY-Android/1.0")
            .build()
        return try {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Elevation service answered ${response.code}")
                    return null
                }
                val values = JsonParser.parseString(response.body?.string().orEmpty())
                    .asJsonObject.getAsJsonArray("elevation") ?: return null
                if (values.size() != points.size) return null
                values.map { it.asDouble }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Elevation request failed: ${e.message}")
            null
        } catch (e: RuntimeException) {
            // An unexpected answer shape: treat as unavailable rather than crash the screen.
            Log.w(TAG, "Unreadable elevation answer: ${e.message}")
            null
        }
    }

}
