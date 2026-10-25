package com.example.pathy.bookmarks

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 🔖 BOOKMARK MANAGER
 * 
 * Save and manage favorite trails
 * - Quick bookmark/unbookmark
 * - Custom lists
 * - Sync across devices
 * - Offline support
 */
class BookmarkManager {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val _bookmarkedTrails = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedTrails: StateFlow<Set<String>> = _bookmarkedTrails
    
    /**
     * Bookmark a trail
     */
    suspend fun bookmarkTrail(
        userId: String,
        trailId: String,
        trailName: String,
        trailData: Map<String, Any> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bookmark = hashMapOf(
                "trailId" to trailId,
                "trailName" to trailName,
                "bookmarkedAt" to System.currentTimeMillis(),
                "trailData" to trailData
            )
            
            firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .document(trailId)
                .set(bookmark)
                .await()
            
            // Update local state
            _bookmarkedTrails.value = _bookmarkedTrails.value + trailId
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove bookmark
     */
    suspend fun removeBookmark(
        userId: String,
        trailId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .document(trailId)
                .delete()
                .await()
            
            // Update local state
            _bookmarkedTrails.value = _bookmarkedTrails.value - trailId
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if trail is bookmarked
     */
    fun isBookmarked(trailId: String): Boolean {
        return _bookmarkedTrails.value.contains(trailId)
    }
    
    /**
     * Toggle bookmark
     */
    suspend fun toggleBookmark(
        userId: String,
        trailId: String,
        trailName: String,
        trailData: Map<String, Any> = emptyMap()
    ): Result<Boolean> {
        return if (isBookmarked(trailId)) {
            removeBookmark(userId, trailId).map { false }
        } else {
            bookmarkTrail(userId, trailId, trailName, trailData).map { true }
        }
    }
    
    /**
     * Load user's bookmarks
     */
    suspend fun loadBookmarks(userId: String): Result<List<Bookmark>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("bookmarks")
                .get()
                .await()
            
            val bookmarks = snapshot.documents.mapNotNull { doc ->
                try {
                    Bookmark(
                        trailId = doc.getString("trailId") ?: return@mapNotNull null,
                        trailName = doc.getString("trailName") ?: "",
                        bookmarkedAt = doc.getLong("bookmarkedAt") ?: 0,
                        trailData = doc.get("trailData") as? Map<String, Any> ?: emptyMap()
                    )
                } catch (e: Exception) {
                    null
                }
            }
            
            // Update local state
            _bookmarkedTrails.value = bookmarks.map { it.trailId }.toSet()
            
            Result.success(bookmarks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Create custom list
     */
    suspend fun createList(
        userId: String,
        listName: String,
        description: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val list = hashMapOf(
                "name" to listName,
                "description" to description,
                "createdAt" to System.currentTimeMillis(),
                "trailCount" to 0
            )
            
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("lists")
                .add(list)
                .await()
            
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add trail to list
     */
    suspend fun addToList(
        userId: String,
        listId: String,
        trailId: String,
        trailName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val item = hashMapOf(
                "trailId" to trailId,
                "trailName" to trailName,
                "addedAt" to System.currentTimeMillis()
            )
            
            firestore.collection("users")
                .document(userId)
                .collection("lists")
                .document(listId)
                .collection("trails")
                .document(trailId)
                .set(item)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Bookmark data class
 */
data class Bookmark(
    val trailId: String,
    val trailName: String,
    val bookmarkedAt: Long,
    val trailData: Map<String, Any>
)

/**
 * Custom list
 */
data class TrailList(
    val id: String,
    val name: String,
    val description: String,
    val trailCount: Int,
    val createdAt: Long
)
