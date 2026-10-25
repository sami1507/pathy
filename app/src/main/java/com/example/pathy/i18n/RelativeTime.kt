package com.example.pathy.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.pathy.R

/**
 * "3 minutes ago" in the app language, with the right plural form for Arabic (zero, one, two, few,
 * many, other) and Hebrew (one, two, many, other). The wording lives in plurals.xml.
 */
object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun format(context: Context, timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val minutes = (diff / MINUTE).toInt()
        val hours = (diff / HOUR).toInt()
        val days = (diff / DAY).toInt()
        val resources = context.resources
        return when {
            minutes < 1 -> resources.getString(R.string.time_ago_just_now)
            minutes < 60 -> resources.getQuantityString(R.plurals.time_ago_minutes, minutes, minutes)
            hours < 24 -> resources.getQuantityString(R.plurals.time_ago_hours, hours, hours)
            days < 30 -> resources.getQuantityString(R.plurals.time_ago_days, days, days)
            else -> {
                val months = (days / 30).coerceAtLeast(1)
                resources.getQuantityString(R.plurals.time_ago_months, months, months)
            }
        }
    }
}

@Composable
fun relativeTimeAgo(timestamp: Long): String = RelativeTime.format(LocalContext.current, timestamp)
