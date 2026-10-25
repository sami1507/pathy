package com.example.pathy.data

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pathy.R
import com.example.pathy.ui.theme.PathyColors

/**
 * A report category the user can pick on the map.
 *
 * [name] is the value saved with each report (and used to choose the marker icon). It is Hebrew
 * because reports were first saved that way, and it must not change or old reports would lose their
 * icon. [labelRes] is what the user reads, in the app language.
 */
data class ReportOptions(
    val name: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val backgroundColor: Color
)

object HazardOptions {
    val all = listOf(
        ReportOptions("מכשול בדרך", R.string.report_obstacle, Icons.Filled.Block, PathyColors.DifficultyExtreme),
        ReportOptions("צוק מסוכן", R.string.report_cliff, Icons.Filled.Warning, PathyColors.DifficultyExtreme),
        ReportOptions("שטח בוצי", R.string.report_mud, Icons.Filled.Grain, PathyColors.Bark),
        ReportOptions("דרך חסומה", R.string.report_blocked_road, Icons.Filled.DoNotDisturb, PathyColors.TextSecondary),
        ReportOptions("סכנת התהפכות", R.string.report_rollover, Icons.Filled.ReportProblem, PathyColors.Warning),
        ReportOptions("שיטפון", R.string.report_flood, Icons.Filled.Waves, PathyColors.Secondary)
    )
}

object PointOfInterestOptions {
    val all = listOf(
        ReportOptions("מעיין", R.string.poi_spring, Icons.Filled.Water, PathyColors.Secondary),
        ReportOptions("תצפית", R.string.poi_viewpoint, Icons.Filled.Visibility, PathyColors.SecondaryDark),
        ReportOptions("מקום למחנאות", R.string.poi_camping, Icons.Filled.HolidayVillage, PathyColors.Title),
        ReportOptions("אזור צל", R.string.poi_shade, Icons.Filled.FilterHdr, PathyColors.Bark),
        ReportOptions("חניון מסודר", R.string.poi_parking, Icons.Filled.LocalParking, PathyColors.SecondaryDark),
        ReportOptions("נקודת תדלוק", R.string.poi_fuel, Icons.Filled.LocalGasStation, PathyColors.TextSecondary)
    )
}

/** The text to show for a stored report category, in the app language. Unknown values are shown as saved. */
fun reportCategoryLabel(context: android.content.Context, category: String): String {
    val option = (HazardOptions.all + PointOfInterestOptions.all).firstOrNull { it.name == category }
    return option?.let { context.getString(it.labelRes) } ?: category
}
