package com.example.pathy.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.pathy.R

/**
 * Activity types are stored as language-independent codes ("HIKING", "MOUNTAIN_BIKING", ...) and
 * the history filters use short keys ("All", "MTB"). These helpers turn them into display text.
 */
@StringRes
private fun activityTypeRes(type: String?): Int? = when (type?.uppercase()) {
    "HIKING" -> R.string.activity_hiking
    "RUNNING" -> R.string.activity_running
    "CYCLING" -> R.string.activity_cycling
    "MOUNTAIN_BIKING", "MTB" -> R.string.activity_mtb
    else -> null
}

/** Display name of a stored activity type; unknown types are shown as they are stored. */
@Composable
fun activityTypeLabel(type: String?): String =
    activityTypeRes(type)?.let { stringResource(it) } ?: type.orEmpty()

/** Display name of a history filter key: "All" or an activity type. */
@Composable
fun activityFilterLabel(filter: String): String =
    if (filter == "All") stringResource(R.string.filter_all) else activityTypeLabel(filter)
