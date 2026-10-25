package com.example.pathy.profile

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * 👤 USER PROFILE
 * 
 * Complete user data model
 * - Personal info
 * - Stats & achievements
 * - Preferences
 * - Social connections
 */

data class UserProfile(
    @DocumentId
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val location: String = "",
    
    // Stats
    val stats: UserStats = UserStats(),
    
    // Preferences
    val preferences: UserPreferences = UserPreferences(),
    
    // Social
    val followers: Int = 0,
    val following: Int = 0,
    val friends: List<String> = emptyList(),
    
    // Achievements
    val achievements: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val level: Int = 1,
    val experiencePoints: Int = 0,
    
    @ServerTimestamp
    val createdAt: Date? = null,
    
    @ServerTimestamp
    val lastActive: Date? = null
)

/**
 * User statistics
 */
data class UserStats(
    val totalDistance: Float = 0f, // meters
    val totalDuration: Long = 0, // milliseconds
    val totalElevationGain: Float = 0f, // meters
    val totalActivities: Int = 0,
    val totalTrails: Int = 0,
    val totalCalories: Int = 0,
    
    // Activity breakdown
    val hikingDistance: Float = 0f,
    val runningDistance: Float = 0f,
    val cyclingDistance: Float = 0f,
    val mtbDistance: Float = 0f,
    
    // Streaks
    val currentStreak: Int = 0, // days
    val longestStreak: Int = 0, // days
    val lastActivityDate: Date? = null,
    
    // Records
    val longestDistance: Float = 0f,
    val longestDuration: Long = 0,
    val highestElevation: Float = 0f,
    val fastestPace: Float = 0f, // min/km
    
    // This year
    val yearDistance: Float = 0f,
    val yearActivities: Int = 0,
    
    // This month
    val monthDistance: Float = 0f,
    val monthActivities: Int = 0
)

/**
 * User preferences
 */
data class UserPreferences(
    // Units
    val useMetricUnits: Boolean = true,
    val useKilometers: Boolean = true,
    val useCelsius: Boolean = true,
    
    // Activity tracking
    val autoPause: Boolean = true,
    val autoLap: Boolean = false,
    val lapDistance: Float = 1000f, // meters
    val recordInterval: Int = 5, // seconds
    
    // Privacy
    val profilePublic: Boolean = true,
    val activitiesPublic: Boolean = true,
    val locationSharing: Boolean = false,
    
    // Notifications
    val pushNotifications: Boolean = true,
    val emailNotifications: Boolean = false,
    val weeklyReport: Boolean = true,
    val achievementNotifications: Boolean = true,
    val friendNotifications: Boolean = true,
    val trailAlerts: Boolean = true,
    val weatherAlerts: Boolean = true,
    
    // Map preferences
    val mapType: String = "terrain", // terrain, satellite, street
    val showTrailMarkers: Boolean = true,
    val showElevationProfile: Boolean = true,
    val offlineMapsEnabled: Boolean = false,
    
    // Voice & Audio
    val voiceGuidance: Boolean = true,
    val audioFeedback: Boolean = true,
    val audioInterval: Int = 1000, // meters
    
    // Display
    val darkMode: String = "auto", // auto, light, dark
    val keepScreenOn: Boolean = true,
    val showBatteryWarning: Boolean = true
)

/**
 * Achievement types
 */
enum class AchievementType {
    // Distance
    FIRST_KILOMETER,
    FIRST_10K,
    FIRST_MARATHON,
    FIRST_100K,
    DISTANCE_WARRIOR,
    
    // Elevation
    FIRST_100M,
    FIRST_1000M,
    MOUNTAIN_CLIMBER,
    PEAK_MASTER,
    
    // Streaks
    STREAK_7,
    STREAK_30,
    STREAK_100,
    STREAK_365,
    
    // Activities
    FIRST_TRAIL,
    TRAIL_10,
    TRAIL_50,
    TRAIL_100,
    CENTURY_CLUB,
    
    // Social
    FIRST_FRIEND,
    SOCIAL_BUTTERFLY,
    GROUP_LEADER,
    COMMUNITY_HERO,
    
    // Special
    EARLY_BIRD,
    NIGHT_OWL,
    ALL_WEATHER,
    EXPLORER,
    SPEED_DEMON
}

/**
 * Badge types
 */
enum class BadgeType {
    HIKER,
    RUNNER,
    CYCLIST,
    MTB_RIDER,
    OFFROADER,
    ALL_ROUNDER,
    ELITE_ATHLETE,
    LEGEND
}

/**
 * User level calculation
 */
object UserLevelCalculator {
    
    fun calculateLevel(experiencePoints: Int): Int {
        return when {
            experiencePoints < 100 -> 1
            experiencePoints < 300 -> 2
            experiencePoints < 600 -> 3
            experiencePoints < 1000 -> 4
            experiencePoints < 1500 -> 5
            experiencePoints < 2100 -> 6
            experiencePoints < 2800 -> 7
            experiencePoints < 3600 -> 8
            experiencePoints < 4500 -> 9
            experiencePoints < 5500 -> 10
            experiencePoints < 7000 -> 11
            experiencePoints < 9000 -> 12
            experiencePoints < 12000 -> 15
            experiencePoints < 16000 -> 18
            experiencePoints < 20000 -> 20
            else -> 20 + (experiencePoints - 20000) / 2000
        }
    }
    
    fun experienceForNextLevel(currentLevel: Int): Int {
        return when (currentLevel) {
            1 -> 100
            2 -> 300
            3 -> 600
            4 -> 1000
            5 -> 1500
            6 -> 2100
            7 -> 2800
            8 -> 3600
            9 -> 4500
            10 -> 5500
            11 -> 7000
            12 -> 9000
            13 -> 12000
            14 -> 16000
            15 -> 20000
            else -> 20000 + (currentLevel - 15) * 2000
        }
    }
    
    fun earnExperience(activityType: String, distanceMeters: Float, durationMs: Long): Int {
        var xp = 0
        
        // Base XP from distance (10 XP per km)
        xp += (distanceMeters / 1000 * 10).toInt()
        
        // Bonus for longer activities
        val hours = durationMs / (1000 * 60 * 60)
        if (hours > 0) {
            xp += (hours * 20).toInt()
        }
        
        // Activity type multiplier
        xp = when (activityType.lowercase()) {
            "running" -> (xp * 1.2).toInt()
            "cycling" -> (xp * 0.8).toInt()
            "mountain_biking" -> (xp * 1.5).toInt()
            else -> xp
        }
        
        return xp
    }
}
