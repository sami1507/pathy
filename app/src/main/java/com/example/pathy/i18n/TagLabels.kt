package com.example.pathy.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.pathy.R

/**
 * Trail tags are stored (and matched against saved trails) as Hebrew words: the "suitable for"
 * categories and the suggested group kinds. Older saves also carry a leading emoji and stray
 * spaces ("🚶‍♂️הליכה  "). The stored values must not change, so these keep working with existing
 * data; only the label on screen is translated here. The emoji prefix is kept. Unknown values are
 * shown as they are.
 */
@StringRes
private fun tagRes(core: String): Int? = when (core) {
    "הליכה" -> R.string.tag_walk
    "אופניים" -> R.string.tag_bike
    "רכב" -> R.string.tag_car
    "רכבי שטח" -> R.string.tag_offroad
    "בודד" -> R.string.group_solo
    "זוג" -> R.string.group_couple
    "משפחה" -> R.string.group_family
    "ילדים קטנים" -> R.string.group_small_kids
    // Trail features saved in English by the trail loader.
    "Viewpoint" -> R.string.tag_viewpoint
    "Waterfall" -> R.string.feat_waterfall
    "Forest" -> R.string.tag_forest
    "Accessible" -> R.string.tag_accessible
    "Technical" -> R.string.feat_technical
    "4WD Only" -> R.string.feat_4wd_only
    else -> null
}

@Composable
fun tagLabel(value: String): String {
    val core = value.filter { it.isLetter() || it == ' ' }.trim()
    val res = tagRes(value.trim()) ?: tagRes(core) ?: return value
    val prefix = value.takeWhile { !it.isLetter() }.trim()
    val text = stringResource(res)
    return if (prefix.isEmpty()) text else "$prefix $text"
}