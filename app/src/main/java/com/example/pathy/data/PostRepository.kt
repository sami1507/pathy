package com.example.pathy.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.util.UUID

fun uploadPostToFirestore(
    post: Post,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val itemRef = firestore.collection("posts").document(post.postId)

    itemRef.set(post)
        .addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onFailure(it) }
}

fun deletePostWithImages(postId: String, imageUrls: List<String>) {
    val db = Firebase.firestore
    val storage = Firebase.storage

    for (url in imageUrls) {
        if (url.isNotBlank()) {
            val imageRef = storage.getReferenceFromUrl(url)
            imageRef.delete()
        }
    }
    val commentsRef = db.collection("posts").document(postId).collection("comments")
    commentsRef.get()
        .addOnSuccessListener { snapshot ->
            val batch = db.batch()

            for (doc in snapshot.documents) {
                batch.delete(doc.reference)
            }

            batch.commit()
                .addOnSuccessListener {
                    db.collection("posts").document(postId)
                        .delete()
                }
        }
}
fun toggleLike(
    collection: String,
    itemId: String,
    userId: String
) {
    val db = FirebaseFirestore.getInstance()
    val itemRef = db.collection(collection).document(itemId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(itemRef)

        val currentLikes = (snapshot.getLong("likesCount") ?: 0).coerceAtLeast(0)
        val likedBy = snapshot.get("likedBy") as? Map<String, Boolean> ?: emptyMap()
        val newLikedBy = likedBy.toMutableMap()

        val hasLiked = likedBy.containsKey(userId)
        val newLikes = when {
            hasLiked -> (currentLikes - 1).coerceAtLeast(0).also { newLikedBy.remove(userId) }
            else -> currentLikes + 1.also { newLikedBy[userId] = true }
        }

        transaction.update(itemRef, mapOf(
            "likedBy" to newLikedBy,
            "likesCount" to newLikes
        ))
    }
    .addOnSuccessListener {}
    .addOnFailureListener {}
}




fun submitComment(
    collection: String,
    itemId: String,
    text: String,
    onSuccess: () -> Unit = {},
    onError: (Exception) -> Unit = {}
) {
    val user = FirebaseAuth.getInstance().currentUser ?: return
    val db = FirebaseFirestore.getInstance()

    val comment = Comment(
        commentId = UUID.randomUUID().toString(),
        userId = user.uid,
        text = text
    )

    val itemRef = db.collection(collection).document(itemId)
    val commentRef = itemRef.collection("comments").document(comment.commentId)

    db.runTransaction { transaction ->
        val snapshot = transaction.get(itemRef)
        val currentCount = (snapshot.getLong("commentsCount") ?: 0).coerceAtLeast(0)
        val newCount = currentCount + 1

        transaction.set(commentRef, comment)
        transaction.update(itemRef, "commentsCount", newCount)
    }.addOnSuccessListener { onSuccess() }
        .addOnFailureListener { onError(it) }
}

fun listenToCommentsWithProfiles(
    collection: String,
    itemId: String,
    onResult: (List<DisplayComment>) -> Unit,
    onError: (Exception) -> Unit = {}
): ListenerRegistration {
    val firestore = FirebaseFirestore.getInstance()
    return firestore.collection(collection)
        .document(itemId)
        .collection("comments")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                onError(error ?: Exception("No snapshot"))
                return@addSnapshotListener
            }

            val comments = snapshot.documents.mapNotNull { it.toObject(Comment::class.java) }
            val userIds = comments.map { it.userId }.toSet()

            if (userIds.isEmpty()) {
                onResult(emptyList())
                return@addSnapshotListener
            }

            firestore.collection("users")
                .whereIn(FieldPath.documentId(), userIds.toList())
                .get()
                .addOnSuccessListener { userSnapshot ->
                    val userMap = userSnapshot.documents.associateBy(
                        { it.id },
                        {
                            val name = it.getString("name") ?: "משתמש"
                            val imageUrl = it.getString("profileImageUrl") ?: ""
                            name to imageUrl
                        }
                    )
                    val displayComments = comments.map {
                        val (name, image) = userMap[it.userId] ?: ("משתמש" to "")
                        DisplayComment(it, name, image)
                    }
                    onResult(displayComments)
                }
                .addOnFailureListener { onError(it) }
        }
}