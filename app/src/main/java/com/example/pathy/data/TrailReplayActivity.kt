package com.example.pathy.data

import com.example.pathy.i18n.PathyActivity
import com.example.pathy.ui.theme.MapPalette

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.example.pathy.R
import com.example.pathy.interpolatePoints
import com.google.gson.Gson
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures

class TrailReplayActivity : PathyActivity() {

    private lateinit var mapView: MapView
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private var trailPoints: List<Point> = emptyList()
    private var index = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var moveRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trail_replay)

        mapView = findViewById(R.id.replayMapView)

        intent.getStringExtra("trailJson")?.let {
            val trail = Gson().fromJson(it, SavedTrail::class.java)
            trailPoints = trail.geoPoints.map { coord -> Point.fromLngLat(coord.longitude, coord.latitude) }
        }

        mapView.getMapboxMap().loadStyleUri(Style.OUTDOORS) {
            setupReplay()
        }
    }

    private fun drawTrailLine(points: List<Point>) {
        val lineString = LineString.fromLngLats(points)
        mapView.getMapboxMap().getStyle { style ->
            if (style.getSourceAs<GeoJsonSource>("route-source") == null) {
                style.addSource(geoJsonSource("route-source") {
                    geometry(lineString)
                })
                style.addLayer(lineLayer("route-border-layer", "route-source") {
                    lineColor(MapPalette.routeCasingHex)
                    lineWidth(12.0)
                })
                style.addLayer(lineLayer("route-layer", "route-source") {
                    lineColor(MapPalette.routeLineHex)
                    lineWidth(6.0)
                })
            } else {
                style.getSourceAs<GeoJsonSource>("route-source")?.geometry(lineString)
            }
        }
    }

    private fun setupReplay() {
        drawTrailLine(trailPoints)

        if (trailPoints.size < 2) return

        val annotationApi = mapView.annotations
        pointAnnotationManager = annotationApi.createPointAnnotationManager()

        mapView.gestures.updateSettings {
            scrollEnabled = false
            rotateEnabled = false
            pitchEnabled = false
        }

        val rawIcon = BitmapFactory.decodeResource(resources, R.drawable.hiking_icon)
        val resizedIcon = Bitmap.createScaledBitmap(rawIcon, 72, 72, false)

        var lastBearing = 0.0
        val tilt = 65.0
        val zoom = 23.5

        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(trailPoints[0])
                .pitch(tilt)
                .zoom(zoom)
                .bearing(lastBearing)
                .build()
        )

        moveRunnable = object : Runnable {
            override fun run() {
                if (index >= trailPoints.size - 1) {
                    setResult(Activity.RESULT_OK)
                    finish()
                    return
                }

                val start = trailPoints[index]
                val end = trailPoints[index + 1]
                val interpolated = interpolatePoints(start, end, steps = 8)
                var stepIndex = 0

                var stepSinceLastCameraUpdate = 0

                fun animateStep() {
                    if (stepIndex >= interpolated.size) {
                        index++
                        handler.postDelayed(this, 0)
                        return
                    }

                    val currentPoint = interpolated[stepIndex++]

                    val cameraOptions = CameraOptions.Builder()
                        .center(currentPoint)
                        .pitch(45.0)         // תצוגה רגועה בזווית תלת מימד
                        .zoom(21.5)          // תקריב נוח
                        .bearing(0.0)        // לא מסתובבת לפי כיוון
                        .build()

                    mapView.getMapboxMap().easeTo(
                        cameraOptions,
                        MapAnimationOptions.mapAnimationOptions {
                            duration(100)
                        }
                    )

                    pointAnnotationManager.deleteAll()
                    val annotation = PointAnnotationOptions()
                        .withPoint(currentPoint)
                        .withIconImage(resizedIcon)
                    pointAnnotationManager.create(annotation)

                    handler.postDelayed({ animateStep() }, 60)
                }

                animateStep()
            }
        }

        handler.post(moveRunnable)
    }

    private fun offsetPoint(point: Point, bearing: Double, distanceMeters: Double): Point {
        val R = 6378137.0 // רדיוס כדור הארץ במטרים
        val bearingRad = Math.toRadians(bearing)
        val lat1 = Math.toRadians(point.latitude())
        val lon1 = Math.toRadians(point.longitude())

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(distanceMeters / R) +
                    Math.cos(lat1) * Math.sin(distanceMeters / R) * Math.cos(bearingRad)
        )
        val lon2 = lon1 + Math.atan2(
            Math.sin(bearingRad) * Math.sin(distanceMeters / R) * Math.cos(lat1),
            Math.cos(distanceMeters / R) - Math.sin(lat1) * Math.sin(lat2)
        )

        return Point.fromLngLat(Math.toDegrees(lon2), Math.toDegrees(lat2))
    }


    override fun onDestroy() {
        handler.removeCallbacks(moveRunnable)
        super.onDestroy()
    }
}