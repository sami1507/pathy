package com.example.pathy.i18n

import android.content.Context
import androidx.annotation.StringRes
import com.example.pathy.R
import com.example.pathy.data.TrailInfo

/** The words for what the map data says about a trail, in the app language. */
object TrailInfoLabels {

    @StringRes
    private fun markedRes(colour: String): Int? = when (colour) {
        "red" -> R.string.trail_marked_red
        "blue" -> R.string.trail_marked_blue
        "green" -> R.string.trail_marked_green
        "black" -> R.string.trail_marked_black
        "orange" -> R.string.trail_marked_orange
        "yellow" -> R.string.trail_marked_yellow
        "white" -> R.string.trail_marked_white
        "purple" -> R.string.trail_marked_purple
        "brown" -> R.string.trail_marked_brown
        "pink" -> R.string.trail_marked_pink
        "grey", "gray" -> R.string.trail_marked_grey
        else -> null
    }

    /** "Red-marked trail": the name of a waymarked route that has no name of its own, or null. */
    fun markedName(context: Context, info: TrailInfo?): String? =
        info?.waymark?.let(::markedRes)?.let { context.getString(it) }

    /** A name for a list: the trail's own name, else its paint mark, else "Unnamed trail". */
    fun listName(context: Context, name: String, info: TrailInfo?): String =
        name.takeIf { it.isNotBlank() } ?: markedName(context, info) ?: context.getString(R.string.trail_unnamed)

    fun network(context: Context, code: String?): String? = when (code) {
        "iwn" -> context.getString(R.string.trail_net_international)
        "nwn" -> context.getString(R.string.trail_net_national)
        "rwn" -> context.getString(R.string.trail_net_regional)
        "lwn" -> context.getString(R.string.trail_net_local)
        else -> null
    }

    fun warning(context: Context, code: String): String? = when (code) {
        "SEASONAL" -> context.getString(R.string.trail_chip_seasonal)
        "HARD_TO_FOLLOW" -> context.getString(R.string.trail_chip_hard)
        "NEEDS_CHECK" -> context.getString(R.string.trail_chip_check)
        "OUTDATED" -> context.getString(R.string.trail_chip_outdated)
        else -> null
    }

    /** "Updated 3 months ago" or "Checked on site 2 weeks ago"; null when nothing is known. */
    fun updated(context: Context, info: TrailInfo?, nowMs: Long = System.currentTimeMillis()): String? {
        val at = info?.updatedMs ?: return null
        val ago = RelativeTime.format(context, at, nowMs)
        return context.getString(if (info.verified) R.string.trail_verified else R.string.trail_updated, ago)
    }
}
