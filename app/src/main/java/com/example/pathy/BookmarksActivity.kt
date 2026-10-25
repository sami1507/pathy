package com.example.pathy

import com.example.pathy.i18n.PathyActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.pathy.bookmarks.Bookmark
import com.example.pathy.bookmarks.BookmarkManager
import com.example.pathy.data.TrailData
import com.example.pathy.screens.BookmarksScreen
import com.example.pathy.ui.theme.TEMPATHYTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.launch

/** Key under which the full trail JSON is stored inside a bookmark's trailData map. */
private const val TRAIL_JSON_KEY = "trailJson"

/** Builds the trailData map stored with a bookmark (the whole trail, so it can be reopened later). */
internal fun bookmarkDataFor(trail: TrailData): Map<String, Any> =
    mapOf(TRAIL_JSON_KEY to Gson().toJson(trail))

/** Rebuilds the trail from a bookmark, or null for bookmarks saved without trail data. */
internal fun trailFromBookmark(bookmark: Bookmark): TrailData? {
    val json = bookmark.trailData[TRAIL_JSON_KEY] as? String ?: return null
    return runCatching { Gson().fromJson(json, TrailData::class.java) }.getOrNull()
}

/**
 * 🔖 Lists the signed-in user's saved trails and opens them in [TrailDetailsActivity].
 */
class BookmarksActivity : PathyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TEMPATHYTheme {
                BookmarksRoute(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun BookmarksRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val manager = remember { BookmarkManager() }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }

    LaunchedEffect(uid) {
        if (uid == null) {
            Toast.makeText(context, context.getString(R.string.bookmarks_sign_in_required), Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        manager.loadBookmarks(uid)
            .onSuccess { bookmarks = it }
            .onFailure { e ->
                Toast.makeText(context, context.getString(R.string.bookmarks_load_failed, e.message), Toast.LENGTH_LONG).show()
            }
    }

    BookmarksScreen(
        bookmarks = bookmarks,
        onBookmarkClick = { trailId ->
            val trail = bookmarks.firstOrNull { it.trailId == trailId }?.let(::trailFromBookmark)
            if (trail != null) {
                context.startActivity(TrailDetailsActivity.createIntent(context, trail))
            } else {
                Toast.makeText(context, context.getString(R.string.trail_details_unavailable), Toast.LENGTH_SHORT).show()
            }
        },
        onRemoveBookmark = { trailId ->
            if (uid != null) {
                scope.launch {
                    manager.removeBookmark(uid, trailId)
                        .onSuccess { bookmarks = bookmarks.filterNot { it.trailId == trailId } }
                        .onFailure { e ->
                            Toast.makeText(context, context.getString(R.string.bookmarks_remove_failed, e.message), Toast.LENGTH_SHORT).show()
                        }
                }
            }
        },
        onBack = onBack
    )
}
