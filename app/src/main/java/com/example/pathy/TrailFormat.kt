package com.example.pathy

import com.example.pathy.i18n.isolateBidi
import kotlin.math.*
import android.content.Context
import com.example.pathy.data.DifficultyLevel
import com.example.pathy.data.TrailData
import java.util.*

fun formatDistance(context: Context, distanceMeters: Double): String {
    return when {
        distanceMeters >= 10_000 ->
            context.getString(R.string.unit_km, (distanceMeters / 1000).toInt().toString())
        distanceMeters >= 1_000 ->
            context.getString(R.string.unit_km, String.format(Locale.getDefault(), "%.1f", distanceMeters / 1000))
        else ->
            context.getString(R.string.unit_m, ((distanceMeters / 10).roundToInt() * 10).toString())
    }
}

fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
        minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
        else -> String.format("00:%02d", seconds)
    }
}


fun formatDifficulty(context: Context, level: DifficultyLevel): String = when (level) {
    DifficultyLevel.EASY -> context.getString(R.string.difficulty_easy)
    DifficultyLevel.MEDIUM -> context.getString(R.string.difficulty_moderate)
    DifficultyLevel.HARD -> context.getString(R.string.difficulty_hard)
    DifficultyLevel.EXTREME -> context.getString(R.string.difficulty_extreme)
}


/** Names that mean "no name was given", including the Hebrew default that older saves stored. */
private val UNNAMED_TRAIL_NAMES = setOf("מסלול ללא שם", "Unnamed trail", "مسار بلا اسم")

fun getDisplayTrailName(context: Context, trail: TrailData): String {
    if (trail.name.isNotBlank() && trail.name !in UNNAMED_TRAIL_NAMES) return trail.name
    // A waymarked route without a name is still told apart by the colour of its paint marks.
    com.example.pathy.i18n.TrailInfoLabels.markedName(context, trail.info)?.let { return it }
    return context.getString(R.string.trail_in_place, getMiddlePointLocationName(context, trail.geoPoints).isolateBidi())
}
