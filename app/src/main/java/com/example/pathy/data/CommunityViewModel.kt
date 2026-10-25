package com.example.pathy.data

import androidx.lifecycle.ViewModel
import com.example.pathy.CommunityEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow


class CommunityViewModel : ViewModel() {

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _deletingPostId = MutableStateFlow<String?>(null)
    private val _deletingEventId = MutableStateFlow<String?>(null)
    private val _events = MutableStateFlow<List<CommunityEvent>>(emptyList())
    private val _combinedItems = MutableStateFlow<List<CommunityItem>>(emptyList())
    val combinedItems: StateFlow<List<CommunityItem>> = _combinedItems


    init {
        listenToPosts()
        listenToEvents()
    }

    fun markPostAsDeleting(postId: String) {
        _deletingPostId.value = postId
    }
    fun markEventAsDeleting(eventId: String) {
        _deletingEventId.value = eventId
    }

    fun listenToPosts() {
        _posts.value = emptyList()
        FirebaseFirestore.getInstance()
            .collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val updatedPosts = snapshot.documents.mapNotNull {
                        val post = it.toObject(Post::class.java)?.copy(postId = it.id)
                        post?.takeIf {
                            it.imageUrls == null || it.imageUrls.all { url -> url.isNotBlank() }
                        }

                    }

                    _posts.value = updatedPosts
                    updateCombinedItems()
                    _isLoading.value = false
                }
            }
    }
    fun listenToEvents() {
        _events.value = emptyList()
        FirebaseFirestore.getInstance()
            .collection("events")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null) {
                    val updatedEvents = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(CommunityEvent::class.java)?.copy(id = doc.id)
                    }

                    _events.value = updatedEvents
                    updateCombinedItems()
                }
            }
    }
    private fun updateCombinedItems() {
        val posts = _posts.value
        val events = _events.value
        val combined = (posts.map { CommunityItem.PostItem(it) } +
                events.map { CommunityItem.EventItem(it) })
            .sortedByDescending {
                when (it) {
                    is CommunityItem.PostItem -> it.post.timestamp
                    is CommunityItem.EventItem -> it.event.createdAt
                }
            }
        _combinedItems.value = combined
    }
}
