package com.example.pathy.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pathy.R
import com.example.pathy.bookmarks.Bookmark
import com.example.pathy.i18n.relativeTimeAgo
import com.example.pathy.ui.design.NatureCard
import com.example.pathy.ui.design.NatureEmptyState
import com.example.pathy.ui.design.NatureLoadingState
import com.example.pathy.ui.design.pathyTopBarColors
import com.example.pathy.ui.design.pathyTopBarSubtitleColor

/**
 * The signed-in user's saved trails. Draws the list, the first load, the empty list (with a way to
 * start saving) and a failed load (with a retry). Removing a bookmark is reported through
 * [onRemoveBookmark]; the route shows the undo message in [snackbarHost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (String) -> Unit = {},
    onRemoveBookmark: (String) -> Unit = {},
    onBack: () -> Unit = {},
    isLoading: Boolean = false,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
    onDiscover: () -> Unit = {},
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.bookmarks_title),
                            fontWeight = FontWeight.Bold
                        )
                        if (bookmarks.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(R.plurals.saved_trails_count, bookmarks.size, bookmarks.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = pathyTopBarSubtitleColor()
                            )
                        }
                    }
                },
                colors = pathyTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            bookmarks.isNotEmpty() -> LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(bookmarks, key = { it.trailId }) { bookmark ->
                    BookmarkCard(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark.trailId) },
                        onRemove = { onRemoveBookmark(bookmark.trailId) }
                    )
                }
            }
            isLoading -> Box(modifier, contentAlignment = Alignment.Center) {
                NatureLoadingState(stringResource(R.string.common_loading))
            }
            loadFailed -> Box(modifier, contentAlignment = Alignment.Center) {
                NatureEmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = stringResource(R.string.load_failed),
                    message = stringResource(R.string.error_network_check),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    isError = true
                )
            }
            else -> Box(modifier, contentAlignment = Alignment.Center) {
                EmptyBookmarksState(onDiscover = onDiscover)
            }
        }
    }
}

@Composable
fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    NatureCard(onClick = onClick, contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Hiking,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.trailName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.bookmark_saved_ago, relativeTimeAgo(bookmark.bookmarkedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // A filled bookmark: pressing it takes the trail out of the list.
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = stringResource(R.string.bookmarks_remove),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyBookmarksState(onDiscover: () -> Unit = {}) {
    NatureEmptyState(
        icon = Icons.Filled.BookmarkBorder,
        title = stringResource(R.string.bookmarks_empty_title),
        message = stringResource(R.string.bookmarks_empty_body),
        actionLabel = stringResource(R.string.home_discover),
        onAction = onDiscover
    )
}
