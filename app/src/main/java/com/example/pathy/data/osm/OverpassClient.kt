package com.example.pathy.data.osm

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Asks the Overpass servers (OpenStreetMap's public query service) for trails.
 *
 * The public servers are shared and ask clients to be polite, so this client
 *  - names itself in the User-Agent,
 *  - sends at most two requests at a time,
 *  - tries a second and a third server when the first is busy or down,
 *  - keeps every answer on disk for a few hours, so asking again costs nothing, and
 *  - falls back to an older saved answer when there is no connection (hikers often have none).
 */
object OverpassClient {
    private const val TAG = "OverpassClient"
    private const val USER_AGENT = "PATHY-Android/1.0 (hiking app; contact via Play Store listing)"
    private const val FRESH_MS = 6 * 60 * 60 * 1000L
    private const val STALE_OK_MS = 14 * 24 * 60 * 60 * 1000L
    private const val MAX_CACHE_FILES = 60

    private val servers = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    /** Where answers are kept. Set once by the app; without it nothing is cached. */
    @Volatile
    var cacheDir: File? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    private val gate = Semaphore(2)

    /** The JSON answer for [query], or null when no server answered and nothing is saved. */
    suspend fun query(query: String): String? {
        if (query.isBlank()) return null
        val file = cacheFile(query)
        readCache(file, FRESH_MS)?.let { return it }

        val fresh = gate.withPermit { askServers(query) }
        if (fresh != null) {
            writeCache(file, fresh)
            return fresh
        }
        // Offline or every server busy: an older answer is better than nothing.
        return readCache(file, STALE_OK_MS)
    }

    private suspend fun askServers(query: String): String? {
        for ((index, url) in servers.withIndex()) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .post(FormBody.Builder().add("data", query).build())
                    .build()
                http.newCall(request).await().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful && body.trimStart().startsWith("{") && body.contains("\"elements\"") -> return body
                        // Busy (429) or timed out (504): wait a moment and let the next server try.
                        response.code == 429 || response.code == 504 -> {
                            Log.w(TAG, "$url is busy (${response.code})")
                            if (index < servers.lastIndex) delay(1_500)
                        }
                        else -> Log.w(TAG, "$url answered ${response.code}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "$url failed: ${e.message}")
            }
        }
        return null
    }

    // ---- disk cache --------------------------------------------------------------------------

    private fun cacheFile(query: String): File? {
        val dir = cacheDir?.let { File(it, "overpass") } ?: return null
        val hash = MessageDigest.getInstance("SHA-1").digest(query.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private suspend fun readCache(file: File?, maxAgeMs: Long): String? = withContext(Dispatchers.IO) {
        if (file == null || !file.isFile) return@withContext null
        if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) return@withContext null
        runCatching { file.readText() }.getOrNull()
    }

    private suspend fun writeCache(file: File?, json: String) = withContext(Dispatchers.IO) {
        if (file == null) return@withContext
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(file)) tmp.delete()
            // Keep the folder small: remove the oldest answers beyond the limit.
            val all = file.parentFile?.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            if (all.size > MAX_CACHE_FILES) all.sortedBy { it.lastModified() }.take(all.size - MAX_CACHE_FILES).forEach { it.delete() }
        }
        Unit
    }
}

/** Waits for an OkHttp call without blocking a thread; cancelling the coroutine cancels the call. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
}
