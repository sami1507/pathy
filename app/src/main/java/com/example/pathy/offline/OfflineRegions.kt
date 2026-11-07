package com.example.pathy.offline

import com.example.pathy.data.LatLngDTO
import com.mapbox.bindgen.Value
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegionCallback
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileRegionLoadProgressCallback
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.max

/**
 * Saves the map around a trail on the phone, so the trail can be followed without a connection.
 *
 * This uses Mapbox's own offline storage. The previous version fetched thousands of tiles in a loop from
 * the volunteer-run openstreetmap.org tile servers (which their usage policy forbids) and then served
 * them to the map through a web server listening on the phone. The map itself now does the work, and a
 * downloaded area is used automatically whenever the map has no connection.
 */
object OfflineRegions {
    /** The style offline maps are saved in; the offline screen must show the same one. */
    const val STYLE = Style.OUTDOORS

    private const val MIN_ZOOM: Byte = 10
    private const val MARGIN_M = 500.0

    fun regionId(trailId: String) = "trail-$trailId"

    /** Zoom as detailed as is sensible for the size of the area: a long trail gets less detail so the download stays small. */
    internal fun maxZoomFor(spanKm: Double): Byte = when {
        spanKm <= 8 -> 16
        spanKm <= 20 -> 15
        spanKm <= 50 -> 14
        else -> 13
    }

    /** The square (with a margin) that covers [path], as a map polygon, and its longer side in km. */
    internal fun areaAround(path: List<LatLngDTO>): Pair<Polygon, Double> {
        require(path.size >= 2) { "A trail needs at least two points" }
        val south = path.minOf { it.latitude }
        val north = path.maxOf { it.latitude }
        val west = path.minOf { it.longitude }
        val east = path.maxOf { it.longitude }
        val dLat = MARGIN_M / 111_320.0
        val dLon = MARGIN_M / (111_320.0 * max(0.01, cos(Math.toRadians((south + north) / 2))))
        val ring = listOf(
            Point.fromLngLat(west - dLon, south - dLat),
            Point.fromLngLat(east + dLon, south - dLat),
            Point.fromLngLat(east + dLon, north + dLat),
            Point.fromLngLat(west - dLon, north + dLat),
            Point.fromLngLat(west - dLon, south - dLat)
        )
        val heightKm = (north - south + 2 * dLat) * 111.32
        val widthKm = (east - west + 2 * dLon) * 111.32 * cos(Math.toRadians((south + north) / 2))
        return Polygon.fromLngLats(listOf(ring)) to max(heightKm, widthKm)
    }

    /** Downloads the area. Returns true when it finished, false on any failure. Progress is 0..1. */
    suspend fun download(trailId: String, path: List<LatLngDTO>, onProgress: (Float) -> Unit): Boolean {
        val (area, spanKm) = areaAround(path)
        val tileStore = TileStore.create()
        val styleOptions = StylePackLoadOptions.Builder()
            .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
            .acceptExpired(true)
            .build()
        val descriptor = OfflineManager().createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(STYLE)
                .minZoom(MIN_ZOOM)
                .maxZoom(maxZoomFor(spanKm))
                .stylePackOptions(styleOptions)
                .build()
        )
        val options = TileRegionLoadOptions.Builder()
            .geometry(area)
            .descriptors(listOf(descriptor))
            .metadata(Value.valueOf("pathy-trail"))
            .acceptExpired(true)
            .networkRestriction(NetworkRestriction.NONE)
            .build()

        return suspendCancellableCoroutine { cont ->
            val task = tileStore.loadTileRegion(
                regionId(trailId),
                options,
                TileRegionLoadProgressCallback { p ->
                    val total = p.requiredResourceCount
                    if (total > 0) onProgress((p.completedResourceCount.toFloat() / total).coerceIn(0f, 1f))
                },
                TileRegionCallback { result ->
                    if (cont.isActive) cont.resume(result.isValue)
                }
            )
            cont.invokeOnCancellation { task.cancel() }
        }
    }

    /** Removes a downloaded area. */
    fun remove(trailId: String) {
        TileStore.create().removeTileRegion(regionId(trailId))
    }

    /** How much room the saved map of this trail takes on the phone, or null when it is not saved or cannot be read. */
    suspend fun sizeBytes(trailId: String): Long? = suspendCancellableCoroutine { cont ->
        TileStore.create().getAllTileRegions { result ->
            val size = result.value?.firstOrNull { it.id == regionId(trailId) }?.completedResourceSize
            if (cont.isActive) cont.resume(size)
        }
    }
}
