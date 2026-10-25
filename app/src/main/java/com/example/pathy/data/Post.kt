package com.example.pathy.data

data class Post(
    val postId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImageUrl: String = "",
    val trailId: String = "",
    val trailName: String = "",
    val postText: String = "",
    val imageUrls: List<String> = emptyList(),
    val trailRating: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val likedBy: Map<String, Boolean> = emptyMap()
)
data class Comment(
    val commentId: String = "",
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
data class DisplayComment(
    val comment: Comment,
    val userName: String,
    val userProfileImageUrl: String
)

