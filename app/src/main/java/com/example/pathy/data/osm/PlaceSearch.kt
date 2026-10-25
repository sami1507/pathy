package com.example.pathy.data.osm

import android.util.Log
import com.example.pathy.core.osm.Place
import com.example.pathy.core.osm.PlaceSearchParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Finds a place by name anywhere in the world, so trails can be explored before travelling there.
 *
 * Uses the OpenStreetMap place-search service, which asks apps to identify themselves, to send no more than
 * one request a second, and not to search while the person is still typing. This class does all three:
 * the screen calls it once, when the person presses search, and answers are remembered.
 */
object PlaceSearch {
    private const val TAG = "PlaceSearch"
    private const val USER_AGENT = "PATHY-Android/1.0 (hiking app; contact via Play Store listing)"
    private const val MIN_GAP_MS = 1_100L

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val gate = Mutex()
    private var lastRequestAt = 0L
    private val remembered = object : LinkedHashMap<String, List<Place>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?) = size > 30
    }

    /** Matching places, best first; an empty list when nothing matches or there is no connection. */
    suspend fun search(query: String, language: String): List<Place> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val key = "$language|${q.lowercase()}"
        synchronized(remembered) { remembered[key] }?.let { return it }

        return gate.withLock {
            val wait = lastRequestAt + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastRequestAt = System.currentTimeMillis()

            val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("limit", "6")
                .addQueryParameter("accept-language", language)
                .addQueryParameter("addressdetails", "0")
                .build()
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            try {
                http.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Place search answered ${response.code}")
                        return@withLock emptyList()
                    }
                    val places = PlaceSearchParser.parse(response.body?.string().orEmpty())
                    synchronized(remembered) { remembered[key] = places }
                    places
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Place search failed: ${e.message}")
                emptyList()
            }
        }
    }
}
