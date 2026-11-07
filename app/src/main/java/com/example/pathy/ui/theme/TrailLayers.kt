package com.example.pathy.ui.theme

import com.example.pathy.data.DifficultyLevel
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource

/**
 * How a trail is drawn on the map. Layers live in the map style, so a saved trail looks the same with
 * no connection as with one. The line takes the colour of the trail's difficulty, sits on a white casing
 * so it reads on forest, desert and satellite alike, and has a green start and a dark end marker.
 */
object TrailLayers {

    private const val CASING = "#FFFFFF"

    fun difficultyHex(level: DifficultyLevel): String = when (level) {
        DifficultyLevel.EASY -> MapPalette.easy
        DifficultyLevel.MEDIUM -> MapPalette.moderate
        DifficultyLevel.HARD -> MapPalette.hard
        DifficultyLevel.EXTREME -> MapPalette.extreme
    }

    /** Adds the trail to [style]. Safe to call once per style load; [prefix] keeps several trails apart. */
    fun add(style: Style, points: List<Point>, difficulty: DifficultyLevel, prefix: String = "trail") {
        if (points.size < 2) return
        style.addSource(geoJsonSource("$prefix-line-source") { geometry(LineString.fromLngLats(points)) })
        style.addLayer(lineLayer("$prefix-casing", "$prefix-line-source") {
            lineColor(CASING)
            lineWidth(9.0)
            lineOpacity(0.95)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        })
        style.addLayer(lineLayer("$prefix-line", "$prefix-line-source") {
            lineColor(difficultyHex(difficulty))
            lineWidth(5.5)
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
        })

        marker(style, "$prefix-end", points.last(), MapPalette.endMarkerHex)
        marker(style, "$prefix-start", points.first(), MapPalette.startMarkerHex)
    }

    /** Takes away what [add] put on the map under [prefix]. Missing parts are ignored. */
    fun remove(style: Style, prefix: String) {
        listOf("$prefix-start-dot", "$prefix-start-ring", "$prefix-end-dot", "$prefix-end-ring", "$prefix-line", "$prefix-casing")
            .forEach { runCatching { style.removeStyleLayer(it) } }
        listOf("$prefix-start-source", "$prefix-end-source", "$prefix-line-source")
            .forEach { runCatching { style.removeStyleSource(it) } }
    }

    private fun marker(style: Style, id: String, at: Point, colorHex: String) {
        style.addSource(geoJsonSource("$id-source") { geometry(at) })
        style.addLayer(circleLayer("$id-ring", "$id-source") {
            circleRadius(9.0)
            circleColor(CASING)
        })
        style.addLayer(circleLayer("$id-dot", "$id-source") {
            circleRadius(6.0)
            circleColor(colorHex)
        })
    }
}
