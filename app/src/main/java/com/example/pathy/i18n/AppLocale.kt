package com.example.pathy.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The one place that decides which language PATHY shows.
 *
 * - The user's choice is either [SYSTEM] or one of [SUPPORTED]. It is stored in SharedPreferences,
 *   not DataStore, because it must be read synchronously in `attachBaseContext`, before any
 *   coroutine can run.
 * - [SYSTEM] resolves to the first device language PATHY has translations for, and falls back to
 *   English. It never silently becomes Hebrew.
 * - [wrap] puts the resolved locale on a Context. Every activity and service calls it from
 *   `attachBaseContext` (see PathyActivity), so plain `ComponentActivity` works on all API levels.
 * - From Android 13 the choice is mirrored to the system per-app language setting, and a change made
 *   there is picked up again by [syncFromSystem].
 *
 * Arabic uses Western digits (0-9): that is what Arabic readers in the Levant expect on maps,
 * distances and times, and it keeps numbers aligned with the Latin units next to them.
 */
object AppLocale {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val ARABIC = "ar"
    const val HEBREW = "he"

    val SUPPORTED = listOf(ENGLISH, ARABIC, HEBREW)

    private const val PREFS = "pathy_locale"
    private const val KEY_CHOICE = "choice"

    /** The stored choice: [SYSTEM] or one of [SUPPORTED]. */
    fun choice(context: Context): String {
        val saved = prefs(context).getString(KEY_CHOICE, null)
        return normalize(saved) ?: SYSTEM
    }

    // applicationContext is still null while Application.attachBaseContext runs, so fall back to
    // the context itself; SharedPreferences works on it either way.
    private fun prefs(context: Context) =
        (context.applicationContext ?: context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Stores [choice] and, on Android 13+, mirrors it to the system per-app language setting. */
    fun setChoice(context: Context, choice: String) {
        val value = normalize(choice) ?: SYSTEM
        prefs(context).edit().putString(KEY_CHOICE, value).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales =
                if (value == SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(value)
        }
    }

    /**
     * Android 13+: the user can also change the app language in system settings. If that left a
     * supported language there, adopt it as the stored choice.
     */
    fun syncFromSystem(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val fromSystem = manager.applicationLocales
        if (fromSystem.isEmpty) return
        val code = normalize(fromSystem[0].language) ?: return
        if (code != choice(context)) {
            prefs(context).edit().putString(KEY_CHOICE, code).apply()
        }
    }

    /** The language code ([ENGLISH], [ARABIC] or [HEBREW]) that [choice] resolves to right now. */
    fun resolve(choice: String, deviceLocales: List<Locale>): String =
        normalize(choice)?.takeIf { it != SYSTEM }
            ?: deviceLocales.firstNotNullOfOrNull { normalize(it.language)?.takeIf { code -> code != SYSTEM } }
            ?: ENGLISH

    /** The locale used for formatting and resources for a resolved language code. */
    fun localeFor(code: String): Locale = when (code) {
        ARABIC -> Locale.forLanguageTag("ar-u-nu-latn")
        // The legacy "iw" code is what Android's resource table and Locale.getDefault() use for
        // Hebrew, so it matches values-iw on every API level.
        HEBREW -> Locale("iw")
        else -> Locale.ENGLISH
    }

    /** True for the languages written right to left. */
    fun isRtl(code: String): Boolean = code == ARABIC || code == HEBREW

    /** Returns [base] with the user's language applied. Safe to call more than once. */
    fun wrap(base: Context): Context {
        val device = deviceLocales(base.resources.configuration)
        val code = resolve(choice(base), device)
        val locale = localeFor(code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * The locale speech recognition and text-to-speech should use for the language currently shown.
     * The region comes from the device when it has one for this language (ar-IL, he-IL, ...), so the
     * recognizer picks the closest dialect; otherwise a sensible default is used.
     */
    fun speechLocale(context: Context): Locale {
        val code = current(context)
        val fromDevice = deviceLocales(android.content.res.Resources.getSystem().configuration)
            .firstOrNull { normalize(it.language) == code && it.country.isNotEmpty() }
        return fromDevice ?: when (code) {
            ARABIC -> Locale("ar", "SA")
            HEBREW -> Locale("iw", "IL")
            else -> Locale.US
        }
    }

    /** The language code currently shown, e.g. for the Settings screen and server requests. */
    fun current(context: Context): String =
        resolve(choice(context), deviceLocales(context.resources.configuration))

    private fun deviceLocales(config: Configuration): List<Locale> {
        val list = config.locales
        return (0 until list.size()).map { list[it] }
    }

    /** Maps "iw" to "he", strips regions, and returns null for languages PATHY does not ship. */
    private fun normalize(value: String?): String? {
        val language = value?.substringBefore('-')?.substringBefore('_')?.lowercase() ?: return null
        return when (language) {
            SYSTEM -> SYSTEM
            "en" -> ENGLISH
            "ar" -> ARABIC
            "he", "iw" -> HEBREW
            else -> null
        }
    }
}
