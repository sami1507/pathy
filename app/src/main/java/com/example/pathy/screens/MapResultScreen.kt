package com.example.pathy.screens

import com.example.pathy.getDisplayTrailName
import com.example.pathy.i18n.applyAppLanguage
import androidx.compose.ui.res.stringResource
import com.example.pathy.R
import com.example.pathy.ui.theme.PathyColors

import com.example.pathy.ui.theme.MapPalette

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pathy.TrailDetailsActivity
import com.example.pathy.data.LatLngDTO
import com.example.pathy.data.TrailData
import com.example.pathy.data.TrailKind
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapResultScreen(centerPoint: Point, trails: List<TrailData>, onBack: () -> Unit) {
    val context = LocalContext.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val mapReady = remember { mutableStateOf(false) }
    var selectedTrail by remember { mutableStateOf<TrailData?>(null) }
    var polylineAnnotationManager by remember { mutableStateOf<PolylineAnnotationManager?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetScroll = rememberScrollState()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetContainerColor = Color.Transparent,
        sheetContent = {
            selectedTrail?.let { trail ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.58f)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(PathyColors.PrimaryContainer, PathyColors.OutlineVariant)
                            ),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .verticalScroll(sheetScroll)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp, bottom = 12.dp)
                            .size(width = 48.dp, height = 4.dp)
                            .background(PathyColors.ScrimSoft, RoundedCornerShape(100))
                    )

                    Text(
                        text = "📍 ${getDisplayTrailName(context, trail)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))

                    InfoRow(stringResource(R.string.map_points_count), "${trail.geoPoints.size}")
                    InfoRow(stringResource(R.string.map_is_hiking), if (trail.isHikingTrail) stringResource(R.string.common_yes) else stringResource(R.string.common_no))

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            selectedTrail?.let { t ->
                                val intent = TrailDetailsActivity.createIntent(context, t)
                                context.startActivity(intent)
                            } ?: Toast.makeText(context, context.getString(R.string.map_no_trail_selected), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(55.dp)
                            .clip(RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = PathyColors.PrimaryDark) // ירוק כמו StartTrailButton
                    ) {
                        Text(
                            text = stringResource(R.string.map_go_to_trail),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } ?: Spacer(modifier = Modifier.height(1.dp))
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        mapViewRef.value = this
                        getMapboxMap().setCamera(
                            CameraOptions.Builder()
                                .center(centerPoint)
                                .zoom(14.5)
                                .build()
                        )
                        getMapboxMap().loadStyleUri(Style.STANDARD) {
                            it.applyAppLanguage(context)
                            mapReady.value = true

                            val annotationManager = annotations.createPointAnnotationManager()
                            polylineAnnotationManager = annotations.createPolylineAnnotationManager()

                            val pinHiking = createFancyMarkerBitmap(
                                sizePx = 96,
                                topColor = MapPalette.pin(PathyColors.TrailHiking).top,
                                bottomColor = MapPalette.pin(PathyColors.TrailHiking).bottom,
                                strokeColor = MapPalette.pin(PathyColors.TrailHiking).stroke,
                                emoji = "🥾"
                            )
                            val pinBicycle = createFancyMarkerBitmap(
                                sizePx = 96,
                                topColor = MapPalette.pin(PathyColors.TrailCycling).top,
                                bottomColor = MapPalette.pin(PathyColors.TrailCycling).bottom,
                                strokeColor = MapPalette.pin(PathyColors.TrailCycling).stroke,
                                emoji = "🚲"
                            )
                            val pinOffroad = createFancyMarkerBitmap(
                                sizePx = 96,
                                topColor = MapPalette.pin(PathyColors.TrailOffroad).top,
                                bottomColor = MapPalette.pin(PathyColors.TrailOffroad).bottom,
                                strokeColor = MapPalette.pin(PathyColors.TrailOffroad).stroke,
                                emoji = "🚙"
                            )

                            trails.forEach { trail ->
                                val icon = when (trail.kind()) {
                                    TrailKind.HIKING  -> pinHiking
                                    TrailKind.BICYCLE -> pinBicycle
                                    TrailKind.CAR     -> pinOffroad
                                }

                                val point = trail.midpoint?.let {
                                    Point.fromLngLat(it.longitude, it.latitude)
                                } ?: centerPoint

                                val annotationOptions = PointAnnotationOptions()
                                    .withPoint(point)
                                    .withIconImage(icon)

                                val annotation = annotationManager.create(annotationOptions)
                                annotationManager.addClickListener {
                                    if (it == annotation) {
                                        coroutineScope.launch {
                                            selectedTrail = trail

                                            drawSelectedTrailPolyline(
                                                polylineAnnotationManager,
                                                trail.geoPoints.map { p -> p.toPoint() }
                                            )

                                            val pts = trail.geoPoints.map { p -> p.toPoint() }
                                            if (pts.size >= 2) {
                                                val bottomInset = mapViewRef.value?.height?.times(0.55)?: 400.0
                                                val cam = mapViewRef.value?.getMapboxMap()?.cameraForCoordinates(
                                                    pts,
                                                    EdgeInsets(100.0, 80.0, bottomInset, 80.0),
                                                    null,
                                                    null
                                                )
                                                cam?.let { mapViewRef.value?.getMapboxMap()?.setCamera(it) }
                                            } else {
                                                mapViewRef.value?.getMapboxMap()?.setCamera(
                                                    CameraOptions.Builder().center(point).zoom(14.0).build()
                                                )
                                            }

                                            scaffoldState.bottomSheetState.expand()
                                        }

                                        true
                                    } else false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (!mapReady.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(10.dp).align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowCircleRight,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold)
        Text(text = value)
    }
}

private fun drawSelectedTrailPolyline(
    polylineManager: PolylineAnnotationManager?,
    points: List<Point>
) {
    polylineManager?.deleteAll()
    if (points.size < 2) return

    val options = PolylineAnnotationOptions()
        .withPoints(points)
        .withLineWidth(6.0)
        .withLineColor(MapPalette.routeLineHex)

    polylineManager?.create(options)
}

private fun createFancyMarkerBitmap(
    sizePx: Int,
    topColor: Int,
    bottomColor: Int,
    strokeColor: Int,
    emoji: String? = null
): Bitmap {
    val w = sizePx
    val h = sizePx
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)

    val center = w / 2f
    val radius = w * 0.42f

    val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(40, 0, 0, 0)
    }
    c.drawCircle(center, center + radius * 0.08f, radius * 1.05f, shadow)

    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            topColor, bottomColor,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    c.drawCircle(center, center, radius, fill)

    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        color = strokeColor
        strokeWidth = w * 0.06f
    }
    c.drawCircle(center, center, radius - stroke.strokeWidth / 2f, stroke)

    val tail = android.graphics.Path().apply {
        moveTo(center - radius * 0.22f, center + radius * 0.35f)
        lineTo(center + radius * 0.22f, center + radius * 0.35f)
        lineTo(center, center + radius * 0.95f)
        close()
    }
    val tailPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            center, center + radius * 0.35f, center, center + radius * 0.95f,
            bottomColor, bottomColor, android.graphics.Shader.TileMode.CLAMP
        )
    }
    c.drawPath(tail, tailPaint)

    emoji?.takeIf { it.isNotBlank() }?.let {
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            textSize = w * 0.45f
        }
        val fm = textPaint.fontMetrics
        val textY = center - (fm.ascent + fm.descent) / 2
        c.drawText(it, center, textY, textPaint)
    }

    return bmp
}

fun LatLngDTO.toPoint(): Point = Point.fromLngLat(this.longitude, this.latitude)
