package com.example.pathy.ai

import com.example.pathy.i18n.AppLocale
import androidx.annotation.StringRes
import com.example.pathy.R
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 🤖 SCOUT - Trail companion powered by OpenAI.
 *
 * The app never talks to OpenAI directly and holds no API key. Requests go to the
 * `scoutChat` Cloud Function (functions/index.js), which authenticates the user,
 * enforces a daily quota, adds weather context and calls OpenAI with a server-side secret.
 */
class ScoutAI(private val context: Context) {

    companion object {
        private const val TAG = "ScoutAI"
        private const val MAX_MESSAGE_CHARS = 1000
        private const val HISTORY_TURNS_SENT = 10
    }

    /** Resolved when called, so replies and errors follow the language the user has chosen right now. */
    private fun s(@StringRes id: Int): String = AppLocale.wrap(context).getString(id)

    // Conversation kept for context; the server only receives the most recent turns.
    private val history = CopyOnWriteArrayList<ChatMessage>()

    /**
     * Sends a message to Scout and returns its reply. Never throws: failures come back as
     * [ScoutResponse] with `isError = true` and a user-facing message.
     */
    suspend fun chat(
        userMessage: String,
        userLocation: Location? = null,
        @Suppress("UNUSED_PARAMETER") currentWeather: WeatherData? = null
    ): ScoutResponse {
        val text = userMessage.trim().take(MAX_MESSAGE_CHARS)
        if (text.isEmpty()) {
            return errorResponse(s(R.string.scout_ask_something))
        }

        val payload = hashMapOf<String, Any>(
            "message" to text,
            // The server asks the model to answer in the app language (en, ar or he).
            "lang" to AppLocale.current(context),
            "history" to history.takeLast(HISTORY_TURNS_SENT).map {
                mapOf("role" to it.role, "content" to it.content)
            }
        )
        userLocation?.let {
            payload["lat"] = it.latitude
            payload["lng"] = it.longitude
        }

        return try {
            val result = FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("scoutChat")
                .call(payload)
                .await()

            val reply = (result.data as? Map<*, *>)?.get("message") as? String
                ?: throw IllegalStateException("Scout returned an unexpected response")

            val now = System.currentTimeMillis()
            history.add(ChatMessage(role = "user", content = text, timestamp = now))
            history.add(ChatMessage(role = "assistant", content = reply, timestamp = now))

            ScoutResponse(
                message = reply,
                suggestions = extractSuggestions(reply),
                timestamp = now
            )
        } catch (e: FirebaseFunctionsException) {
            Log.w(TAG, "scoutChat failed: ${e.code}")
            errorResponse(friendlyMessage(e))
        } catch (e: Exception) {
            Log.e(TAG, "scoutChat failed", e)
            errorResponse(s(R.string.scout_unavailable))
        }
    }

    /** Trail recommendations based on the user's preferences. */
    suspend fun recommendTrails(preferences: TrailPreferences, location: Location): ScoutResponse {
        val prompt = """
Based on these preferences:
- Type: ${preferences.type}
- Difficulty: ${preferences.difficulty}
- Distance: ${preferences.distanceRange}

Recommend 3 great trails near me and explain why they're good choices.
""".trimIndent()
        return chat(prompt, location)
    }

    /** Weather-aware advice. The server fetches the current weather for [location]. */
    suspend fun getWeatherAdvice(location: Location): ScoutResponse {
        return chat("Should I go hiking today? What should I prepare for the current conditions?", location)
    }

    /** Safety tips for a planned trail. */
    suspend fun getSafetyTips(
        trailDifficulty: String,
        trailType: String,
        weather: WeatherData? = null
    ): ScoutResponse {
        val weatherInfo = weather?.let { "Current weather: ${it.description}, ${it.temperature}°C" } ?: ""
        val prompt = """
I'm planning a $trailDifficulty $trailType trail.
$weatherInfo

What safety tips and gear should I know about?
""".trimIndent()
        return chat(prompt)
    }

    suspend fun askAboutConditions(question: String): ScoutResponse =
        chat("Trail conditions question: $question")

    suspend fun getNavigationHelp(question: String): ScoutResponse =
        chat("PATHY app help (map navigation, groups, walkie-talkie, trail discovery): $question")

    fun clearHistory() {
        history.clear()
    }

    fun getHistory(): List<ChatMessage> = history.toList()

    // ---------- private helpers ----------

    private fun errorResponse(message: String) = ScoutResponse(
        message = message,
        suggestions = emptyList(),
        timestamp = System.currentTimeMillis(),
        isError = true
    )

    private fun friendlyMessage(e: FirebaseFunctionsException): String = when (e.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> s(R.string.scout_sign_in)
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
            s(R.string.scout_limit_reached)
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> s(R.string.scout_unreadable)
        else -> s(R.string.scout_unavailable)
    }

    /** Follow-up chips, chosen from words in the reply. Works for English, Arabic and Hebrew replies. */
    private fun extractSuggestions(response: String): List<String> {
        fun mentions(vararg words: String) = words.any { response.contains(it, ignoreCase = true) }
        val suggestions = mutableListOf<String>()
        when {
            mentions("weather", "طقس", "الأحوال الجوية", "מזג") -> suggestions.add(s(R.string.scout_sug_weather))
            mentions("trail", "مسار", "מסלול") && mentions("recommend", "أنصح", "أوصي", "نوصي", "توصي", "ממליץ", "המלצ") ->
                suggestions.add(s(R.string.scout_sug_recommended))
            mentions("map", "navigate", "خريطة", "تنقل", "מפה", "ניווט") -> suggestions.add(s(R.string.home_map))
            mentions("group", "مجموعة", "קבוצה") -> suggestions.add(s(R.string.groups_find))
        }
        if (suggestions.isEmpty()) {
            suggestions.addAll(listOf(s(R.string.scout_sug_nearby), s(R.string.scout_sug_weather), s(R.string.scout_sug_safety)))
        }
        return suggestions.take(3)
    }
}

// ============================================
// DATA CLASSES
// ============================================

data class ScoutResponse(
    val message: String,
    val suggestions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class ChatMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long
)

data class WeatherData(
    val temperature: Double = 0.0,
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val description: String = "Unknown",
    val windSpeed: Double = 0.0,
    val icon: String = "01d"
)

data class TrailPreferences(
    val type: String = "HIKING", // HIKING, MTB, CYCLING, OFFROAD
    val difficulty: String = "MODERATE", // EASY, MODERATE, HARD, EXTREME
    val distanceRange: String = "5-15 km",
    val elevationGain: String = "any"
)

/** Quick responses for common questions */
object ScoutQuickReplies {
    val QUICK_QUESTIONS = listOf(
        R.string.scout_sug_nearby,
        R.string.scout_q_weather,
        R.string.scout_q_easy_hikes,
        R.string.scout_q_mtb,
        R.string.scout_q_safety,
        R.string.scout_q_navigation,
        R.string.scout_q_group,
        R.string.scout_q_bring
    )

    val TRAIL_TYPE_QUESTIONS = listOf(
        R.string.scout_q_hiking_trails,
        R.string.scout_q_mtb_routes,
        R.string.scout_q_cycling,
        R.string.scout_q_offroad
    )

    val DIFFICULTY_QUESTIONS = listOf(
        R.string.scout_q_easy,
        R.string.scout_q_moderate,
        R.string.scout_q_hard,
        R.string.scout_q_expert
    )
}
