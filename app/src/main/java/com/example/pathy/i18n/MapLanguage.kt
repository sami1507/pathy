package com.example.pathy.i18n

import android.content.Context
import com.mapbox.bindgen.Value
import com.mapbox.maps.Style

/**
 * Asks the Mapbox Standard style to write place names in the app language, so an Arabic user sees
 * Arabic street and town names. Other styles ignore the setting, and where a name is not available
 * in that language the map falls back to the local name. A failure here must never break the map.
 */
fun Style.applyAppLanguage(context: Context) {
    val tag = when (AppLocale.current(context)) {
        AppLocale.ARABIC -> "ar"
        AppLocale.HEBREW -> "he"
        else -> "en"
    }
    try {
        setStyleImportConfigProperty("basemap", "language", Value(tag))
    } catch (_: Throwable) {
        // Older style or SDK without the "language" option: keep the default labels.
    }
}
