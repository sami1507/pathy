package com.example.pathy.weather

import androidx.annotation.StringRes
import com.example.pathy.R
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 🌤️ OUTDOOR WEATHER SERVICE
 *
 * Evaluates weather conditions for outdoor activities
 * - Running, Hiking, Biking, Trail sports
 * - Smart recommendations based on conditions
 * - Beautiful UI data for display
 *
 * The weather provider key stays on the server: the data comes from the `currentWeather`
 * Cloud Function.
 */
class OutdoorWeatherService {

    companion object {
        private const val TAG = "OutdoorWeatherService"
    }

    /**
     * Get weather evaluation for location
     */
    suspend fun getWeatherEvaluation(
        latitude: Double,
        longitude: Double
    ): WeatherEvaluation {
        return withContext(Dispatchers.IO) {
            try {
                val result = FirebaseFunctions.getInstance("us-central1")
                    .getHttpsCallable("currentWeather")
                    .call(mapOf("lat" to latitude, "lng" to longitude))
                    .await()
                val json = JSONObject(Gson().toJson(result.data))
                
                // Parse weather data
                val main = json.getJSONObject("main")
                val weather = json.getJSONArray("weather").getJSONObject(0)
                val wind = json.getJSONObject("wind")
                val clouds = json.getJSONObject("clouds")
                val sys = json.getJSONObject("sys")
                
                val temp = main.getDouble("temp")
                val feelsLike = main.getDouble("feels_like")
                val humidity = main.getInt("humidity")
                val pressure = main.getInt("pressure")
                
                val windSpeed = wind.optDouble("speed", 0.0) * 3.6 // m/s to km/h
                val windDeg = wind.optInt("deg", 0)
                
                val cloudiness = clouds.optInt("all", 0)
                val weatherMain = weather.getString("main")
                val weatherDesc = weather.getString("description")
                val weatherIcon = weather.getString("icon")
                
                val sunrise = sys.optLong("sunrise", 0)
                val sunset = sys.optLong("sunset", 0)
                
                // Check for rain/snow
                val rain = json.optJSONObject("rain")?.optDouble("1h", 0.0) ?: 0.0
                val snow = json.optJSONObject("snow")?.optDouble("1h", 0.0) ?: 0.0
                
                // Evaluate conditions
                val evaluation = evaluateConditions(
                    temp = temp,
                    feelsLike = feelsLike,
                    humidity = humidity,
                    windSpeed = windSpeed,
                    weatherMain = weatherMain,
                    rain = rain,
                    snow = snow,
                    cloudiness = cloudiness
                )
                
                WeatherEvaluation(
                    temperature = temp,
                    feelsLike = feelsLike,
                    humidity = humidity,
                    windSpeed = windSpeed,
                    windDirection = getWindDirection(windDeg),
                    condition = weatherMain,
                    description = weatherDesc,
                    icon = weatherIcon,
                    cloudiness = cloudiness,
                    rain = rain,
                    snow = snow,
                    sunrise = sunrise,
                    sunset = sunset,
                    score = evaluation.score,
                    suitability = evaluation.suitability,
                    recommendation = evaluation.recommendation,
                    warnings = evaluation.warnings,
                    activityScores = evaluation.activityScores
                )
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Weather fetch failed", e)
                WeatherEvaluation.error(R.string.w_err_fetch)
            }
        }
    }
    
    /**
     * Evaluate weather conditions for outdoor activities
     */
    private fun evaluateConditions(
        temp: Double,
        feelsLike: Double,
        humidity: Int,
        windSpeed: Double,
        weatherMain: String,
        rain: Double,
        snow: Double,
        cloudiness: Int
    ): WeatherScore {
        
        val warnings = mutableListOf<Int>()
        var totalScore = 100.0
        
        // Temperature scoring (optimal: 15-25°C)
        val tempScore = when {
            temp < -5 -> {
                warnings.add(R.string.w_warn_extreme_cold)
                20.0
            }
            temp < 5 -> {
                warnings.add(R.string.w_warn_very_cold)
                50.0
            }
            temp < 10 -> 70.0
            temp in 10.0..28.0 -> 100.0 // Ideal range
            temp < 32 -> {
                warnings.add(R.string.w_warn_hot)
                75.0
            }
            temp < 36 -> {
                warnings.add(R.string.w_warn_very_hot)
                40.0
            }
            else -> {
                warnings.add(R.string.w_warn_dangerous_heat)
                10.0
            }
        }
        
        // Feels-like adjustment
        val feelsLikeDiff = kotlin.math.abs(temp - feelsLike)
        if (feelsLikeDiff > 5) {
            totalScore -= 10
            if (feelsLike < temp) {
                warnings.add(R.string.w_warn_wind_chill)
            } else {
                warnings.add(R.string.w_warn_humidity_heat)
            }
        }
        
        // Wind scoring
        val windScore = when {
            windSpeed < 15 -> 100.0
            windSpeed < 30 -> {
                warnings.add(R.string.w_warn_windy)
                75.0
            }
            windSpeed < 50 -> {
                warnings.add(R.string.w_warn_strong_wind)
                40.0
            }
            else -> {
                warnings.add(R.string.w_warn_dangerous_wind)
                10.0
            }
        }
        
        // Precipitation scoring
        val precipScore = when {
            rain > 5 || snow > 3 -> {
                warnings.add(R.string.w_warn_heavy_precip)
                20.0
            }
            rain > 2 || snow > 1 -> {
                warnings.add(R.string.w_warn_light_precip)
                60.0
            }
            weatherMain == "Thunderstorm" -> {
                warnings.add(R.string.w_warn_thunderstorm)
                0.0
            }
            else -> 100.0
        }
        
        // Humidity scoring
        val humidityScore = when {
            humidity > 85 -> {
                warnings.add(R.string.w_warn_humid)
                60.0
            }
            humidity > 70 -> 80.0
            humidity in 30..70 -> 100.0
            humidity < 30 -> {
                warnings.add(R.string.w_warn_dry)
                80.0
            }
            else -> 70.0
        }
        
        // Calculate weighted total score
        totalScore = (tempScore * 0.35 + windScore * 0.25 + precipScore * 0.25 + humidityScore * 0.15)
        
        // Determine suitability
        val suitability = when {
            totalScore >= 85 -> WeatherSuitability.EXCELLENT
            totalScore >= 70 -> WeatherSuitability.GOOD
            totalScore >= 50 -> WeatherSuitability.FAIR
            totalScore >= 30 -> WeatherSuitability.POOR
            else -> WeatherSuitability.DANGEROUS
        }
        
        // Generate recommendation
        val recommendation = when (suitability) {
            WeatherSuitability.EXCELLENT -> R.string.w_rec_excellent
            WeatherSuitability.GOOD -> R.string.w_rec_good
            WeatherSuitability.FAIR -> R.string.w_rec_fair
            WeatherSuitability.POOR -> R.string.w_rec_poor
            WeatherSuitability.DANGEROUS -> R.string.w_rec_dangerous
        }
        
        // Activity-specific scores
        val activityScores = ActivityScores(
            running = calculateRunningScore(temp, windSpeed, rain),
            hiking = calculateHikingScore(temp, windSpeed, rain, cloudiness),
            cycling = calculateCyclingScore(temp, windSpeed, rain),
            mountainBiking = calculateMTBScore(temp, rain, weatherMain)
        )
        
        return WeatherScore(
            score = totalScore.toInt(),
            suitability = suitability,
            recommendation = recommendation,
            warnings = warnings,
            activityScores = activityScores
        )
    }
    
    private fun calculateRunningScore(temp: Double, windSpeed: Double, rain: Double): Int {
        var score = 100
        if (temp < 5 || temp > 28) score -= 20
        if (temp < 0 || temp > 32) score -= 30
        if (windSpeed > 20) score -= 15
        if (rain > 1) score -= 30
        return score.coerceIn(0, 100)
    }
    
    private fun calculateHikingScore(temp: Double, windSpeed: Double, rain: Double, cloudiness: Int): Int {
        var score = 100
        if (temp < 0 || temp > 30) score -= 15
        if (windSpeed > 30) score -= 20
        if (rain > 2) score -= 25
        if (cloudiness > 80 && rain == 0.0) score += 5 // Overcast is ok for hiking
        return score.coerceIn(0, 100)
    }
    
    private fun calculateCyclingScore(temp: Double, windSpeed: Double, rain: Double): Int {
        var score = 100
        if (temp < 8 || temp > 30) score -= 20
        if (windSpeed > 25) score -= 25
        if (rain > 0.5) score -= 35
        return score.coerceIn(0, 100)
    }
    
    private fun calculateMTBScore(temp: Double, rain: Double, weatherMain: String): Int {
        var score = 100
        if (temp < 5 || temp > 32) score -= 15
        if (rain > 1) score -= 40 // Muddy trails
        if (weatherMain == "Thunderstorm") score = 0
        return score.coerceIn(0, 100)
    }
    
    private fun getWindDirection(degrees: Int): String {
        return when (degrees) {
            in 0..22, in 338..360 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SW"
            in 248..292 -> "W"
            in 293..337 -> "NW"
            else -> "N"
        }
    }
}

/**
 * Weather evaluation result
 */
data class WeatherEvaluation(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windDirection: String,
    val condition: String,
    val description: String,
    val icon: String,
    val cloudiness: Int,
    val rain: Double,
    val snow: Double,
    val sunrise: Long,
    val sunset: Long,
    val score: Int,
    val suitability: WeatherSuitability,
    @StringRes val recommendation: Int,
    val warnings: List<Int>,
    val activityScores: ActivityScores,
    val isError: Boolean = false,
    @StringRes val errorMessage: Int? = null
) {
    companion object {
        fun error(@StringRes message: Int) = WeatherEvaluation(
            temperature = 0.0,
            feelsLike = 0.0,
            humidity = 0,
            windSpeed = 0.0,
            windDirection = "",
            condition = "",
            description = "",
            icon = "",
            cloudiness = 0,
            rain = 0.0,
            snow = 0.0,
            sunrise = 0,
            sunset = 0,
            score = 0,
            suitability = WeatherSuitability.POOR,
            recommendation = R.string.w_err_get,
            warnings = emptyList(),
            activityScores = ActivityScores(0, 0, 0, 0),
            isError = true,
            errorMessage = message
        )
    }
}

/**
 * Weather suitability levels
 */
enum class WeatherSuitability {
    EXCELLENT,  // 85-100
    GOOD,       // 70-84
    FAIR,       // 50-69
    POOR,       // 30-49
    DANGEROUS   // 0-29
}

/**
 * Activity-specific scores
 */
data class ActivityScores(
    val running: Int,
    val hiking: Int,
    val cycling: Int,
    val mountainBiking: Int
)

/**
 * Internal weather score calculation
 */
private data class WeatherScore(
    val score: Int,
    val suitability: WeatherSuitability,
    @StringRes val recommendation: Int,
    val warnings: List<Int>,
    val activityScores: ActivityScores
)
