package com.example.pathy

import android.content.Context
import android.graphics.BitmapFactory
import com.example.pathy.data.Report
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.geojson.Point

fun drawReportMarker(
    context: Context,
    mapView: MapView,
    report: Report
) {
    val annotationPlugin = mapView.annotations
    val pointAnnotationManager = annotationPlugin.createPointAnnotationManager()

    val iconId = when (report.category) {
        "מכשול בדרך" -> R.drawable.block
        "צוק מסוכן" -> R.drawable.landslide
        "נקודת תדלוק" -> R.drawable.local_gas_station
        else -> R.drawable.report
    }

    val bitmap = BitmapFactory.decodeResource(context.resources, iconId)

    val annotationOptions = PointAnnotationOptions()
        .withPoint(Point.fromLngLat(report.longitude, report.latitude))
        .withIconImage(bitmap)
        .withIconSize(0.05)
    pointAnnotationManager.create(annotationOptions)
}
