package com.example.pathy

data class CommunityEvent(
    val id: String = "",
    val userName: String = "",
    val userProfileImageUrl: String = "",
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val trailId: String = "",
    val trailName: String = "",
    val trailRating: Float = 0f,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val likedBy: Map<String, Boolean> = emptyMap(),
    val imageUrls: List<String> = emptyList(),
    val registrationOpen: Boolean = true,
    val createdByUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val attendees: List<String> = emptyList()
)

