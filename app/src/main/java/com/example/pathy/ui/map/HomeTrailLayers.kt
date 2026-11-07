package com.example.pathy.ui.map

import com.example.pathy.data.TrailData
import com.example.pathy.ui.design.downsample
import com.example.pathy.ui.theme.TrailLayers
import com.google.gson.JsonObject
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.QueriedRenderedFeature
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenBox
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs

/**
 * The natural trails around the person, drawn on the home map. One source holds every trail, coloured by
 * difficulty, so a hundred trails cost the map one layer. It lives in the map style, which is why saved
 * trails still appear with no connection.
 */
object HomeTrailLayers {
    private const val SOURCE = "nearby-trails-source"
    private const val CASING = "nearby-trails-casing"
    const val LINE = "nearby-trails-line"
    private const val SELECTED = "selected-trail"
    private const val MAX_POINTS_PER_TRAIL = 300
    private const val TAP_SLOP_PX = 28.0

    /** One line per trail, carrying its id and colour, so a tap can say which trail it hit. */
    fun collection(trails: List<TrailData>): FeatureCollection =
        FeatureCollection.fromFeatures(
            trails.filter { it.geoPoints.size >= 2 }.map { trail ->
                val line = LineString.fromLngLats(
                    downsample(trail.geoPoints, MAX_POINTS_PER_TRAIL).map { Point.fromLngLat(it.longitude, it.latitude) }
                )
                Feature.fromGeometry(line, JsonObject().apply {
                    addProperty("id", trail.id)
                    addProperty("color", TrailLayers.difficultyHex(trail.difficulty))
                })
            }
        )

    /** Adds the source and layers the first time, then only replaces the data. Safe to call after every style load. */
    fun update(style: Style, trails: List<TrailData>) {
        val data = collection(trails)
        if (style.styleSourceExists(SOURCE)) {
            style.getSourceAs<GeoJsonSource>(SOURCE)?.featureCollection(data)
            return
        }
        style.addSource(geoJsonSource(SOURCE) { featureCollection(data) })
        style.addLayer(lineLayer(CASING, SOURCE) {
            lineColor("#FFFFFF")
            lineWidth(6.5)
            lineOpacity(0.85)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        })
        style.addLayer(lineLayer(LINE, SOURCE) {
            lineColor(Expression.get("color"))
            lineWidth(3.8)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        })
    }

    /** Emphasises one trail with its start and end markers, or clears the emphasis when [trail] is null. */
    fun select(style: Style, trail: TrailData?) {
        TrailLayers.remove(style, SELECTED)
        if (trail == null || trail.geoPoints.size < 2) return
        TrailLayers.add(
            style,
            downsample(trail.geoPoints, MAX_POINTS_PER_TRAIL).map { Point.fromLngLat(it.longitude, it.latitude) },
            trail.difficulty,
            prefix = SELECTED
        )
    }

    /** The id of the trail under a tap, looking a thumb's width around the finger, or null. */
    fun idAt(map: MapboxMap, tap: Point, onResult: (String?) -> Unit) {
        val pixel = map.pixelForCoordinate(tap)
        val box = ScreenBox(
            ScreenCoordinate(pixel.x - TAP_SLOP_PX, pixel.y - TAP_SLOP_PX),
            ScreenCoordinate(pixel.x + TAP_SLOP_PX, pixel.y + TAP_SLOP_PX)
        )
        map.queryRenderedFeatures(RenderedQueryGeometry(box), RenderedQueryOptions(listOf(LINE), null)) { result ->
            val hit: QueriedRenderedFeature? = result.value?.firstOrNull()
            onResult(hit?.queriedFeature?.feature?.getStringProperty("id"))
        }
    }
}
